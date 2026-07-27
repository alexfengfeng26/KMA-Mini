package com.kma.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.dto.ModelProfileRequest;
import com.kma.knowledge.entity.ModelProfile;
import com.kma.knowledge.mapper.KnowledgeDatasetMapper;
import com.kma.knowledge.mapper.ModelProfileMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelProfileServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void preventsChangingVectorIdentityWhenProfileIsBound() {
        ModelProfileMapper profileMapper = mock(ModelProfileMapper.class);
        KnowledgeDatasetMapper datasetMapper = mock(KnowledgeDatasetMapper.class);
        ModelProfile existing = profile("bge-v1", "bge-m3", 1024);
        existing.setProfileId(3L);
        when(profileMapper.selectById(3L)).thenReturn(existing);
        when(datasetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        ModelProfileServiceImpl service = new ModelProfileServiceImpl(profileMapper, datasetMapper);
        ModelProfileRequest request = request("bge-v1", "bge-m3-new", 1024);
        request.setProfileId(3L);

        assertThatThrownBy(() -> service.update(request))
            .isInstanceOfSatisfying(KmaException.class, ex -> assertThat(ex.getCode()).isEqualTo(409));
    }

    @Test
    void requiresSupportedDimensionForEmbeddingProfile() {
        ModelProfileServiceImpl service = new ModelProfileServiceImpl(
            mock(ModelProfileMapper.class), mock(KnowledgeDatasetMapper.class));
        ModelProfileRequest missing = request("bge-v1", "bge-m3", 1024);
        missing.setDimension(null);
        ModelProfileRequest unsupported = request("bge-v2", "bge-m3", 1024);
        unsupported.setDimension(384);

        assertThatThrownBy(() -> service.create(missing)).isInstanceOf(KmaException.class)
            .hasMessageContaining("必须配置");
        assertThatThrownBy(() -> service.create(unsupported)).isInstanceOf(KmaException.class)
            .hasMessageContaining("仅支持");
    }

    @Test
    void createsValidatedProfileThroughMapper() {
        ModelProfileMapper mapper = mock(ModelProfileMapper.class);
        ModelProfileServiceImpl service = new ModelProfileServiceImpl(mapper, mock(KnowledgeDatasetMapper.class));

        ModelProfile created = service.create(request("bge-v1", "bge-m3", 1024));

        verify(mapper).insert(created);
        assertThat(created.getProfileId()).isNull();
        assertThat(created.getCreateTime()).isNotNull();
        assertThat(created.getUpdateTime()).isEqualTo(created.getCreateTime());
    }

    private ModelProfile profile(String code, String model, int dimension) {
        ModelProfile profile = new ModelProfile();
        profile.setProfileCode(code);
        profile.setName(code);
        profile.setCapability("embedding");
        profile.setProvider("local-bge-m3");
        profile.setModelName(model);
        profile.setDimension(dimension);
        profile.setTimeoutSeconds(60);
        profile.setEnabled(true);
        return profile;
    }

    private ModelProfileRequest request(String code, String model, int dimension) {
        ModelProfileRequest request = new ModelProfileRequest();
        request.setProfileCode(code);
        request.setName(code);
        request.setCapability("embedding");
        request.setProvider("local-bge-m3");
        request.setModelName(model);
        request.setDimension(dimension);
        return request;
    }
}
