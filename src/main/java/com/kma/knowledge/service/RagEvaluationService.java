package com.kma.knowledge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.result.PageResult;
import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.EvaluationCaseRequest;
import com.kma.knowledge.dto.EvaluationDatasetRequest;
import com.kma.knowledge.dto.QARequest;
import com.kma.knowledge.dto.QAResult;
import com.kma.knowledge.dto.EvaluationGateRequest;
import com.kma.knowledge.rag.evaluation.AnswerCorrectnessJudge;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RagEvaluationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final KnowledgeQAService qaService;
    private final AnswerCorrectnessJudge correctnessJudge;

    public RagEvaluationService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                                ObjectMapper objectMapper,
                                KnowledgeQAService qaService,
                                AnswerCorrectnessJudge correctnessJudge) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.qaService = qaService;
        this.correctnessJudge = correctnessJudge;
    }

    public Long createDataset(EvaluationDatasetRequest request) {
        return jdbc.queryForObject("""
            INSERT INTO kma_evaluation_dataset(name, space_code, description)
            VALUES (?, ?, ?) RETURNING evaluation_dataset_id
            """, Long.class, request.getName(), request.getSpaceCode(), request.getDescription());
    }

    public List<Map<String, Object>> listDatasets() {
        return jdbc.queryForList("""
            SELECT d.evaluation_dataset_id,d.name,d.space_code,d.description,'active' AS status,d.create_time,
                   count(DISTINCT c.evaluation_case_id) case_count,
                   max(r.start_time) last_run_time
            FROM kma_evaluation_dataset d
            LEFT JOIN kma_evaluation_case c ON c.evaluation_dataset_id=d.evaluation_dataset_id
            LEFT JOIN kma_evaluation_run r ON r.evaluation_dataset_id=d.evaluation_dataset_id
            GROUP BY d.evaluation_dataset_id,d.name,d.space_code,d.description,d.create_time
            ORDER BY d.create_time DESC
            """);
    }

    public Long addCase(Long datasetId, EvaluationCaseRequest request) {
        ensureDataset(datasetId);
        try {
            return jdbc.queryForObject("""
                INSERT INTO kma_evaluation_case
                    (evaluation_dataset_id, question, expected_external_refs, expected_answer, should_refuse)
                VALUES (?, ?, ?::jsonb, ?, ?) RETURNING evaluation_case_id
                """, Long.class, datasetId, request.getQuestion(),
                objectMapper.writeValueAsString(request.getExpectedExternalRefs()),
                request.getExpectedAnswer(), request.isShouldRefuse());
        } catch (Exception ex) {
            throw new KmaException("评测用例保存失败", ex);
        }
    }

    public Map<String, Object> run(Long datasetId, int topK) {
        Dataset dataset = ensureDataset(datasetId);
        Long runId = jdbc.queryForObject("""
            INSERT INTO kma_evaluation_run(evaluation_dataset_id, status, top_k)
            VALUES (?, 'running', ?) RETURNING evaluation_run_id
            """, Long.class, datasetId, topK);
        try {
            List<EvaluationCase> cases = loadCases(datasetId);
            List<CaseMetrics> metrics = new ArrayList<>();
            for (EvaluationCase evaluationCase : cases) {
                metrics.add(runCase(runId, dataset, evaluationCase, topK));
            }
            Map<String, Object> summary = summarize(runId, cases.size(), metrics);
            GateDecision decision = evaluateGate(datasetId, summary);
            summary.put("gatePassed", decision.passed());
            summary.put("gateFailures", decision.failures());
            jdbc.update("""
                UPDATE kma_evaluation_run SET status = 'completed', metrics = ?::jsonb, gate_passed=?,
                    gate_failures=?::jsonb,end_time = now()
                WHERE evaluation_run_id = ?
                """, objectMapper.writeValueAsString(summary), decision.passed(),
                objectMapper.writeValueAsString(decision.failures()), runId);
            return summary;
        } catch (Exception ex) {
            jdbc.update("""
                UPDATE kma_evaluation_run SET status = 'failed', error_message = ?, end_time = now()
                WHERE evaluation_run_id = ?
                """, truncate(ex.getMessage()), runId);
            throw ex instanceof RuntimeException runtime ? runtime : new KmaException("评测执行失败", ex);
        }
    }

    public List<Map<String, Object>> listRuns(Long datasetId) {
        ensureDataset(datasetId);
        return jdbc.queryForList("""
            SELECT evaluation_run_id, status, top_k, metrics, gate_passed, gate_failures,
                   error_message, start_time, end_time
            FROM kma_evaluation_run
            WHERE evaluation_dataset_id = ? ORDER BY start_time DESC LIMIT 100
            """, datasetId);
    }

    public PageResult<Map<String, Object>> runPage(Long datasetId, int pageNum, int pageSize, String keyword,
                                                   String sortBy, String sortOrder) {
        ensureDataset(datasetId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String pattern = "%" + normalizedKeyword + "%";
        String orderColumn = switch (sortBy) {
            case "status" -> "status";
            case "topK" -> "top_k";
            case "endTime" -> "end_time";
            default -> "start_time";
        };
        String direction = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM kma_evaluation_run
            WHERE evaluation_dataset_id=?
              AND (?='' OR status ILIKE ? OR COALESCE(error_message,'') ILIKE ?)
            """, Long.class, datasetId, normalizedKeyword, pattern, pattern);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT evaluation_run_id,status,top_k,metrics,gate_passed,gate_failures,
                   error_message,start_time,end_time
            FROM kma_evaluation_run
            WHERE evaluation_dataset_id=?
              AND (?='' OR status ILIKE ? OR COALESCE(error_message,'') ILIKE ?)
            ORDER BY %s %s, evaluation_run_id DESC LIMIT ? OFFSET ?
            """.formatted(orderColumn, direction), datasetId, normalizedKeyword, pattern, pattern,
            pageSize, (pageNum - 1) * pageSize);
        return new PageResult<>(rows, total == null ? 0 : total, pageNum, pageSize);
    }

    public void configureGate(Long datasetId, EvaluationGateRequest request) {
        ensureDataset(datasetId);
        jdbc.update("""
            INSERT INTO kma_evaluation_gate(evaluation_dataset_id,min_recall_at_k,min_mrr,
                min_citation_precision,min_refusal_accuracy,min_answer_correctness,min_case_count,enabled)
            VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (evaluation_dataset_id) DO UPDATE SET
                min_recall_at_k=EXCLUDED.min_recall_at_k,min_mrr=EXCLUDED.min_mrr,
                min_citation_precision=EXCLUDED.min_citation_precision,
                min_refusal_accuracy=EXCLUDED.min_refusal_accuracy,
                min_answer_correctness=EXCLUDED.min_answer_correctness,min_case_count=EXCLUDED.min_case_count,
                enabled=EXCLUDED.enabled,update_time=now()
            """, datasetId, request.getMinRecallAtK(), request.getMinMrr(),
            request.getMinCitationPrecision(), request.getMinRefusalAccuracy(), request.getMinAnswerCorrectness(),
            request.getMinCaseCount(), request.isEnabled());
    }

    public Map<String, Object> getGate(Long datasetId) {
        ensureDataset(datasetId);
        List<Map<String, Object>> values = jdbc.queryForList("""
            SELECT min_recall_at_k,min_mrr,min_citation_precision,min_refusal_accuracy,
                   min_answer_correctness,min_case_count,enabled,update_time
            FROM kma_evaluation_gate WHERE evaluation_dataset_id=?
            """, datasetId);
        if (!values.isEmpty()) return values.get(0);
        return Map.of("min_recall_at_k", 0.80, "min_mrr", 0.60,
            "min_citation_precision", 0.80, "min_refusal_accuracy", 0.90,
            "min_answer_correctness", 0.70, "min_case_count", 1, "enabled", true);
    }

    public Map<String, Object> assertReleaseReady(Long runId) {
        List<Map<String, Object>> values = jdbc.queryForList("""
            SELECT evaluation_run_id,status,gate_passed,gate_failures,metrics
            FROM kma_evaluation_run WHERE evaluation_run_id=?
            """, runId);
        if (values.isEmpty()) throw new KmaException(404, "评测运行不存在");
        Map<String, Object> run = values.get(0);
        if (!"completed".equals(run.get("status"))) throw new KmaException(409, "评测运行尚未完成");
        if (!Boolean.TRUE.equals(run.get("gate_passed"))) {
            throw new KmaException(409, "RAG 发布门禁未通过: " + run.get("gate_failures"));
        }
        return run;
    }

    private CaseMetrics runCase(Long runId, Dataset dataset, EvaluationCase evaluationCase, int topK) throws Exception {
        long start = System.currentTimeMillis();
        QARequest qaRequest = new QARequest();
        qaRequest.setSpaceCode(dataset.spaceCode());
        qaRequest.setQuery(evaluationCase.question());
        qaRequest.setTopK(topK);
        qaRequest.setStream(false);
        QAResult answer = qaService.answer(qaRequest);
        List<ChunkHitVO> hits = answer.getCitations() == null ? List.of() : answer.getCitations();
        int latency = (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - start);
        List<String> refs = hits.stream().map(ChunkHitVO::getExternalRef).filter(value -> value != null && !value.isBlank()).toList();
        Set<String> expected = new LinkedHashSet<>(evaluationCase.expectedRefs());
        long matched = refs.stream().filter(expected::contains).distinct().count();
        double recall = expected.isEmpty() ? (refs.isEmpty() ? 1.0 : 0.0) : (double) matched / expected.size();
        double precision = refs.isEmpty() ? (expected.isEmpty() ? 1.0 : 0.0) : (double) matched / new LinkedHashSet<>(refs).size();
        double reciprocalRank = 0.0;
        for (int i = 0; i < refs.size(); i++) {
            if (expected.contains(refs.get(i))) {
                reciprocalRank = 1.0 / (i + 1.0);
                break;
            }
        }
        boolean refused = !Boolean.TRUE.equals(answer.getAnswered());
        boolean refusalCorrect = evaluationCase.shouldRefuse() == refused;
        AnswerCorrectnessJudge.Judgment judgment = evaluationCase.shouldRefuse()
            ? new AnswerCorrectnessJudge.Judgment(refusalCorrect ? 1.0 : 0.0, "拒答行为检查")
            : correctnessJudge.judge(evaluationCase.expectedAnswer(), answer.getAnswer());
        jdbc.update("""
            INSERT INTO kma_evaluation_result
                (evaluation_run_id, evaluation_case_id, retrieved_external_refs,
                 recall_at_k, reciprocal_rank, citation_precision, refusal_correct, latency_millis,
                 generated_answer,answer_correctness,judge_reason)
            VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
            """, runId, evaluationCase.id(), objectMapper.writeValueAsString(refs),
            recall, reciprocalRank, precision, refusalCorrect, latency, answer.getAnswer(),
            judgment.score(), truncate(judgment.reason()));
        return new CaseMetrics(recall, reciprocalRank, precision, refusalCorrect, judgment.score(), latency);
    }

    private Map<String, Object> summarize(Long runId, int caseCount, List<CaseMetrics> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("caseCount", caseCount);
        result.put("recallAtK", average(values.stream().map(CaseMetrics::recall).toList()));
        result.put("mrr", average(values.stream().map(CaseMetrics::reciprocalRank).toList()));
        result.put("citationPrecision", average(values.stream().map(CaseMetrics::precision).toList()));
        result.put("refusalAccuracy", values.isEmpty() ? 0.0
            : (double) values.stream().filter(CaseMetrics::refusalCorrect).count() / values.size());
        result.put("answerCorrectness", average(values.stream().map(CaseMetrics::answerCorrectness).toList()));
        result.put("averageLatencyMillis", average(values.stream().map(value -> (double) value.latency()).toList()));
        return result;
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private Dataset ensureDataset(Long datasetId) {
        List<Dataset> values = jdbc.query("""
            SELECT evaluation_dataset_id, space_code FROM kma_evaluation_dataset
            WHERE evaluation_dataset_id = ?
            """, (rs, row) -> new Dataset(rs.getLong(1), rs.getString(2)), datasetId);
        if (values.isEmpty()) throw new KmaException(404, "评测数据集不存在");
        return values.get(0);
    }

    private List<EvaluationCase> loadCases(Long datasetId) {
        return jdbc.query("""
            SELECT evaluation_case_id, question, expected_external_refs, expected_answer, should_refuse
            FROM kma_evaluation_case WHERE evaluation_dataset_id = ? ORDER BY evaluation_case_id
            """, (rs, row) -> new EvaluationCase(rs.getLong(1), rs.getString(2),
            parseRefs(rs.getString(3)), rs.getString(4), rs.getBoolean(5)),
            datasetId);
    }

    private List<String> parseRefs(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            throw new KmaException("评测用例引用格式无效", ex);
        }
    }

    private String truncate(String value) { return value == null ? "未知错误" : value.substring(0, Math.min(1000, value.length())); }
    private record Dataset(Long id, String spaceCode) {}
    private GateDecision evaluateGate(Long datasetId, Map<String, Object> metrics) {
        List<Map<String, Object>> gates = jdbc.queryForList("""
            SELECT * FROM kma_evaluation_gate WHERE evaluation_dataset_id=? AND enabled=TRUE
            """, datasetId);
        Map<String, Object> gate = gates.isEmpty() ? Map.of(
            "min_recall_at_k", 0.80, "min_mrr", 0.60, "min_citation_precision", 0.80,
            "min_refusal_accuracy", 0.90, "min_answer_correctness", 0.70, "min_case_count", 1) : gates.get(0);
        List<String> failures = new ArrayList<>();
        check(failures, "caseCount", number(metrics, "caseCount"), number(gate, "min_case_count"));
        check(failures, "recallAtK", number(metrics, "recallAtK"), number(gate, "min_recall_at_k"));
        check(failures, "mrr", number(metrics, "mrr"), number(gate, "min_mrr"));
        check(failures, "citationPrecision", number(metrics, "citationPrecision"), number(gate, "min_citation_precision"));
        check(failures, "refusalAccuracy", number(metrics, "refusalAccuracy"), number(gate, "min_refusal_accuracy"));
        check(failures, "answerCorrectness", number(metrics, "answerCorrectness"), number(gate, "min_answer_correctness"));
        return new GateDecision(failures.isEmpty(), failures);
    }

    private void check(List<String> failures, String name, double actual, double minimum) {
        if (actual < minimum) failures.add(name + "=" + actual + " < " + minimum);
    }
    private double number(Map<String, Object> values, String key) { return ((Number) values.get(key)).doubleValue(); }

    private record EvaluationCase(Long id, String question, List<String> expectedRefs, String expectedAnswer, boolean shouldRefuse) {}
    private record CaseMetrics(double recall, double reciprocalRank, double precision, boolean refusalCorrect,
                               double answerCorrectness, int latency) {}
    private record GateDecision(boolean passed, List<String> failures) {}
}
