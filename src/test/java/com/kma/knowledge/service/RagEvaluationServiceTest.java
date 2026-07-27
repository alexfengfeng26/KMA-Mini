package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.EvaluationCaseRequest;
import com.kma.knowledge.dto.EvaluationDatasetRequest;
import com.kma.knowledge.dto.EvaluationGateRequest;
import com.kma.knowledge.dto.QAResult;
import com.kma.knowledge.rag.evaluation.AnswerCorrectnessJudge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagEvaluationServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private KnowledgeQAService qaService;
    @Mock private AnswerCorrectnessJudge correctnessJudge;
    private RagEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new RagEvaluationService(jdbc, new ObjectMapper(), qaService, correctnessJudge);
    }

    @Test
    void createsListsAndAddsTraceableEvaluationCases() throws Exception {
        EvaluationDatasetRequest dataset = new EvaluationDatasetRequest();
        dataset.setName("党建发布集");
        dataset.setSpaceCode("party");
        dataset.setDescription("KMA-QA.1 baseline");
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("党建发布集"),
            eq("party"), eq("KMA-QA.1 baseline"))).thenReturn(7L);
        assertThat(call(() -> service.createDataset(dataset))).isEqualTo(7L);

        List<Map<String, Object>> rows = List.of(Map.of("evaluation_dataset_id", 7L));
        when(jdbc.queryForList(anyString())).thenReturn(rows);
        assertThat(call(service::listDatasets)).isSameAs(rows);
        verify(jdbc).queryForList(argThat(sql -> sql.contains("'active' AS status")
            && !sql.contains("d.status")));

        stubDataset(true);
        EvaluationCaseRequest request = new EvaluationCaseRequest();
        request.setQuestion("党的根本宗旨是什么？");
        request.setExpectedExternalRefs(List.of("party-charter:1"));
        request.setExpectedAnswer("全心全意为人民服务");
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(7L), anyString(),
            anyString(), anyString(), eq(false))).thenReturn(11L);
        assertThat(call(() -> service.addCase(7L, request))).isEqualTo(11L);
    }

    @Test
    void runsDatasetAndPassesAllRagReleaseMetrics() throws Exception {
        stubDatasetAndCases();
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(7L), eq(5))).thenReturn(99L);
        ChunkHitVO hit = new ChunkHitVO();
        hit.setExternalRef("party-charter:1");
        QAResult answer = new QAResult();
        answer.setAnswered(true);
        answer.setAnswer("全心全意为人民服务");
        answer.setCitations(List.of(hit));
        when(qaService.answer(any())).thenReturn(answer);
        when(correctnessJudge.judge(anyString(), anyString()))
            .thenReturn(new AnswerCorrectnessJudge.Judgment(1.0, "exact"));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("kma_evaluation_gate"),
            eq(7L))).thenReturn(List.of());

        Map<String, Object> result = call(() -> service.run(7L, 5));

        assertThat(result).containsEntry("runId", 99L)
            .containsEntry("caseCount", 1)
            .containsEntry("recallAtK", 1.0)
            .containsEntry("mrr", 1.0)
            .containsEntry("citationPrecision", 1.0)
            .containsEntry("answerCorrectness", 1.0)
            .containsEntry("gatePassed", true);
        verify(qaService).answer(any());
    }

    @Test
    void failedQaMarksRunFailedAndPreservesStableError() throws Exception {
        stubDatasetAndCases();
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(7L), eq(3))).thenReturn(100L);
        when(qaService.answer(any())).thenThrow(new KmaException(503, "model unavailable"));

        assertThatThrownBy(() -> call(() -> service.run(7L, 3)))
            .isInstanceOf(KmaException.class).hasMessageContaining("model unavailable");
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("status = 'failed'"),
            eq("model unavailable"), eq(100L));
    }

    @Test
    void configuresReadsAndDefaultsReleaseGate() throws Exception {
        stubDataset(true);
        EvaluationGateRequest gate = new EvaluationGateRequest();
        gate.setMinRecallAtK(0.9);
        gate.setMinMrr(0.8);
        gate.setMinCitationPrecision(0.9);
        gate.setMinRefusalAccuracy(1.0);
        gate.setMinAnswerCorrectness(0.85);
        gate.setMinCaseCount(10);
        gate.setEnabled(true);
        run(() -> service.configureGate(7L, gate));
        verify(jdbc).update(anyString(), eq(7L), eq(0.9), eq(0.8), eq(0.9),
            eq(1.0), eq(0.85), eq(10), eq(true));

        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("min_recall_at_k"),
            eq(7L))).thenReturn(List.of());
        assertThat(call(() -> service.getGate(7L))).containsEntry("min_recall_at_k", 0.80)
            .containsEntry("enabled", true);
    }

    @Test
    void releaseAssertionFailsClosedForMissingRunningOrFailedRuns() {
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of());
        assertThatThrownBy(() -> call(() -> service.assertReleaseReady(1L)))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(404);

        when(jdbc.queryForList(anyString(), eq(2L)))
            .thenReturn(List.of(Map.of("status", "running", "gate_passed", false)));
        assertThatThrownBy(() -> call(() -> service.assertReleaseReady(2L)))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(409);

        when(jdbc.queryForList(anyString(), eq(3L)))
            .thenReturn(List.of(Map.of("status", "completed", "gate_passed", false,
                "gate_failures", List.of("recallAtK"))));
        assertThatThrownBy(() -> call(() -> service.assertReleaseReady(3L)))
            .isInstanceOf(KmaException.class).hasMessageContaining("门禁未通过");

        Map<String, Object> ready = Map.of("status", "completed", "gate_passed", true);
        when(jdbc.queryForList(anyString(), eq(4L))).thenReturn(List.of(ready));
        assertThat(call(() -> service.assertReleaseReady(4L))).isSameAs(ready);
    }

    @Test
    void missingDatasetIsAlwaysRejected() throws Exception {
        stubDataset(false);
        assertThatThrownBy(() -> call(() -> service.listRuns(404L)))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(404);
    }

    @SuppressWarnings("unchecked")
    private void stubDataset(boolean exists) throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), any())).thenAnswer(invocation -> {
            if (!exists) return List.of();
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong(1)).thenReturn(7L);
            when(rs.getString(2)).thenReturn("party");
            RowMapper<Object> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    @SuppressWarnings("unchecked")
    private void stubDatasetAndCases() throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            ResultSet rs = mock(ResultSet.class);
            RowMapper<Object> mapper = invocation.getArgument(1);
            if (sql.contains("kma_evaluation_dataset")) {
                when(rs.getLong(1)).thenReturn(7L);
                when(rs.getString(2)).thenReturn("party");
            } else {
                when(rs.getLong(1)).thenReturn(11L);
                when(rs.getString(2)).thenReturn("党的根本宗旨是什么？");
                when(rs.getString(3)).thenReturn("[\"party-charter:1\"]");
                when(rs.getString(4)).thenReturn("全心全意为人民服务");
                when(rs.getBoolean(5)).thenReturn(false);
            }
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    private <T> T call(Callable<T> action) {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        try { value.set(action.call()); }
        catch (RuntimeException ex) { error.set(ex); }
        catch (Exception ex) { error.set(new RuntimeException(ex)); }
        if (error.get() != null) throw error.get();
        return value.get();
    }

    private void run(Runnable action) {
        action.run();
    }
}
