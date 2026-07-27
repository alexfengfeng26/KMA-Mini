package com.kma.knowledge.rag.prompt;

import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.dto.ChunkHitVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTokenBudgetTest {
    private final HeuristicTokenCounter counter = new HeuristicTokenCounter();

    @Test
    void truncatesReferencesAndHistoryByTokenBudget() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getRag().setMaxContextTokens(160);
        properties.getRag().setReservedOutputTokens(30);
        properties.getRag().setMaxHistoryTokens(8);
        PromptAssembler assembler = new PromptAssembler(properties, counter);
        ChunkHitVO hit = new ChunkHitVO();
        hit.setDocTitle("测试文档");
        hit.setContent("党建知识".repeat(200));

        String prompt = assembler.buildPromptWithHistory("如何办理？", List.of(hit),
            List.of("第一轮很长的历史消息", "第二轮不应完整保留"));

        assertThat(prompt).contains("用户问题：如何办理？");
        assertThat(prompt).doesNotContain("党建知识".repeat(200));
        assertThat(prompt).doesNotContain("第二轮不应完整保留");
    }

    @Test
    void countsChineseAndAsciiConservatively() {
        assertThat(counter.count("党建ABCDEF")).isEqualTo(4);
        assertThat(counter.truncate("党建工作要求", 3)).isEqualTo("党建工");
    }
}
