package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.security.ContentSecurityService;
import com.kma.knowledge.client.llm.LlmChatResponse;
import com.kma.knowledge.client.llm.PortalDesignLlmClient;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.dto.PortalDesignProposalRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortalDesignServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reportsMissingKeyWithoutExposingConfiguration() {
        KnowledgeProperties properties = new KnowledgeProperties();
        PortalDesignService service = service(properties, null);

        assertThat(service.capability().available()).isFalse();
        assertThat(service.capability().model()).isEqualTo("deepseek-v4-flash");
        assertThat(service.capability().reason()).isEqualTo("DEEPSEEK_API_KEY_MISSING");
    }

    @Test
    void validatesAndReturnsAControlledPageProposalFromPublishedVersion() throws Exception {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getPortalDesign().setApiKey("test-only");
        PortalDesignLlmClient client = mock(PortalDesignLlmClient.class);
        LlmChatResponse llm = new LlmChatResponse();
        llm.setModel("deepseek-v4-flash");
        llm.setContent("""
            {"summary":"突出首页搜索","warnings":[],"target":{
              "slug":"home","kind":"home","title":"首页","root":{
                "id":"generated-root","type":"grid","children":[
                  {"id":"hero-new","type":"component","component":"hero-search"}
                ]
              }
            }}
            """);
        when(client.generate(any(), anyString())).thenReturn(llm);
        PortalDesignService service = service(properties, client);
        PortalDesignProposalRequest request = new PortalDesignProposalRequest();
        request.setVersionId(8L);
        request.setExpectedLockVersion(0);
        request.setScope("page");
        request.setPageSlug("home");
        request.setInstruction("突出搜索");
        request.setConfig(validConfig());

        var response = service.propose("default", request);

        assertThat(response.model()).isEqualTo("deepseek-v4-flash");
        assertThat(response.summary()).isEqualTo("突出首页搜索");
        assertThat(response.target().path("root").path("id").asText()).isEqualTo("home-root");
    }

    private PortalDesignService service(
        KnowledgeProperties properties,
        PortalDesignLlmClient configuredClient
    ) {
        PortalDesignLlmClient client = configuredClient == null
            ? mock(PortalDesignLlmClient.class) : configuredClient;
        PortalSiteService portalSites = mock(PortalSiteService.class);
        when(portalSites.version("default", 8L))
            .thenReturn(Map.of("status", "published", "lockVersion", 0));
        ContentSecurityService security = mock(ContentSecurityService.class);
        when(security.inspectUserInput(anyString(), anyString()))
            .thenAnswer(invocation -> new ContentSecurityService.Inspection(
                invocation.getArgument(0), List.of(), false));
        when(security.processModelOutput(anyString(), anyString()))
            .thenAnswer(invocation -> new ContentSecurityService.Inspection(
                invocation.getArgument(0), List.of(), false));
        return new PortalDesignService(
            properties,
            objectMapper,
            client,
            portalSites,
            new PortalSiteConfigValidator(),
            security);
    }

    private JsonNode validConfig() throws Exception {
        return objectMapper.readTree("""
            {
              "schemaVersion":3,
              "revision":"test",
              "site":{"siteKey":"default","scenario":"party","name":"KMA Mini","locale":"zh-CN"},
              "shell":{
                "navigation":[{"id":"home","label":"首页","target":"home"}],
                "header":{"id":"global-header","type":"container","children":[
                  {"id":"portal-nav","type":"component","component":"portal-navigation","locked":true}
                ]},
                "footer":{"id":"global-footer","type":"container","children":[
                  {"id":"account-entry","type":"component","component":"account-entry","locked":true}
                ]}
              },
              "theme":{"preset":"emerald","mode":"light","density":"compact","tokens":{}},
              "modules":{},
              "contentScope":{"allSpaces":true,"spaceCodes":[],"topicCodes":[],"contentTypes":[],"validityStatuses":[]},
              "search":{},"assistant":{},
              "pages":{"home":{"slug":"home","kind":"home","title":"首页","root":{
                "id":"home-root","type":"grid","children":[
                  {"id":"hero-old","type":"component","component":"hero-search"}
                ]
              }}},
              "symbols":{},"packages":[]
            }
            """);
    }
}
