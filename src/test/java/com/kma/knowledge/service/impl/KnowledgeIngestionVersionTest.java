package com.kma.knowledge.service.impl;

import com.kma.common.exception.KmaException;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.dto.DocIngestResult;
import com.kma.knowledge.dto.DocIngestTextRequest;
import com.kma.knowledge.dto.DocIngestFileRequest;
import com.kma.knowledge.dto.DocQueryRequest;
import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeChunkMapper;
import com.kma.knowledge.mapper.KnowledgeDocMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.service.KnowledgeIngestionJobService;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import com.kma.knowledge.storage.UploadTypeValidator;
import com.kma.knowledge.storage.KnowledgeStorage;
import com.kma.knowledge.storage.StorageLifecycleService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionVersionTest {

    @BeforeAll
    static void initializeMybatisLambdaMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.kma.knowledge.mapper.KnowledgeDocMapper");
        TableInfoHelper.initTableInfo(assistant, KnowledgeDoc.class);
    }

    @Mock private KnowledgeDocMapper docMapper;
    @Mock private KnowledgeChunkMapper chunkMapper;
    @Mock private KnowledgeSpaceMapper spaceMapper;
    @Mock private KnowledgeIngestionJobService ingestionJobService;
    @Mock private KnowledgeSpaceAclService aclService;
    @Mock private UploadTypeValidator uploadTypeValidator;
    @Mock private KnowledgeStorage knowledgeStorage;
    @Mock private StorageLifecycleService storageLifecycleService;

    private KnowledgeIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeIngestionServiceImpl(
            docMapper, chunkMapper, spaceMapper, ingestionJobService, aclService, uploadTypeValidator,
            knowledgeStorage, storageLifecycleService);
    }

    @Test
    void shouldReturnExistingDocumentForSameVersion() {
        stubSpaceByCode();
        KnowledgeDoc existing = existingDocument(2L);
        when(docMapper.selectList(any())).thenReturn(List.of(existing));

        DocIngestResult result = service.ingestText(request(2L));

        assertThat(result.getDocId()).isEqualTo(99L);
        verify(docMapper, never()).insert(any(KnowledgeDoc.class));
        verify(ingestionJobService, never()).enqueue(any(), any());
    }

    @Test
    void shouldRejectOutOfOrderVersion() {
        stubSpaceByCode();
        when(docMapper.selectList(any())).thenReturn(List.of(existingDocument(3L)));

        assertThatThrownBy(() -> service.ingestText(request(2L)))
            .isInstanceOf(KmaException.class)
            .hasMessageContaining("拒绝乱序旧版本");
        verify(docMapper, never()).insert(any(KnowledgeDoc.class));
    }

    @Test
    void listsAllVersionsForSelectedExternalReference() {
        KnowledgeDoc current = existingDocument(3L);
        current.setSpaceId(10L);
        current.setExternalRef("source:1");
        current.setIsActive(true);
        KnowledgeDoc previous = existingDocument(2L);
        previous.setSpaceId(10L);
        previous.setExternalRef("source:1");
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(10L);
        space.setSpaceCode("default");
        when(docMapper.selectById(99L)).thenReturn(current);
        when(spaceMapper.selectById(10L)).thenReturn(space);
        when(docMapper.selectList(any())).thenReturn(List.of(current, previous));

        assertThat(service.listVersions(99L)).extracting("sourceVersion").containsExactly(3L, 2L);
        verify(aclService).assertReadAccess("default");
    }

    @Test
    void ingestsTextIntoDurableStorageAndQueuesJob() throws Exception {
        stubSpaceByCode();
        when(docMapper.selectList(any())).thenReturn(List.of());
        when(knowledgeStorage.store(eq("default"), eq("document.txt"), any()))
            .thenReturn("default/default/document.txt");
        when(storageLifecycleService.registerStored(eq("default/default/document.txt"), anyLong(), any()))
            .thenReturn(71L);
        doAnswer(invocation -> {
            KnowledgeDoc doc = invocation.getArgument(0);
            doc.setDocId(101L);
            return 1;
        }).when(docMapper).insert(any(KnowledgeDoc.class));

        DocIngestResult result = execute(() -> service.ingestText(request(1L)));

        assertThat(result.getDocId()).isEqualTo(101L);
        verify(storageLifecycleService).bindDocument(71L, 101L);
        verify(ingestionJobService).enqueue(101L, "ingest");
    }

    @Test
    void ingestsFileWithCanonicalMimeAndNormalizedVersion() throws Exception {
        stubSpaceByCode();
        when(docMapper.selectList(any())).thenReturn(List.of());
        when(uploadTypeValidator.validate(any(), eq("guide.pdf"))).thenReturn("application/pdf");
        when(knowledgeStorage.store(eq("default"), eq("guide.pdf"), any()))
            .thenReturn("default/default/guide.pdf");
        when(storageLifecycleService.registerStored(any(), eq(3L), any())).thenReturn(81L);
        doAnswer(invocation -> {
            KnowledgeDoc doc = invocation.getArgument(0);
            doc.setDocId(102L);
            assertThat(doc.getTitle()).isEqualTo("guide");
            assertThat(doc.getMimeType()).isEqualTo("application/pdf");
            assertThat(doc.getSourceVersion()).isEqualTo(1L);
            return 1;
        }).when(docMapper).insert(any(KnowledgeDoc.class));
        DocIngestFileRequest request = new DocIngestFileRequest();
        request.setSpaceCode("default");
        request.setSourceVersion(0L);
        request.setExternalRef("file:1");
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", new byte[]{1, 2, 3});

        DocIngestResult result = execute(() -> service.ingestFile(request, file));

        assertThat(result.getDocId()).isEqualTo(102L);
        verify(storageLifecycleService).bindDocument(81L, 102L);
        verify(ingestionJobService).enqueue(102L, "ingest");
    }

    @Test
    void rejectsEmptyFileAndUnknownSpace() {
        DocIngestFileRequest request = new DocIngestFileRequest();
        request.setSpaceCode("missing");
        MockMultipartFile empty = new MockMultipartFile("file", new byte[0]);
        assertThatThrownBy(() -> service.ingestFile(request, empty))
            .isInstanceOf(KmaException.class).hasMessageContaining("不能为空");

        assertThatThrownBy(() -> service.ingestText(request(1L)))
            .isInstanceOf(KmaException.class).hasMessageContaining("知识空间不存在");
    }

    @Test
    void reportsStatusReindexesAndDeletesWithAcl() {
        KnowledgeDoc doc = existingDocument(1L);
        doc.setSpaceId(10L);
        doc.setStorageObjectId(73L);
        doc.setErrorMessage("failed");
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(10L);
        space.setSpaceCode("default");
        when(docMapper.selectById(99L)).thenReturn(doc);
        when(spaceMapper.selectById(10L)).thenReturn(space);

        assertThat(service.getStatus(99L).getDocId()).isEqualTo(99L);
        service.reindex(99L);
        assertThat(doc.getParseStatus()).isEqualTo("pending");
        assertThat(doc.getChunkCount()).isZero();
        assertThat(doc.getErrorMessage()).isNull();
        verify(docMapper).updateById(doc);
        verify(ingestionJobService).enqueue(99L, "reindex");

        service.delete(99L);
        verify(chunkMapper).deleteByDocId(99L);
        verify(docMapper).deleteById(99L);
        verify(storageLifecycleService).markOrphanIfUnreferenced(73L);
        verify(aclService).assertReadAccess("default");
        verify(aclService).assertIngestAccess("default");
        verify(aclService).assertAdminAccess("default");
    }

    @Test
    void missingDocumentOperationsFailClosed() {
        when(docMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> service.getStatus(404L)).isInstanceOf(KmaException.class);
        assertThatThrownBy(() -> service.reindex(404L)).isInstanceOf(KmaException.class);
        assertThatThrownBy(() -> service.delete(404L)).isInstanceOf(KmaException.class);
        assertThatThrownBy(() -> service.listVersions(404L))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(404);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pagesDocumentsWithAclAndMapsSpaceCodes() {
        KnowledgeDoc doc = existingDocument(1L);
        doc.setSpaceId(10L);
        Page<KnowledgeDoc> selected = new Page<>(1, 20, 1);
        selected.setRecords(List.of(doc));
        when(spaceMapper.selectBySpaceCode("default")).thenReturn(space());
        when(docMapper.selectPage(any(Page.class), any())).thenReturn(selected);
        when(spaceMapper.selectByIds(List.of(10L))).thenReturn(List.of(space()));
        DocQueryRequest request = new DocQueryRequest();
        request.setPageNum(1);
        request.setPageSize(20);
        request.setSpaceCode("default");
        request.setTitle("doc");
        request.setParseStatus("success");

        assertThat(service.page(request).getRecords()).singleElement()
            .satisfies(value -> assertThat(value.getSpaceCode()).isEqualTo("default"));
        verify(aclService).assertReadAccess("default");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pagesOnlyReadableSpacesAndHandlesNoAclSpaces() {
        when(aclService.getReadableSpaceIds()).thenReturn(java.util.Set.of(10L));
        when(docMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10, 0));
        DocQueryRequest request = new DocQueryRequest();
        request.setPageNum(1);
        request.setPageSize(10);
        assertThat(service.page(request).getRecords()).isEmpty();

        when(aclService.getReadableSpaceIds()).thenReturn(java.util.Set.of());
        assertThat(service.page(request).getRecords()).isEmpty();
    }

    @Test
    void storageFailuresBecomeStableDomainErrors() throws Exception {
        stubSpaceByCode();
        when(docMapper.selectList(any())).thenReturn(List.of());
        doThrow(new java.io.IOException("disk full")).when(knowledgeStorage)
            .store(eq("default"), any(), any());

        assertThatThrownBy(() -> execute(() -> service.ingestText(request(1L))))
            .isInstanceOf(KmaException.class).hasMessageContaining("文本保存失败");
        verify(docMapper, never()).insert(any(KnowledgeDoc.class));
    }

    private DocIngestTextRequest request(long version) {
        DocIngestTextRequest request = new DocIngestTextRequest();
        request.setSpaceCode("default");
        request.setTitle("document");
        request.setContent("content");
        request.setExternalRef("source:1");
        request.setSourceVersion(version);
        return request;
    }

    private void stubSpaceByCode() {
        when(spaceMapper.selectBySpaceCode("default")).thenReturn(space());
    }

    private KnowledgeSpace space() {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(10L);
        space.setSpaceCode("default");
        return space;
    }

    private KnowledgeDoc existingDocument(long version) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocId(99L);
        doc.setTitle("document");
        doc.setSourceVersion(version);
        doc.setParseStatus("success");
        doc.setChunkCount(1);
        doc.setCreateTime(LocalDateTime.now());
        return doc;
    }

    private <T> T execute(Supplier<T> supplier) {
        return supplier.get();
    }
}
