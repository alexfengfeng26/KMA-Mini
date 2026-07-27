package com.kma.knowledge.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/** Runtime-only model configuration. Secret values must never be serialized or persisted. */
public final class ResolvedModelProfile {
    private final String profileCode;
    private final String capability;
    private final String provider;
    private final String modelName;
    private final String baseUrl;
    private final Integer dimension;
    private final int timeoutSeconds;
    private final String secretAlias;
    private final List<String> fallbackProfileCodes;
    private final String secret;

    public ResolvedModelProfile(String profileCode, String capability, String provider, String modelName,
                                String baseUrl, Integer dimension, int timeoutSeconds, String secretAlias,
                                List<String> fallbackProfileCodes, String secret) {
        this.profileCode = profileCode;
        this.capability = capability;
        this.provider = provider;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.dimension = dimension;
        this.timeoutSeconds = timeoutSeconds;
        this.secretAlias = secretAlias;
        this.fallbackProfileCodes = List.copyOf(fallbackProfileCodes);
        this.secret = secret;
    }

    public String getProfileCode() { return profileCode; }
    public String getCapability() { return capability; }
    public String getProvider() { return provider; }
    public String getModelName() { return modelName; }
    public String getBaseUrl() { return baseUrl; }
    public Integer getDimension() { return dimension; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public String getSecretAlias() { return secretAlias; }
    public List<String> getFallbackProfileCodes() { return fallbackProfileCodes; }
    @JsonIgnore public String getSecret() { return secret; }

    @Override
    public String toString() {
        return "ResolvedModelProfile{profileCode='" + profileCode + "', capability='" + capability
            + "', provider='" + provider + "', modelName='" + modelName + "', secretAlias='"
            + secretAlias + "', secret='***'}";
    }
}
