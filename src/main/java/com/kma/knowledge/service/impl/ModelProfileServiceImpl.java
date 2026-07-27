package com.kma.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.dto.ModelProfileRequest;
import com.kma.knowledge.entity.KnowledgeDataset;
import com.kma.knowledge.entity.ModelProfile;
import com.kma.knowledge.mapper.KnowledgeDatasetMapper;
import com.kma.knowledge.mapper.ModelProfileMapper;
import com.kma.knowledge.service.ModelProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class ModelProfileServiceImpl implements ModelProfileService {
    private final ModelProfileMapper mapper;
    private final KnowledgeDatasetMapper datasetMapper;

    @Override
    public List<ModelProfile> list(String capability) {
        return mapper.selectList(new LambdaQueryWrapper<ModelProfile>()
            .eq(capability != null && !capability.isBlank(), ModelProfile::getCapability, capability)
            .orderByAsc(ModelProfile::getCapability, ModelProfile::getProfileCode));
    }

    @Override
    @Transactional(transactionManager = "knowledgeTransactionManager")
    public ModelProfile create(ModelProfileRequest request) {
        validate(request);
        ModelProfile profile = from(request);
        profile.setProfileId(null);
        profile.setCreateTime(LocalDateTime.now());
        profile.setUpdateTime(profile.getCreateTime());
        try {
            mapper.insert(profile);
        } catch (DataIntegrityViolationException ex) {
            throw new KmaException(409, "模型 Profile 编码已存在或配置无效");
        }
        return profile;
    }

    @Override
    @Transactional(transactionManager = "knowledgeTransactionManager")
    public ModelProfile update(ModelProfileRequest request) {
        validate(request);
        ModelProfile existing = request.getProfileId() == null ? null : mapper.selectById(request.getProfileId());
        if (existing == null) {
            throw new KmaException(404, "模型 Profile 不存在");
        }
        preventMutationWhenBound(existing, request);
        ModelProfile profile = from(request);
        profile.setCreateTime(existing.getCreateTime());
        profile.setUpdateTime(LocalDateTime.now());
        try {
            mapper.updateById(profile);
        } catch (DataIntegrityViolationException ex) {
            throw new KmaException(409, "模型 Profile 编码已存在或配置无效");
        }
        return profile;
    }

    private void validate(ModelProfileRequest request) {
        if ("embedding".equals(request.getCapability()) && request.getDimension() == null) {
            throw new KmaException(400, "Embedding Profile 必须配置向量维度");
        }
        Integer dimension = request.getDimension();
        if (dimension != null && dimension != 768 && dimension != 1024 && dimension != 1536) {
            throw new KmaException(400, "向量维度仅支持 768、1024 或 1536");
        }
    }

    private ModelProfile from(ModelProfileRequest request) {
        ModelProfile profile = new ModelProfile();
        profile.setProfileId(request.getProfileId());
        profile.setProfileCode(request.getProfileCode());
        profile.setName(request.getName());
        profile.setCapability(request.getCapability());
        profile.setProvider(request.getProvider());
        profile.setModelName(request.getModelName());
        profile.setBaseUrl(request.getBaseUrl());
        profile.setDimension(request.getDimension());
        profile.setTimeoutSeconds(request.getTimeoutSeconds());
        profile.setSecretAlias(request.getSecretAlias());
        profile.setFallbackProfileCodes(request.getFallbackProfileCodes());
        profile.setEnabled(request.getEnabled());
        profile.setDefaultProfile(Boolean.TRUE.equals(request.getDefaultProfile()));
        return profile;
    }

    private void preventMutationWhenBound(ModelProfile existing, ModelProfileRequest request) {
        long references = datasetMapper.selectCount(new LambdaQueryWrapper<KnowledgeDataset>()
            .eq(KnowledgeDataset::getEmbeddingProfileCode, existing.getProfileCode()));
        if (references == 0) {
            return;
        }
        boolean immutableFieldsChanged = !Objects.equals(existing.getProfileCode(), request.getProfileCode())
            || !Objects.equals(existing.getCapability(), request.getCapability())
            || !Objects.equals(existing.getProvider(), request.getProvider())
            || !Objects.equals(existing.getModelName(), request.getModelName())
            || !Objects.equals(existing.getDimension(), request.getDimension());
        if (immutableFieldsChanged) {
            throw new KmaException(409, "Profile 已绑定数据集；请创建新 Profile 并重建向量版本");
        }
    }
}
