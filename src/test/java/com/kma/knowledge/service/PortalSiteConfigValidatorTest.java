package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortalSiteConfigValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PortalSiteConfigValidator validator = new PortalSiteConfigValidator();

    @Test
    void acceptsAControlledMultiPageConfiguration() throws Exception {
        JsonNode config = objectMapper.readTree("""
            {
              "schemaVersion":2,
              "site":{"siteKey":"policy","scenario":"internal-policy","name":"制度中心"},
              "shell":{"navigation":[{"id":"home","label":"首页","target":"home"}]},
              "theme":{"customCss":".card { color: #123456; }"},
              "modules":{},
              "contentScope":{"allSpaces":true,"spaceCodes":[]},
              "search":{},"assistant":{},
              "pages":{"home":{"slug":"home","layout":"twelve-grid","regions":{"main":[
                {"id":"hero","type":"hero-search","enabled":true,"span":12}
              ]}}}
            }
            """);

        assertThat(validator.validate(config, "policy")).isEmpty();
    }

    @Test
    void rejectsUnknownBlocksDangerousSourcesAndCrossSiteConfig() throws Exception {
        JsonNode config = objectMapper.readTree("""
            {
              "schemaVersion":2,
              "site":{"siteKey":"other","scenario":"party","name":"站点"},
              "shell":{"navigation":[]},"theme":{"customCss":"@import 'https://evil.test/a.css';"},
              "modules":{"remote.module":true},
              "contentScope":{"allSpaces":false,"spaceCodes":[]},
              "search":{"apiUrl":"https://evil.test"},"assistant":{},
              "pages":{"home":{"slug":"home","layout":"twelve-grid","regions":{"main":[
                {"id":"bad","type":"remote-widget","enabled":true}
              ]}}}
            }
            """);

        assertThat(validator.validate(config, "policy"))
            .anyMatch(issue -> issue.contains("site.siteKey"))
            .anyMatch(issue -> issue.contains("未知区块"))
            .anyMatch(issue -> issue.contains("customCss"))
            .anyMatch(issue -> issue.contains("禁止的配置字段"))
            .anyMatch(issue -> issue.contains("spaceCode"));
    }

    @Test
    void acceptsV3ResponsiveTreeAndRejectsUnsafeTreeShapes() throws Exception {
        JsonNode valid = objectMapper.readTree("""
            {
              "schemaVersion":3,
              "site":{"siteKey":"v3-site","scenario":"party","name":"V3 门户"},
              "shell":{
                "navigation":[{"id":"home","label":"首页","target":"home"}],
                "header":{"id":"global-header","type":"container","children":[
                  {"id":"portal-nav","type":"component","component":"portal-navigation"}
                ]},
                "footer":{"id":"global-footer","type":"container","children":[
                  {"id":"account-entry","type":"component","component":"account-entry"}
                ]}
              },
              "theme":{"customCss":""},"modules":{},
              "contentScope":{"allSpaces":true,"spaceCodes":[]},
              "search":{},"assistant":{},
              "pages":{"home":{"slug":"home","kind":"home","root":{
                "id":"home-root","type":"grid","children":[
                  {"id":"hero-block","type":"component","component":"hero-search",
                   "layout":{"span":{"desktop":12,"tablet":8,"mobile":4}}}
                ]
              }}},
              "symbols":{},"packages":[]
            }
            """);

        assertThat(validator.validate(valid, "v3-site")).isEmpty();

        JsonNode invalid = valid.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.at("/pages/home/root/children/0"))
            .put("component", "remote-code");
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.at("/pages/home/root/children/0/layout/span"))
            .put("mobile", 8);
        assertThat(validator.validate(invalid, "v3-site"))
            .anyMatch(issue -> issue.contains("未知组件"))
            .anyMatch(issue -> issue.contains("1–4"));
    }
}
