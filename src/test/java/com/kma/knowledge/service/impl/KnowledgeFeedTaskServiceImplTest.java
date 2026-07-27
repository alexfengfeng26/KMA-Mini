package com.kma.knowledge.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.dto.DocIngestTextRequest;
import com.kma.knowledge.dto.FeedTaskQueryRequest;
import com.kma.knowledge.entity.KnowledgeFeedTask;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeFeedTaskMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.service.KnowledgeIngestionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeFeedTaskServiceImplTest {
    @Mock private KnowledgeFeedTaskMapper taskMapper;
    @Mock private KnowledgeSpaceMapper spaceMapper;
    @Mock private KnowledgeIngestionService ingestionService;
    private KnowledgeProperties properties;
    private KnowledgeFeedTaskServiceImpl service;

    @BeforeAll
    static void initializeMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.kma.knowledge.mapper.KnowledgeFeedTaskMapper");
        TableInfoHelper.initTableInfo(assistant, KnowledgeFeedTask.class);
    }

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        properties.getFeed().setMaxRetry(3);
        properties.getFeed().setBatchSize(10);
        service = new KnowledgeFeedTaskServiceImpl(taskMapper, spaceMapper,
            ingestionService, properties, new ObjectMapper());
    }

    @Test
    void incompleteOrUnknownTargetSubmissionsAreIgnored() {
        assertThat(service.submit(null, 1L, 1L, "party", request())).isNull();
        assertThat(service.submit("course", null, 1L, "party", request())).isNull();
        assertThat(service.submit("course", 1L, 1L, null, request())).isNull();
        assertThat(service.submit("course", 1L, 1L, "party", null)).isNull();
        DocIngestTextRequest incomplete = request();
        incomplete.setContent(" ");
        assertThat(service.submit("course", 1L, 1L, "party", incomplete)).isNull();
        when(spaceMapper.selectBySpaceCode("party")).thenReturn(null);
        assertThat(service.submit("course", 1L, 1L, "party", request())).isNull();
        verify(taskMapper, never()).insert(any(KnowledgeFeedTask.class));
    }

    @Test
    void duplicatePendingSubmissionReturnsExistingTask() {
        when(spaceMapper.selectBySpaceCode("party")).thenReturn(space());
        KnowledgeFeedTask existing = task(8L, "pending", 0, 3);
        when(taskMapper.selectOne(any())).thenReturn(existing);
        assertThat(service.submit("course", 1L, 2L, "party", request())).isEqualTo(8L);
        verify(taskMapper, never()).insert(any(KnowledgeFeedTask.class));
    }

    @Test
    void createsImmutablePayloadSnapshotForNewSubmission() {
        when(spaceMapper.selectBySpaceCode("party")).thenReturn(space());
        when(taskMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            KnowledgeFeedTask task = invocation.getArgument(0);
            task.setTaskId(9L);
            assertThat(task.getMeta()).contains("党建课程", "course:1");
            assertThat(task.getStatus()).isEqualTo("pending");
            return 1;
        }).when(taskMapper).insert(any(KnowledgeFeedTask.class));
        assertThat(service.submit("course", 1L, 2L, "party", request())).isEqualTo(9L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pagesAndAggregatesTaskStatistics() {
        KnowledgeFeedTask task = task(9L, "success", 1, 3);
        task.setSourceType("course");
        task.setSourceId(1L);
        task.setSourceVersionId(2L);
        task.setSpaceCode("party");
        Page<KnowledgeFeedTask> selected = new Page<>(1, 10, 1);
        selected.setRecords(List.of(task));
        when(taskMapper.selectPage(any(Page.class), any())).thenReturn(selected);
        FeedTaskQueryRequest query = new FeedTaskQueryRequest();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setSourceType("course");
        query.setSpaceCode("party");
        query.setStatus("success");
        assertThat(service.page(query).getRecords()).singleElement()
            .satisfies(value -> assertThat(value.getTaskId()).isEqualTo(9L));

        when(taskMapper.selectMaps(any())).thenReturn(List.of(
            Map.of("status", "success", "count", 4L),
            Map.of("status", "dead", "count", 1L)));
        assertThat(service.stats()).containsEntry("success", 4L).containsEntry("dead", 1L);
    }

    @Test
    void manualRetryExecutesImmediatelyAndMarksSuccess() {
        KnowledgeFeedTask task = task(10L, "dead", 2, 3);
        task.setSpaceCode("party");
        task.setSourceVersionId(4L);
        task.setMeta(new ObjectMapper().createObjectNode()
            .put("title", "党建课程").put("content", "content")
            .put("sourceTag", "course").put("externalRef", "course:1").toString());
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(taskMapper.update(any(KnowledgeFeedTask.class), any())).thenReturn(1);

        service.retry(10L);

        verify(ingestionService).ingestTextAsSystem(any());
        verify(taskMapper, org.mockito.Mockito.atLeast(2)).updateById(any(KnowledgeFeedTask.class));
    }

    @Test
    void retryRejectsMissingTaskAndInvalidPayloadMovesToDeadLetter() {
        when(taskMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> service.retry(404L)).isInstanceOf(KmaException.class);

        KnowledgeFeedTask task = task(11L, "pending", 0, 3);
        task.setSpaceCode("party");
        task.setMeta("not-json");
        when(taskMapper.selectById(11L)).thenReturn(task);
        when(taskMapper.update(any(KnowledgeFeedTask.class), any())).thenReturn(1);
        service.retry(11L);
        verify(ingestionService, never()).ingestTextAsSystem(any());
        verify(taskMapper, org.mockito.Mockito.atLeast(2)).updateById(any(KnowledgeFeedTask.class));
    }

    @Test
    void schedulerHandlesTransientFailureDeadLetterAndLostOptimisticLock() {
        properties.getFeed().setEnabled(false);
        service.processPendingTasks();
        verify(taskMapper, never()).selectList(any());

        properties.getFeed().setEnabled(true);
        KnowledgeFeedTask transientTask = task(12L, "pending", 0, 3);
        transientTask.setSpaceCode("party");
        transientTask.setMeta(payload());
        KnowledgeFeedTask deadTask = task(13L, "pending", 2, 3);
        deadTask.setSpaceCode("party");
        deadTask.setMeta(payload());
        KnowledgeFeedTask lost = task(14L, "pending", 0, 3);
        lost.setSpaceCode("party");
        lost.setMeta(payload());
        when(taskMapper.selectList(any())).thenReturn(List.of(transientTask, deadTask, lost));
        when(taskMapper.update(any(KnowledgeFeedTask.class), any())).thenReturn(1, 1, 0);
        doThrow(new KmaException(503, "model unavailable")).when(ingestionService).ingestTextAsSystem(any());

        service.processPendingTasks();

        verify(ingestionService, org.mockito.Mockito.times(2)).ingestTextAsSystem(any());
        verify(taskMapper, org.mockito.Mockito.atLeast(2)).updateById(any(KnowledgeFeedTask.class));
    }

    @Test
    void serializationFailureBecomesDomainError() throws Exception {
        ObjectMapper broken = org.mockito.Mockito.mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new JsonProcessingException("broken") {});
        service = new KnowledgeFeedTaskServiceImpl(taskMapper, spaceMapper,
            ingestionService, properties, broken);
        when(spaceMapper.selectBySpaceCode("party")).thenReturn(space());
        when(taskMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.submit("course", 1L, 1L, "party", request()))
            .isInstanceOf(KmaException.class).hasMessageContaining("序列化失败");
    }

    private DocIngestTextRequest request() {
        DocIngestTextRequest request = new DocIngestTextRequest();
        request.setSpaceCode("party");
        request.setTitle("党建课程");
        request.setContent("content");
        request.setSourceTag("course");
        request.setExternalRef("course:1");
        return request;
    }

    private KnowledgeSpace space() {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(1L);
        space.setSpaceCode("party");
        return space;
    }

    private KnowledgeFeedTask task(long id, String status, int retry, int maxRetry) {
        KnowledgeFeedTask task = new KnowledgeFeedTask();
        task.setTaskId(id);
        task.setStatus(status);
        task.setRetryCount(retry);
        task.setMaxRetry(maxRetry);
        task.setNextExecuteTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        return task;
    }

    private String payload() {
        return "{\"title\":\"党建课程\",\"content\":\"content\",\"sourceTag\":\"course\",\"externalRef\":\"course:1\"}";
    }
}
