package com.kma.knowledge.rag.evaluation;

public interface AnswerCorrectnessJudge {
    Judgment judge(String expectedAnswer, String actualAnswer);
    record Judgment(double score, String reason) {}
}
