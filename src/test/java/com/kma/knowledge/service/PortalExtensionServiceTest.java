package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.dto.PortalExtensionReleaseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PortalExtensionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsInvalidManifestBeforeItCanReachTheCatalog() throws Exception {
        PortalExtensionService service = new PortalExtensionService(mock(JdbcTemplate.class), mapper,
            mock(SecurityAuditService.class));
        ReflectionTestUtils.setField(service, "signingKey", "test-signing-key");
        PortalExtensionReleaseRequest request = new PortalExtensionReleaseRequest();
        request.setExtensionId("bad-extension");
        request.setVersion("1.0.0");
        request.setDisplayName("Bad");
        request.setEntryUrl("/portal-extensions/bad-extension/1.0.0/index.html");
        request.setIntegrityHash("sha256:test");
        request.setSignature("not-a-signature");
        request.setManifest(mapper.readTree("{\"id\":\"different\",\"version\":\"1.0.0\"}"));

        assertThatThrownBy(() -> service.release(request))
            .isInstanceOf(KmaException.class)
            .hasMessageContaining("PORTAL_EXTENSION_MANIFEST_INVALID");
    }
}
