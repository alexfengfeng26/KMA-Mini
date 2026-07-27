package com.kma.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.dto.DatasetCreateRequest;
import com.kma.knowledge.dto.DatasetQueryRequest;
import com.kma.knowledge.dto.DatasetUpdateRequest;
import com.kma.knowledge.dto.DatasetVO;
import com.kma.knowledge.entity.KnowledgeDataset;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.entity.ModelProfile;
import com.kma.knowledge.enums.DatasetStatus;
import com.kma.knowledge.mapper.KnowledgeDatasetMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.mapper.ModelProfileMapper;
import com.kma.knowledge.service.KnowledgeDatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 数据集服务实现
 *
 * @author party
 * @date 2026/06/30
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeDatasetServiceImpl implements KnowledgeDatasetService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final KnowledgeDatasetMapper datasetMapper;
    private final KnowledgeSpaceMapper spaceMapper;
    private final ModelProfileMapper modelProfileMapper;

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public Long create(DatasetCreateRequest request) {
        long count = datasetMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeDataset>().eq(KnowledgeDataset::getName, request.getName())
        );
        if (count > 0) {
            throw new KmaException("数据集名称已存在");
        }

        KnowledgeDataset dataset = new KnowledgeDataset();
        dataset.setName(request.getName());
        dataset.setDescription(request.getDescription());
        dataset.setChunkStrategy(normalizeJson(request.getChunkStrategy(), "{\"type\":\"recursive\"}", "分块策略"));
        dataset.setParseConfig(normalizeJson(request.getParseConfig(), "{}", "解析配置"));
        if (StringUtils.isNotBlank(request.getEmbeddingProfileCode())) {
            requireEmbeddingProfile(request.getEmbeddingProfileCode());
            dataset.setEmbeddingProfileCode(request.getEmbeddingProfileCode());
        }
        dataset.setRerankEnabled(request.getRerankEnabled() != null ? request.getRerankEnabled() : true);
        dataset.setRerankModel(request.getRerankModel());
        dataset.setPresetQuestions(normalizeJson(request.getPresetQuestions(), null, "预设问题"));
        dataset.setStatus(DatasetStatus.ACTIVE.getCode());
        dataset.setCreateTime(LocalDateTime.now());
        dataset.setUpdateTime(LocalDateTime.now());
        datasetMapper.insert(dataset);
        return dataset.getDatasetId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void update(DatasetUpdateRequest request) {
        KnowledgeDataset existing = datasetMapper.selectById(request.getDatasetId());
        if (existing == null) {
            throw new KmaException("数据集不存在");
        }
        long count = datasetMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeDataset>()
                .eq(KnowledgeDataset::getName, request.getName())
                .ne(KnowledgeDataset::getDatasetId, request.getDatasetId())
        );
        if (count > 0) {
            throw new KmaException("数据集名称已存在");
        }

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setChunkStrategy(normalizeJson(request.getChunkStrategy(), "{\"type\":\"recursive\"}", "分块策略"));
        existing.setParseConfig(normalizeJson(request.getParseConfig(), "{}", "解析配置"));
        bindEmbeddingProfile(existing, request.getEmbeddingProfileCode());
        existing.setRerankEnabled(request.getRerankEnabled() != null ? request.getRerankEnabled() : existing.getRerankEnabled());
        existing.setRerankModel(request.getRerankModel());
        existing.setPresetQuestions(normalizeJson(request.getPresetQuestions(), null, "预设问题"));
        existing.setUpdateTime(LocalDateTime.now());
        datasetMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void delete(Long datasetId) {
        long boundCount = spaceMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeSpace>().eq(KnowledgeSpace::getDatasetId, datasetId)
        );
        if (boundCount > 0) {
            throw new KmaException("数据集已绑定空间，无法删除");
        }
        datasetMapper.deleteById(datasetId);
    }

    @Override
    public Page<DatasetVO> page(DatasetQueryRequest request) {
        Page<KnowledgeDataset> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<KnowledgeDataset> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(request.getName())) {
            wrapper.like(KnowledgeDataset::getName, request.getName());
        }
        if (StringUtils.isNotBlank(request.getStatus())) {
            wrapper.eq(KnowledgeDataset::getStatus, request.getStatus());
        }
        wrapper.orderByDesc(KnowledgeDataset::getCreateTime);
        Page<KnowledgeDataset> ApiResult = datasetMapper.selectPage(page, wrapper);

        List<DatasetVO> records = ApiResult.getRecords().stream()
            .map(this::toVo)
            .collect(Collectors.toList());
        Page<DatasetVO> voPage = new Page<>(ApiResult.getCurrent(), ApiResult.getSize(), ApiResult.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public DatasetVO getById(Long datasetId) {
        KnowledgeDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new KmaException("数据集不存在");
        }
        return toVo(dataset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void changeStatus(Long datasetId, String status) {
        KnowledgeDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new KmaException("数据集不存在");
        }
        dataset.setStatus(status);
        dataset.setUpdateTime(LocalDateTime.now());
        datasetMapper.updateById(dataset);
    }

    @Override
    public List<DatasetVO> listActive() {
        LambdaQueryWrapper<KnowledgeDataset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDataset::getStatus, DatasetStatus.ACTIVE.getCode());
        wrapper.orderByDesc(KnowledgeDataset::getCreateTime);
        return datasetMapper.selectList(wrapper).stream()
            .map(this::toVo)
            .collect(Collectors.toList());
    }

    private DatasetVO toVo(KnowledgeDataset dataset) {
        DatasetVO vo = new DatasetVO();
        vo.setDatasetId(dataset.getDatasetId());
        vo.setName(dataset.getName());
        vo.setDescription(dataset.getDescription());
        vo.setChunkStrategy(dataset.getChunkStrategy());
        vo.setParseConfig(dataset.getParseConfig());
        vo.setEmbeddingProfileCode(dataset.getEmbeddingProfileCode());
        vo.setRerankEnabled(dataset.getRerankEnabled());
        vo.setRerankModel(dataset.getRerankModel());
        vo.setPresetQuestions(dataset.getPresetQuestions());
        vo.setStatus(dataset.getStatus());
        vo.setCreateTime(dataset.getCreateTime());
        vo.setUpdateTime(dataset.getUpdateTime());
        return vo;
    }

    private void bindEmbeddingProfile(KnowledgeDataset dataset, String requestedCode) {
        if (StringUtils.isBlank(requestedCode)) {
            return;
        }
        if (StringUtils.isNotBlank(dataset.getEmbeddingProfileCode())
            && !Objects.equals(dataset.getEmbeddingProfileCode(), requestedCode)) {
            throw new KmaException("数据集的 Embedding Profile 已绑定，不允许修改");
        }
        requireEmbeddingProfile(requestedCode);
        dataset.setEmbeddingProfileCode(requestedCode);
    }

    private ModelProfile requireEmbeddingProfile(String profileCode) {
        ModelProfile profile = modelProfileMapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getProfileCode, profileCode)
            .eq(ModelProfile::getCapability, "embedding")
            .eq(ModelProfile::getEnabled, true));
        if (profile == null) {
            throw new KmaException("可用的 Embedding Profile 不存在: " + profileCode);
        }
        if (profile.getDimension() == null) {
            throw new KmaException("Embedding Profile 未配置向量维度: " + profileCode);
        }
        return profile;
    }

    private String normalizeJson(String value, String fallback, String fieldName) {
        String candidate = StringUtils.isBlank(value) ? fallback : value;
        if (candidate == null) {
            return null;
        }
        try {
            return JSON.readTree(candidate).toString();
        } catch (JsonProcessingException exception) {
            throw new KmaException(fieldName + "必须是合法 JSON");
        }
    }
}



