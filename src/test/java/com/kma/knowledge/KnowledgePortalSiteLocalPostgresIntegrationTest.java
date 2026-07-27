package com.kma.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaPrincipal;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.dto.PartyContentView;
import com.kma.knowledge.dto.PortalConfigDraftRequest;
import com.kma.knowledge.dto.PortalContentQuery;
import com.kma.knowledge.dto.PortalSiteCreateRequest;
import com.kma.knowledge.dto.PortalSiteUpdateRequest;
import com.kma.knowledge.dto.PortalVersionActionRequest;
import com.kma.knowledge.dto.QARequest;
import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.service.PartyKnowledgeService;
import com.kma.knowledge.service.PortalCssScopeService;
import com.kma.knowledge.service.PortalCodePackageService;
import com.kma.knowledge.service.PortalExtensionService;
import com.kma.knowledge.service.PortalSiteConfigValidator;
import com.kma.knowledge.service.PortalSiteService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("local-pg")
@EnabledIfSystemProperty(named = "kma.local.pg.it", matches = "true")
class KnowledgePortalSiteLocalPostgresIntegrationTest {
    private JdbcTemplate jdbc;
    private PortalSiteService service;

    @BeforeEach
    void setUp() {
        String url = required("KMA_IT_DB_URL");
        assertThat(databaseName(url)).endsWith("_test");
        String username = required("KMA_IT_DB_USERNAME");
        String password = required("KMA_IT_DB_PASSWORD");
        Flyway flyway = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();

        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        Long userId = jdbc.queryForObject("""
            INSERT INTO kma_user(username,display_name,identity_provider)
            VALUES ('cms-admin','CMS Admin','local')
            RETURNING user_id
            """, Long.class);
        KmaPrincipal principal = new KmaPrincipal();
        principal.setUserId(userId);
        principal.setUsername("cms-admin");
        principal.setPermissions(Set.of("kma:admin"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));

        PartyKnowledgeService partyKnowledge = mock(PartyKnowledgeService.class);
        when(partyKnowledge.home(any())).thenReturn(Map.of(
            "config", Map.of(), "categories", List.of(), "recent", List.of(),
            "topics", List.of(), "history", List.of(), "favorites", List.of()));
        when(partyKnowledge.portalPage(any(PortalContentQuery.class), any()))
            .thenReturn(Map.of("list", List.of(), "total", 0));
        when(partyKnowledge.getPortalContent(any(), any(), any())).thenReturn(new PartyContentView());
        when(partyKnowledge.getPortalDocument(any())).thenReturn(new KnowledgeDoc());

        SecurityAuditService audit = mock(SecurityAuditService.class);
        service = new PortalSiteService(jdbc, new ObjectMapper(), new PortalSiteConfigValidator(),
            new PortalCssScopeService(), new PortalExtensionService(jdbc, new ObjectMapper(), audit),
            mock(PortalCodePackageService.class), partyKnowledge, audit);
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managesDraftReviewPublishRollbackAndScopedPortalRuntime() {
        assertThat(service.listSites()).extracting(row -> row.get("siteKey")).contains("default");
        assertThat(service.getSite("default").get("versions")).isInstanceOf(List.class);
        assertThat(service.bootstrap("default", "home")).containsEntry("publishedVersion", 1);
        assertThat(service.page("default", "home").path("slug").asText()).isEqualTo("home");

        Map<String, Object> created = service.createSite(site("policy-center", "制度与 SOP", "internal-policy"));
        assertThat(created).containsEntry("siteKey", "policy-center");
        Map<String, Object> draftVersion = service.versions("policy-center").getFirst();
        Long versionId = ((Number) draftVersion.get("versionId")).longValue();
        Map<String, Object> detail = service.version("policy-center", versionId);
        assertThat(detail.get("config")).isInstanceOf(JsonNode.class);

        ObjectNode config = ((JsonNode) detail.get("config")).deepCopy();
        config.withObject("/theme").put("customCss", ".cms-card { color: #123456; }");
        ObjectNode scope = config.withObject("/contentScope");
        scope.put("allSpaces", false);
        scope.set("spaceCodes", array("party-main"));
        scope.set("topicCodes", array("three-meetings"));
        scope.set("contentTypes", array("policy"));
        scope.set("validityStatuses", array("effective"));

        PortalConfigDraftRequest update = new PortalConfigDraftRequest();
        update.setConfig(config);
        update.setExpectedLockVersion(((Number) detail.get("lockVersion")).intValue());
        update.setChangeNote("限定制度站点范围");
        Map<String, Object> updated = service.updateDraft("policy-center", versionId, update);
        assertThat(((JsonNode) updated.get("config")).path("contentScope").path("allSpaces").asBoolean())
            .isFalse();
        assertThat(service.validate("policy-center", action(versionId)).get("valid")).isEqualTo(true);

        service.submit("policy-center", action(versionId));
        service.approve("policy-center", action(versionId));
        service.publish("policy-center", action(versionId));
        Map<String, Object> bootstrap = service.bootstrap("policy-center", "home");
        assertThat(((Map<?, ?>) bootstrap.get("site")).get("name")).isEqualTo("制度与 SOP");
        assertThat(bootstrap.get("theme").toString()).contains("data-kma-site");

        PortalContentQuery query = new PortalContentQuery();
        assertThat(service.contents("policy-center", query)).containsEntry("total", 0);
        assertThat(service.content("policy-center", 1L, "page-1")).isNotNull();
        assertThat(service.document("policy-center", 1L)).isNotNull();

        QARequest qa = new QARequest();
        qa.setSpaceCode("party-main");
        service.secureQa("policy-center", qa);
        assertThat(qa.getPortalOnly()).isTrue();
        assertThat(qa.getContentTypes()).containsExactly("policy");
        assertThat(qa.getTopicCodes()).containsExactly("three-meetings");
        assertThat(qa.getValidityStatuses()).containsExactly("effective");
        QARequest outside = new QARequest();
        outside.setSpaceCode("other");
        assertThatThrownBy(() -> service.secureQa("policy-center", outside))
            .isInstanceOf(KmaException.class).hasMessageContaining("PORTAL_SPACE_OUT_OF_SCOPE");

        Map<String, Object> rollback = service.rollback("policy-center", versionId);
        assertThat(rollback).containsEntry("status", "published");
        Map<String, Object> nextDraft = service.createDraft("policy-center", null);
        Long nextDraftId = ((Number) nextDraft.get("versionId")).longValue();
        service.submit("policy-center", action(nextDraftId));
        service.reject("policy-center", action(nextDraftId));
        assertThat(service.version("policy-center", nextDraftId)).containsEntry("status", "draft");

        PortalSiteUpdateRequest siteUpdate = new PortalSiteUpdateRequest();
        siteUpdate.setName("制度中心（更新）");
        siteUpdate.setStatus("disabled");
        siteUpdate.setDefaultSite(false);
        assertThat(service.updateSite("policy-center", siteUpdate)).containsEntry("status", "disabled");
        service.deleteSite("policy-center");
        assertThatThrownBy(() -> service.getSite("policy-center"))
            .isInstanceOf(KmaException.class).hasMessageContaining("PORTAL_SITE_NOT_FOUND");

        service.createSite(site("product-help", "产品帮助", "product-help"));
        service.createSite(site("party-study", "党建学习", "party"));
        service.deleteSite("product-help");
        service.deleteSite("party-study");
    }

    private PortalSiteCreateRequest site(String key, String name, String scenario) {
        PortalSiteCreateRequest request = new PortalSiteCreateRequest();
        request.setSiteKey(key);
        request.setName(name);
        request.setScenario(scenario);
        request.setDefaultSite(false);
        return request;
    }

    private PortalVersionActionRequest action(Long versionId) {
        PortalVersionActionRequest request = new PortalVersionActionRequest();
        request.setVersionId(versionId);
        request.setNote("integration-test");
        return request;
    }

    private ArrayNode array(String value) {
        return new ObjectMapper().createArrayNode().add(value);
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量 " + name);
        return value;
    }

    private String databaseName(String jdbcUrl) {
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        return uri.getPath().substring(1);
    }
}
