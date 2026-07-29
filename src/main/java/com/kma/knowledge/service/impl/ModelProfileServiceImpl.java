package com.kma.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kma.common.exception.KmaException;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.client.llm.LlmChatRequest;
import com.kma.knowledge.client.llm.LlmClient;
import com.kma.knowledge.client.llm.LlmClientFactory;
import com.kma.knowledge.dto.ModelProfileProbeResult;
import com.kma.knowledge.dto.ModelProfileRequest;
import com.kma.knowledge.entity.KnowledgeDataset;
import com.kma.knowledge.entity.ModelProfile;
import com.kma.knowledge.mapper.KnowledgeDatasetMapper;
import com.kma.knowledge.mapper.ModelProfileMapper;
import com.kma.knowledge.service.ModelProfileService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class ModelProfileServiceImpl implements ModelProfileService {
    private final ModelProfileMapper mapper;
    private final KnowledgeDatasetMapper datasetMapper;
    private final LlmClientFactory llmClientFactory;
    private final SecurityAuditService audit;
    private final Map<String, Instant> successfulLlmProbes = new ConcurrentHashMap<>();

    public ModelProfileServiceImpl(ModelProfileMapper mapper, KnowledgeDatasetMapper datasetMapper) {
        this(mapper, datasetMapper, null, null);
    }

    @Autowired
    public ModelProfileServiceImpl(ModelProfileMapper mapper, KnowledgeDatasetMapper datasetMapper,
                                   LlmClientFactory llmClientFactory, SecurityAuditService audit) {
        this.mapper = mapper;
        this.datasetMapper = datasetMapper;
        this.llmClientFactory = llmClientFactory;
        this.audit = audit;
    }

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

    @Override
    public ModelProfileProbeResult probe(String profileCode) {
        ModelProfile profile = find(profileCode);
        if (!"llm".equals(profile.getCapability())) {
            throw new KmaException(400, "当前仅支持探测 LLM Profile");
        }
        if (llmClientFactory == null) {
            throw new KmaException(503, "模型探测服务不可用");
        }
        long started = System.currentTimeMillis();
        ModelProfileProbeResult result = new ModelProfileProbeResult();
        result.setProfileCode(profile.getProfileCode());
        result.setCapability(profile.getCapability());
        result.setModelName(profile.getModelName());
        try {
            LlmClient client = llmClientFactory.getSingleProfile(profileCode);
            LlmChatRequest request = probeRequest();
            client.chat(request);
            result.setNonStreamingSupported(true);
            request.setStream(true);
            client.streamChat(request, ignored -> { });
            result.setStreamingSupported(true);
            result.setSuccess(true);
            result.setMessage("非流式与流式连接正常");
            successfulLlmProbes.put(profileCode, Instant.now());
        } catch (Exception ex) {
            successfulLlmProbes.remove(profileCode);
            result.setSuccess(false);
            result.setMessage(safeProbeMessage(ex));
        }
        result.setDurationMillis(System.currentTimeMillis() - started);
        return result;
    }

    @Override
    @Transactional(transactionManager = "knowledgeTransactionManager")
    public ModelProfile activateDefault(String profileCode) {
        ModelProfile target = find(profileCode);
        if (!"llm".equals(target.getCapability()) || !Boolean.TRUE.equals(target.getEnabled())) {
            throw new KmaException(400, "只能激活启用状态的 LLM Profile");
        }
        Instant successAt = successfulLlmProbes.get(profileCode);
        if (successAt == null || successAt.isBefore(Instant.now().minusSeconds(600))) {
            throw new KmaException(409, "请先在十分钟内测试连接成功，再切换默认模型");
        }
        ModelProfile before = mapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getCapability, "llm").eq(ModelProfile::getDefaultProfile, true));
        mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ModelProfile>()
            .eq(ModelProfile::getCapability, "llm")
            .set(ModelProfile::getDefaultProfile, false)
            .set(ModelProfile::getUpdateTime, LocalDateTime.now()));
        mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ModelProfile>()
            .eq(ModelProfile::getProfileId, target.getProfileId())
            .set(ModelProfile::getDefaultProfile, true)
            .set(ModelProfile::getUpdateTime, LocalDateTime.now()));
        target.setDefaultProfile(true);
        if (audit != null) {
            audit.recordRequired("model_profile", "warning", "model-profile.activate-default",
                "model-profile:" + profileCode,
                before == null ? Map.of() : Map.of("profileCode", before.getProfileCode(), "modelName", before.getModelName()),
                Map.of("profileCode", target.getProfileCode(), "modelName", target.getModelName()),
                Map.of("probeVerified", true));
        }
        return target;
    }

    private void validate(ModelProfileRequest request) {
        if ("llm".equals(request.getCapability()) && Boolean.TRUE.equals(request.getDefaultProfile())) {
            throw new KmaException(400, "LLM 默认模型必须先测试连接，再通过激活操作切换");
        }
        if ("embedding".equals(request.getCapability()) && request.getDimension() == null) {
            throw new KmaException(400, "Embedding Profile 必须配置向量维度");
        }
        Integer dimension = request.getDimension();
        if (dimension != null && dimension != 768 && dimension != 1024 && dimension != 1536) {
            throw new KmaException(400, "向量维度仅支持 768、1024 或 1536");
        }
    }

    private ModelProfile find(String profileCode) {
        ModelProfile profile = mapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getProfileCode, profileCode));
        if (profile == null) throw new KmaException(404, "模型 Profile 不存在");
        return profile;
    }

    private LlmChatRequest probeRequest() {
        LlmChatRequest request = new LlmChatRequest();
        request.setTemperature(0d);
        request.setMessages(List.of(Map.of("role", "user", "content", "Reply with OK.")));
        return request;
    }

    private String safeProbeMessage(Exception exception) {
        String message = exception.getMessage() == null ? "模型服务不可用" : exception.getMessage();
        if (message.contains("密钥")) return "环境变量密钥未配置";
        if (message.contains("401") || message.contains("403")) return "认证失败，请检查环境变量密钥";
        if (message.contains("429")) return "请求受限，请稍后重试或检查额度";
        if (message.toLowerCase(java.util.Locale.ROOT).contains("timeout")) return "连接超时，请检查服务地址和网络";
        return "模型或流式接口不兼容，请检查服务地址、模型名和网络";
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
