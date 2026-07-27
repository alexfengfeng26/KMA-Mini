package com.kma.knowledge.client.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 本地 BGE-M3 Embedding 客户端单元测试
 *
 * @author party
 * @date 2026/07/02
 */
class LocalBgeM3EmbeddingClientTest {

    @Test
    void shouldParseEmbeddingResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setEnabled(true);
        properties.getEmbedding().setApiKey("test-key");
        properties.getEmbedding().setDimension(3);
        properties.getEmbedding().getLocal().setBaseUrl("http://localhost:9997/v1");
        properties.getEmbedding().getLocal().setModel("bge-m3");

        String responseJson = "{\"object\":\"list\",\"data\":["
                + "{\"object\":\"embedding\",\"index\":0,\"embedding\":[0.1,0.2,0.3]}"
                + "]}";

        server.expect(MockRestRequestMatchers.requestTo("http://localhost:9997/v1/embeddings"))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer test-key"))
                .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(MockRestResponseCreators.withSuccess(responseJson, MediaType.APPLICATION_JSON));

        LocalBgeM3EmbeddingClient client = new LocalBgeM3EmbeddingClient(restClient, new ObjectMapper(), properties);

        List<float[]> embeddings = client.embed(List.of("党建"));

        assertEquals(1, embeddings.size());
        assertEquals(3, embeddings.get(0).length);
        assertEquals(0.1f, embeddings.get(0)[0], 0.001f);
        server.verify();
    }

    @Test
    void shouldReturnProviderAndDimension() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getEmbedding().setDimension(1024);
        LocalBgeM3EmbeddingClient client = new LocalBgeM3EmbeddingClient(null, new ObjectMapper(), properties);

        assertEquals("local-bge-m3", client.provider());
        assertEquals(1024, client.dimension());
    }
}



