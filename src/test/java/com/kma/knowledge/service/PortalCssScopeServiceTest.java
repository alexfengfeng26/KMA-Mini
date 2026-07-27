package com.kma.knowledge.service;

import com.kma.common.exception.KmaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortalCssScopeServiceTest {
    private final PortalCssScopeService service = new PortalCssScopeService();

    @Test
    void scopesEverySelectorToThePublishedSite() {
        String result = service.scope("policy-center", ".card, .hero strong { color: #075e57; }");

        assertThat(result)
            .contains("[data-kma-site=\"policy-center\"] .card")
            .contains("[data-kma-site=\"policy-center\"] .hero strong");
    }

    @Test
    void rejectsAtRulesNestedRulesAndScopeEscapes() {
        assertThatThrownBy(() -> service.scope("default", "@import 'x';"))
            .isInstanceOf(KmaException.class);
        assertThatThrownBy(() -> service.scope("default", ".x { .y { color:red; } }"))
            .isInstanceOf(KmaException.class);
        assertThatThrownBy(() -> service.scope("default", "[data-kma-site] .x { color:red; }"))
            .isInstanceOf(KmaException.class);
    }
}
