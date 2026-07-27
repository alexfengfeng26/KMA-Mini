package com.kma.knowledge.rag.evaluation;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Reproducible lexical baseline; can later be replaced by an LLM judge behind the same SPI. */
@Component
public class DeterministicAnswerCorrectnessJudge implements AnswerCorrectnessJudge {
    @Override
    public Judgment judge(String expectedAnswer, String actualAnswer) {
        if (expectedAnswer == null || expectedAnswer.isBlank()) {
            return new Judgment(actualAnswer == null || actualAnswer.isBlank() ? 0.0 : 1.0,
                "未提供标准答案，仅检查是否生成有效答案");
        }
        String expectedNormalized = normalize(expectedAnswer);
        String actualNormalized = normalize(actualAnswer);
        if (!expectedNormalized.isEmpty() && actualNormalized.contains(expectedNormalized)) {
            return new Judgment(1.0, "生成答案完整覆盖标准答案");
        }
        Set<String> expected = grams(expectedAnswer);
        Set<String> actual = grams(actualAnswer);
        if (expected.isEmpty()) return new Judgment(actual.isEmpty() ? 1.0 : 0.0, "标准答案归一化后为空");
        long matched = expected.stream().filter(actual::contains).count();
        double precision = actual.isEmpty() ? 0.0 : (double) matched / actual.size();
        double recall = (double) matched / expected.size();
        double f1 = precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
        double score = Math.max(f1, recall);
        return new Judgment(score, "标准答案覆盖度=" + String.format(Locale.ROOT, "%.4f", recall)
            + "，归一化字符二元组 F1=" + String.format(Locale.ROOT, "%.4f", f1));
    }

    private Set<String> grams(String value) {
        String normalized = normalize(value);
        Set<String> values = new LinkedHashSet<>();
        if (normalized.length() == 1) values.add(normalized);
        for (int i = 0; i + 1 < normalized.length(); i++) values.add(normalized.substring(i, i + 2));
        return values;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
