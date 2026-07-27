package com.kma.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.dto.SpaceAclRequest;
import com.kma.knowledge.dto.SpaceCreateRequest;
import com.kma.knowledge.dto.SpaceQueryRequest;
import com.kma.knowledge.dto.SpaceUpdateRequest;
import com.kma.knowledge.entity.KnowledgeDataset;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.entity.KnowledgeSpaceAcl;
import com.kma.knowledge.entity.ModelProfile;
import com.kma.knowledge.mapper.KnowledgeDatasetMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceAclMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.mapper.ModelProfileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeSpaceServiceImplTest {

    @BeforeAll
    static void initializeMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.kma.knowledge.mapper.KnowledgeSpaceMapper");
        TableInfoHelper.initTableInfo(assistant, KnowledgeSpace.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeSpaceAcl.class);
        TableInfoHelper.initTableInfo(assistant, ModelProfile.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsSpaceEmbeddingConfigurationThatDiffersFromDatasetProfile() {
        KnowledgeSpaceMapper spaceMapper = mock(KnowledgeSpaceMapper.class);
        KnowledgeDatasetMapper datasetMapper = mock(KnowledgeDatasetMapper.class);
        ModelProfileMapper profileMapper = mock(ModelProfileMapper.class);
        KnowledgeDataset dataset = new KnowledgeDataset();
        dataset.setDatasetId(10L);
        dataset.setEmbeddingProfileCode("bge-m3-v1");
        ModelProfile profile = new ModelProfile();
        profile.setProfileCode("bge-m3-v1");
        profile.setCapability("embedding");
        profile.setProvider("local-bge-m3");
        profile.setModelName("bge-m3");
        profile.setDimension(1024);
        profile.setEnabled(true);
        when(spaceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
        KnowledgeSpaceServiceImpl service = new KnowledgeSpaceServiceImpl(spaceMapper,
            mock(KnowledgeSpaceAclMapper.class), datasetMapper, profileMapper);
        SpaceCreateRequest request = request();
        request.setEmbeddingDim(1536);

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(KmaException.class).hasMessageContaining("必须与数据集绑定");
    }

    @Test
    void createsSpaceWithDefaultsAndReturnsGeneratedId() {
        Fixtures f = fixtures();
        when(f.spaceMapper.selectCount(any())).thenReturn(0L);
        KnowledgeDataset dataset = new KnowledgeDataset();
        dataset.setDatasetId(10L);
        when(f.datasetMapper.selectById(10L)).thenReturn(dataset);
        doAnswer(invocation -> {
            KnowledgeSpace space = invocation.getArgument(0);
            space.setSpaceId(21L);
            assertThat(space.getDistanceMetric()).isEqualTo("cosine");
            assertThat(space.getDefaultTopK()).isEqualTo(5);
            assertThat(space.getScoreThreshold()).isEqualByComparingTo("0.35");
            return 1;
        }).when(f.spaceMapper).insert(any(KnowledgeSpace.class));

        assertThat(f.service.create(request())).isEqualTo(21L);
    }

    @Test
    void createRejectsDuplicateMissingDatasetAndUnavailableProfile() {
        Fixtures f = fixtures();
        when(f.spaceMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> f.service.create(request())).isInstanceOf(KmaException.class)
            .hasMessageContaining("已存在");

        when(f.spaceMapper.selectCount(any())).thenReturn(0L);
        when(f.datasetMapper.selectById(10L)).thenReturn(null);
        assertThatThrownBy(() -> f.service.create(request())).isInstanceOf(KmaException.class)
            .hasMessageContaining("数据集不存在");

        KnowledgeDataset dataset = new KnowledgeDataset();
        dataset.setEmbeddingProfileCode("missing");
        when(f.datasetMapper.selectById(10L)).thenReturn(dataset);
        when(f.profileMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> f.service.create(request())).isInstanceOf(KmaException.class)
            .hasMessageContaining("Profile 不可用");
    }

    @Test
    void updatesDeletesAndChangesStatus() {
        Fixtures f = fixtures();
        KnowledgeSpace space = spaceEntity();
        when(f.spaceMapper.selectById(21L)).thenReturn(space);
        KnowledgeDataset dataset = new KnowledgeDataset();
        when(f.datasetMapper.selectById(10L)).thenReturn(dataset);
        SpaceUpdateRequest update = new SpaceUpdateRequest();
        update.setSpaceId(21L);
        update.setDatasetId(10L);
        update.setName("新名称");
        update.setDescription("新描述");
        update.setEmbeddingModel("bge-m3-v2");
        update.setDistanceMetric("cosine");
        update.setChunkStrategy("recursive");
        update.setDefaultTopK(8);
        update.setScoreThreshold(new BigDecimal("0.5"));

        f.service.update(update);
        assertThat(space.getName()).isEqualTo("新名称");
        verify(f.spaceMapper).updateById(space);

        f.service.changeStatus(21L, "disabled");
        assertThat(space.getStatus()).isEqualTo("disabled");

        f.service.delete(21L);
        verify(f.aclMapper).delete(any());
        verify(f.spaceMapper).deleteById(21L);
    }

    @Test
    void missingSpaceMutationsFailClosed() {
        Fixtures f = fixtures();
        when(f.spaceMapper.selectById(404L)).thenReturn(null);
        SpaceUpdateRequest update = new SpaceUpdateRequest();
        update.setSpaceId(404L);
        assertThatThrownBy(() -> f.service.update(update)).isInstanceOf(KmaException.class);
        assertThatThrownBy(() -> f.service.delete(404L)).isInstanceOf(KmaException.class);
        assertThatThrownBy(() -> f.service.changeStatus(404L, "active")).isInstanceOf(KmaException.class);
        assertThatThrownBy(() -> f.service.getById(404L)).isInstanceOf(KmaException.class);
        assertThatThrownBy(() -> f.service.getBySpaceCode("missing")).isInstanceOf(KmaException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pagesAndGetsSpacesAsViewModels() {
        Fixtures f = fixtures();
        KnowledgeSpace space = spaceEntity();
        Page<KnowledgeSpace> selected = new Page<>(1, 10, 1);
        selected.setRecords(List.of(space));
        when(f.spaceMapper.selectPage(any(Page.class), any())).thenReturn(selected);
        when(f.spaceMapper.selectById(21L)).thenReturn(space);
        when(f.spaceMapper.selectBySpaceCode("party")).thenReturn(space);
        SpaceQueryRequest query = new SpaceQueryRequest();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setSpaceCode("party");
        query.setName("党建");
        query.setStatus("active");

        assertThat(f.service.page(query).getRecords()).singleElement()
            .satisfies(value -> assertThat(value.getSpaceCode()).isEqualTo("party"));
        assertThat(f.service.getById(21L).getName()).isEqualTo("党建知识库");
        assertThat(f.service.getBySpaceCode("party").getSpaceId()).isEqualTo(21L);
    }

    @Test
    void managesAclsAndRejectsDuplicates() {
        Fixtures f = fixtures();
        when(f.spaceMapper.selectById(21L)).thenReturn(spaceEntity());
        when(f.aclMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            KnowledgeSpaceAcl acl = invocation.getArgument(0);
            acl.setAclId(31L);
            return 1;
        }).when(f.aclMapper).insert(any(KnowledgeSpaceAcl.class));
        SpaceAclRequest request = new SpaceAclRequest();
        request.setSpaceId(21L);
        request.setPrincipalType("role");
        request.setPrincipalValue("editor");
        request.setPermission("read");
        assertThat(f.service.addAcl(request)).isEqualTo(31L);

        KnowledgeSpaceAcl acl = new KnowledgeSpaceAcl();
        acl.setAclId(31L);
        acl.setSpaceId(21L);
        when(f.aclMapper.selectList(any())).thenReturn(List.of(acl));
        when(f.aclMapper.selectById(31L)).thenReturn(acl);
        assertThat(f.service.listAcls(21L)).containsExactly(acl);
        f.service.removeAcl(31L);
        verify(f.aclMapper).deleteById(31L);

        when(f.aclMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> f.service.addAcl(request)).isInstanceOf(KmaException.class)
            .hasMessageContaining("ACL 已存在");
    }

    private SpaceCreateRequest request() {
        SpaceCreateRequest request = new SpaceCreateRequest();
        request.setDatasetId(10L);
        request.setSpaceCode("party");
        request.setName("党建知识库");
        request.setEmbeddingProvider("local-bge-m3");
        request.setEmbeddingModel("bge-m3");
        request.setEmbeddingDim(1024);
        return request;
    }

    private KnowledgeSpace spaceEntity() {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(21L);
        space.setDatasetId(10L);
        space.setSpaceCode("party");
        space.setName("党建知识库");
        space.setEmbeddingProvider("local-bge-m3");
        space.setEmbeddingModel("bge-m3");
        space.setEmbeddingDim(1024);
        space.setDistanceMetric("cosine");
        space.setDefaultTopK(5);
        space.setScoreThreshold(new BigDecimal("0.35"));
        space.setStatus("active");
        return space;
    }

    private Fixtures fixtures() {
        KnowledgeSpaceMapper spaceMapper = mock(KnowledgeSpaceMapper.class);
        KnowledgeSpaceAclMapper aclMapper = mock(KnowledgeSpaceAclMapper.class);
        KnowledgeDatasetMapper datasetMapper = mock(KnowledgeDatasetMapper.class);
        ModelProfileMapper profileMapper = mock(ModelProfileMapper.class);
        return new Fixtures(spaceMapper, aclMapper, datasetMapper, profileMapper,
            new KnowledgeSpaceServiceImpl(spaceMapper, aclMapper, datasetMapper, profileMapper));
    }

    private record Fixtures(KnowledgeSpaceMapper spaceMapper, KnowledgeSpaceAclMapper aclMapper,
                            KnowledgeDatasetMapper datasetMapper, ModelProfileMapper profileMapper,
                            KnowledgeSpaceServiceImpl service) {}
}
