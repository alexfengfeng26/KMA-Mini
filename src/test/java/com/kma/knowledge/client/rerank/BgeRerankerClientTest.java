package com.kma.knowledge.client.rerank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BgeRerankerClientTest {

    @Test
    void fallbackScoreUsesChineseBigramsInsteadOfTreatingSentenceAsOneTerm() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getRerank().setBaseUrl(null);
        BgeRerankerClient client = new BgeRerankerClient(
            RestClient.create(), new ObjectMapper(), properties);

        List<Double> scores = client.score(
            "中国共产党的根本宗旨是什么？",
            List.of(
                "中国共产党的根本宗旨是全心全意为人民服务。",
                "发展党员工作应当把政治标准放在首位。"
            )
        );

        assertThat(scores).hasSize(2);
        assertThat(scores.get(0)).isGreaterThanOrEqualTo(0.9);
        assertThat(scores.get(1)).isLessThan(0.5);
    }
}
