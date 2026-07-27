package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.knowledge.entity.ModelProfile;
import com.kma.knowledge.mapper.ModelProfileMapper;
import com.kma.knowledge.model.ResolvedModelProfile;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelProfileResolverTest {
    @Test
    void everyResolveReadsLatestProfileAndNeverSerializesSecret() throws Exception {
        ModelProfileMapper mapper = mock(ModelProfileMapper.class);
        AtomicReference<String> model = new AtomicReference<>("bge-m3-v1");
        when(mapper.selectOne(any())).thenAnswer(invocation -> profile(model.get()));
        ModelProfileResolver resolver = new ModelProfileResolver(mapper, new ObjectMapper(), alias -> "top-secret");

        ResolvedModelProfile first = resolver.resolve("embed-main", "embedding");
        model.set("bge-m3-v2");
        ResolvedModelProfile second = resolver.resolve("embed-main", "embedding");

        assertThat(first.getModelName()).isEqualTo("bge-m3-v1");
        assertThat(second.getModelName()).isEqualTo("bge-m3-v2");
        assertThat(new ObjectMapper().writeValueAsString(second)).doesNotContain("top-secret");
        assertThat(second.toString()).doesNotContain("top-secret");
    }

    private ModelProfile profile(String model) {
        ModelProfile profile = new ModelProfile();
        profile.setProfileCode("embed-main");
        profile.setCapability("embedding");
        profile.setProvider("openai-compatible");
        profile.setModelName(model);
        profile.setBaseUrl("http://localhost:9997/v1/");
        profile.setDimension(1024);
        profile.setTimeoutSeconds(30);
        profile.setSecretAlias("MODEL_KEY");
        profile.setFallbackProfileCodes("[]");
        profile.setEnabled(true);
        return profile;
    }
}
