package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.dto.EvaluationCaseRequest;
import com.kma.knowledge.dto.QaFeedbackRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class QaFeedbackService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RagEvaluationService evaluationService;
    private final SecurityAuditService audit;

    public QaFeedbackService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc, ObjectMapper objectMapper,
                             RagEvaluationService evaluationService, SecurityAuditService audit) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.evaluationService = evaluationService;
        this.audit = audit;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Long record(QaFeedbackRequest request) {
        Long userId = KmaIdentityContext.getUserId();
        if (userId == null) throw new KmaException(401, "USER_IDENTITY_REQUIRED");
        try {
            Long id = jdbc.queryForObject("""
                INSERT INTO knowledge_qa_feedback(user_id,space_code,session_id,rating,reason,comment,question,answer_excerpt,citations)
                VALUES (?,?,?,?,?,?,?,?,?::jsonb) RETURNING feedback_id
                """, Long.class, userId, request.getSpaceCode(), request.getSessionId(), request.getRating(),
                request.getReason(), request.getComment(), request.getQuestion(), request.getAnswerExcerpt(),
                objectMapper.writeValueAsString(request.getCitationRefs()));
            audit.recordRequired("qa_feedback", "info", "qa.feedback", "qa-feedback:" + id,
                Map.of(), Map.of("rating", request.getRating(), "spaceCode", request.getSpaceCode()), Map.of());
            return id;
        } catch (KmaException ex) { throw ex; }
        catch (Exception ex) { throw new KmaException(400, "QA_FEEDBACK_INVALID"); }
    }

    public List<Map<String, Object>> list(int limit) {
        return jdbc.queryForList("""
            SELECT feedback_id AS "feedbackId",user_id AS "userId",space_code AS "spaceCode",session_id AS "sessionId",
                   rating,reason,comment,question,answer_excerpt AS "answerExcerpt",citations,created_at AS "createdAt",converted_at AS "convertedAt"
            FROM knowledge_qa_feedback ORDER BY created_at DESC LIMIT ?
            """, Math.max(1, Math.min(limit, 200)));
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Long convertToEvaluationCase(Long feedbackId, Long datasetId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT question,answer_excerpt,citations,rating FROM knowledge_qa_feedback WHERE feedback_id=? FOR UPDATE
            """, feedbackId);
        if (rows.isEmpty()) throw new KmaException(404, "QA_FEEDBACK_NOT_FOUND");
        Map<String, Object> row = rows.getFirst();
        String question = String.valueOf(row.get("question"));
        if (question.isBlank() || "null".equals(question)) throw new KmaException(409, "QA_FEEDBACK_QUESTION_REQUIRED");
        EvaluationCaseRequest request = new EvaluationCaseRequest();
        request.setQuestion(question);
        request.setExpectedAnswer(String.valueOf(row.getOrDefault("answer_excerpt", "")));
        request.setShouldRefuse("unhelpful".equals(row.get("rating")));
        try {
            request.setExpectedExternalRefs(objectMapper.readValue(String.valueOf(row.get("citations")), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
        } catch (Exception ignored) { request.setExpectedExternalRefs(List.of()); }
        Long caseId = evaluationService.addCase(datasetId, request);
        jdbc.update("UPDATE knowledge_qa_feedback SET evaluation_case_id=?,converted_at=now() WHERE feedback_id=?", caseId, feedbackId);
        audit.recordRequired("qa_feedback", "info", "qa.feedback.convert", "qa-feedback:" + feedbackId,
            Map.of(), Map.of("evaluationCaseId", caseId, "datasetId", datasetId), Map.of());
        return caseId;
    }
}
