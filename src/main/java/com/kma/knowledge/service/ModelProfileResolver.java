package com.kma.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.entity.ModelProfile;
import com.kma.knowledge.mapper.ModelProfileMapper;
import com.kma.knowledge.model.ResolvedModelProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Model resolver. It deliberately reads the database for every new task. */
@Service
@RequiredArgsConstructor
public class ModelProfileResolver {
    private static final int MAX_CHAIN_LENGTH = 8;
    private final ModelProfileMapper mapper;
    private final ObjectMapper objectMapper;
    private final SecretProvider secretProvider;

    public ResolvedModelProfile resolve(String profileCode, String capability) {
        ModelProfile profile = mapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getProfileCode, profileCode)
            .eq(ModelProfile::getCapability, capability)
            .eq(ModelProfile::getEnabled, true));
        if (profile == null) {
            throw new KmaException(404, "可用的模型 Profile 不存在: " + profileCode);
        }
        return toResolved(profile);
    }

    public ResolvedModelProfile resolveDefault(String capability) {
        ModelProfile profile = mapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getCapability, capability)
            .eq(ModelProfile::getDefaultProfile, true)
            .eq(ModelProfile::getEnabled, true));
        return profile == null ? null : toResolved(profile);
    }

    public List<ResolvedModelProfile> resolveChain(String profileCode, String capability) {
        List<ResolvedModelProfile> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        append(profileCode, capability, result, seen);
        if ("embedding".equals(capability) && !result.isEmpty()) {
            Integer dimension = result.get(0).getDimension();
            boolean mismatch = result.stream().anyMatch(profile -> !java.util.Objects.equals(dimension, profile.getDimension()));
            if (mismatch) throw new KmaException(400, "Embedding Profile 降级链的向量维度必须一致");
        }
        return List.copyOf(result);
    }

    private void append(String code, String capability, List<ResolvedModelProfile> result, Set<String> seen) {
        if (!seen.add(code)) {
            throw new KmaException(400, "模型 Profile 降级链存在循环: " + code);
        }
        if (seen.size() > MAX_CHAIN_LENGTH) {
            throw new KmaException(400, "模型 Profile 降级链最多支持 " + MAX_CHAIN_LENGTH + " 个节点");
        }
        ResolvedModelProfile resolved = resolve(code, capability);
        result.add(resolved);
        for (String fallback : resolved.getFallbackProfileCodes()) {
            append(fallback, capability, result, seen);
        }
    }

    private ResolvedModelProfile toResolved(ModelProfile profile) {
        List<String> fallbacks = parseFallbacks(profile.getFallbackProfileCodes());
        String secret = secretProvider.resolve(profile.getSecretAlias());
        return new ResolvedModelProfile(profile.getProfileCode(), profile.getCapability(), profile.getProvider(),
            profile.getModelName(), normalizeBaseUrl(profile.getBaseUrl()), profile.getDimension(),
            profile.getTimeoutSeconds() == null ? 60 : profile.getTimeoutSeconds(), profile.getSecretAlias(),
            fallbacks, secret);
    }

    private List<String> parseFallbacks(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return values == null ? List.of() : values.stream().filter(StringUtils::hasText).distinct().toList();
        } catch (Exception ex) {
            throw new KmaException(400, "模型 Profile 降级链 JSON 无效");
        }
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) return value;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
