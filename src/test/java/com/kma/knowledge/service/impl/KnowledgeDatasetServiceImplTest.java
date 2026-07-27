package com.kma.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.dto.DatasetCreateRequest;
import com.kma.knowledge.dto.DatasetUpdateRequest;
import com.kma.knowledge.entity.KnowledgeDataset;
import com.kma.knowledge.entity.ModelProfile;
import com.kma.knowledge.mapper.KnowledgeDatasetMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.mapper.ModelProfileMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDatasetServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void bindsAnEnabledEmbeddingProfileWhenCreatingDataset() {
        KnowledgeDatasetMapper datasetMapper = mock(KnowledgeDatasetMapper.class);
        ModelProfileMapper profileMapper = mock(ModelProfileMapper.class);
        when(datasetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(embeddingProfile("bge-m3"));
        KnowledgeDatasetServiceImpl service = new KnowledgeDatasetServiceImpl(datasetMapper,
            mock(KnowledgeSpaceMapper.class), profileMapper);
        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("党建政策");
        request.setEmbeddingProfileCode("bge-m3");

        service.create(request);

        ArgumentCaptor<KnowledgeDataset> captor = ArgumentCaptor.forClass(KnowledgeDataset.class);
        verify(datasetMapper).insert(captor.capture());
        assertThat(captor.getValue().getEmbeddingProfileCode()).isEqualTo("bge-m3");
    }

    @Test
    @SuppressWarnings("unchecked")
    void refusesToReplaceAnExistingEmbeddingProfileBinding() {
        KnowledgeDatasetMapper datasetMapper = mock(KnowledgeDatasetMapper.class);
        KnowledgeDataset existing = new KnowledgeDataset();
        existing.setDatasetId(7L);
        existing.setName("old");
        existing.setEmbeddingProfileCode("profile-v1");
        when(datasetMapper.selectById(7L)).thenReturn(existing);
        when(datasetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        KnowledgeDatasetServiceImpl service = new KnowledgeDatasetServiceImpl(datasetMapper,
            mock(KnowledgeSpaceMapper.class), mock(ModelProfileMapper.class));
        DatasetUpdateRequest request = new DatasetUpdateRequest();
        request.setDatasetId(7L);
        request.setName("new");
        request.setEmbeddingProfileCode("profile-v2");

        assertThatThrownBy(() -> service.update(request))
            .isInstanceOf(KmaException.class).hasMessageContaining("不允许修改");
    }

    @Test
    @SuppressWarnings("unchecked")
    void normalizesJsonDefaultsAndRejectsInvalidConfiguration() {
        KnowledgeDatasetMapper datasetMapper = mock(KnowledgeDatasetMapper.class);
        when(datasetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        KnowledgeDatasetServiceImpl service = new KnowledgeDatasetServiceImpl(datasetMapper,
            mock(KnowledgeSpaceMapper.class), mock(ModelProfileMapper.class));
        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("默认策略");

        service.create(request);

        ArgumentCaptor<KnowledgeDataset> captor = ArgumentCaptor.forClass(KnowledgeDataset.class);
        verify(datasetMapper).insert(captor.capture());
        assertThat(captor.getValue().getChunkStrategy()).isEqualTo("{\"type\":\"recursive\"}");
        assertThat(captor.getValue().getParseConfig()).isEqualTo("{}");
        assertThat(captor.getValue().getPresetQuestions()).isNull();

        request.setName("非法策略");
        request.setChunkStrategy("recursive");
        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(KmaException.class).hasMessageContaining("合法 JSON");
    }

    private ModelProfile embeddingProfile(String code) {
        ModelProfile profile = new ModelProfile();
        profile.setProfileCode(code);
        profile.setCapability("embedding");
        profile.setDimension(1024);
        profile.setEnabled(true);
        return profile;
    }
}
