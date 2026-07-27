package com.kma.knowledge.rag.retrieve;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LexicalQueryAnalyzerTest {
    private final LexicalQueryAnalyzer analyzer = new LexicalQueryAnalyzer();

    @Test
    void createsChineseBigramsAndNormalizedLatinTokens() {
        assertThat(analyzer.toTsQuery("党建 AI-知识库"))
            .isEqualTo("党建 | ai | 知识 | 识库");
        assertThat(analyzer.analyzeDocument("KMA知识库"))
            .isEqualTo("kma 知识 识库");
    }

    @Test
    void removesDuplicateQueryTokensAndHandlesBlankValues() {
        assertThat(analyzer.toTsQuery("AI ai AI")).isEqualTo("ai");
        assertThat(analyzer.toTsQuery(" ")).isEmpty();
        assertThat(analyzer.analyzeDocument(null)).isEmpty();
    }
}
