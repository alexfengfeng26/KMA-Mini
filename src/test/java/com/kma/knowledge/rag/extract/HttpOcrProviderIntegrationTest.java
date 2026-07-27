package com.kma.knowledge.rag.extract;

import com.kma.knowledge.config.KnowledgeProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpOcrProviderIntegrationTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void callsOcrProtocolWithMimeContentAndAuthorization() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocr", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"text\":\"扫描件识别成功\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getOcr().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getOcr().setEndpoint("/ocr");
        properties.getOcr().setApiKey("test-key");

        String text = new HttpOcrProvider(properties).extract("image".getBytes(StandardCharsets.UTF_8), "image/png");

        assertThat(text).isEqualTo("扫描件识别成功");
        assertThat(requestBody.get()).contains("contentBase64", "mimeType", "image/png");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
    }
}
