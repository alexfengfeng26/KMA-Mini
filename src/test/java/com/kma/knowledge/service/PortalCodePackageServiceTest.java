package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.dto.PortalCodeFilesRequest;
import com.kma.knowledge.dto.PortalCodePackageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortalCodePackageServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PortalCodePackageService service = new PortalCodePackageService(
        jdbc, new ObjectMapper(), mock(SecurityAuditService.class));

    @Test
    void supportsCatalogCreateUpdateAndEditorVersionLifecycle() throws Exception {
        stubLifecycle("draft", "passed");
        PortalCodePackageRequest request = new PortalCodePackageRequest();
        request.setPackageKey("example");
        request.setDisplayName("Example");
        request.setDescription("Site component");

        assertThat(service.list()).hasSize(1);
        assertThat(service.get(1L)).containsEntry("packageKey", "example");
        assertThat(service.create(request)).containsEntry("packageKey", "example");
        assertThat(service.update(1L, request)).containsEntry("displayName", "Example");

        PortalCodeFilesRequest files = new PortalCodeFilesRequest();
        files.setVersion("1.0.0");
        files.setManifest(new ObjectMapper().createObjectNode());
        files.setFiles(Map.of(
            "index.html", "<main>Example</main><script src=\"./main.js\"></script>",
            "main.js", "window.addEventListener('message', () => {});",
            "style.css", ".example { color: green; }"));
        assertThat(service.saveEditorFiles(1L, files)).containsEntry("version", "1.0.0");
        assertThat(service.scan(1L, 10L)).containsEntry("scanStatus", "passed");
        assertThat(service.publish(1L, 10L)).containsEntry("packageKey", "example");
    }

    @Test
    void validatesResolvesAndCompilesSiteSandboxBindings() throws Exception {
        stubLifecycle("published", "passed");
        ReflectionTestUtils.setField(service, "sandboxOrigin", "https://sandbox.example.com/");
        ObjectMapper mapper = new ObjectMapper();
        var config = mapper.readTree("""
            {
              "schemaVersion":3,
              "packages":[{"packageId":"example","version":"1.0.0","source":"site"}],
              "pages":{"home":{"root":{"id":"root","type":"container","children":[{
                "id":"widget","type":"sandbox","packageId":"example","version":"1.0.0",
                "config":{"title":"Example"}
              }]}}}
            }
            """);

        assertThat(service.validateReferences(config)).isEmpty();
        var bindings = service.resolveBindings(config.path("pages").path("home"));
        assertThat(bindings).singleElement().satisfies(binding -> {
            assertThat(binding).containsEntry("extensionId", "example");
            assertThat(binding.get("entryUrl").toString())
                .startsWith("https://sandbox.example.com/portal-sandbox/example/1.0.0/");
        });
        service.compileUsage(3L, 4L, config);
    }

    @Test
    void rejectsUnavailableReferenceAndInUseRevoke() throws Exception {
        stubLifecycle("published", "passed");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0, 2);
        var config = new ObjectMapper().readTree("""
            {"schemaVersion":3,
             "packages":[{"packageId":"example","version":"1.0.0","source":"site"}],
             "pages":{"home":{"root":{"id":"widget","type":"sandbox",
             "packageId":"example","version":"1.0.0"}}}}
            """);

        assertThat(service.validateReferences(config)).hasSize(1);
        assertThatThrownBy(() -> service.revoke(1L, 10L))
            .isInstanceOf(KmaException.class)
            .hasMessageContaining("PORTAL_CODE_VERSION_IN_USE");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void servesOnlySafePublishedFiles() {
        doReturn(List.of(new PortalCodePackageService.StaticResource(
            "text/html;charset=UTF-8", "<main>ok</main>".getBytes(StandardCharsets.UTF_8))))
            .when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        var resource = service.publishedFile("example", "1.0.0", "index.html");
        assertThat(resource.mimeType()).startsWith("text/html");
        assertThat(new String(resource.content(), StandardCharsets.UTF_8)).contains("ok");
        assertThatThrownBy(() -> service.publishedFile("example", "1.0.0", "../secret"))
            .isInstanceOf(KmaException.class)
            .hasMessageContaining("PORTAL_CODE_FILE_PATH_INVALID");
    }

    @Test
    void rejectsEditorPathTraversalBeforePersistingFiles() {
        stubPackage();
        PortalCodeFilesRequest request = new PortalCodeFilesRequest();
        request.setVersion("1.0.0");
        request.setFiles(Map.of("index.html", "<main>safe</main>", "../main.js", "alert(1)"));

        assertThatThrownBy(() -> service.saveEditorFiles(1L, request))
            .isInstanceOf(KmaException.class)
            .hasMessageContaining("PORTAL_CODE_FILE_PATH_INVALID");
    }

    @Test
    void validatesEditorSourceWithoutPersistingIt() {
        var result = service.validateEditorSource(Map.of(
            "index.html", "<main>safe</main>",
            "main.js", "fetch('/not-allowed')"), new ObjectMapper().createObjectNode());

        assertThat(result).containsEntry("valid", false);
        assertThat((List<?>) result.get("issues")).anyMatch(issue -> issue.toString().contains("fetch"));
    }

    @Test
    void rejectsZipPathTraversal() throws Exception {
        stubPackage();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("../index.html"));
            zip.write("<main>unsafe</main>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile(
            "file", "unsafe.zip", "application/zip", bytes.toByteArray());

        assertThatThrownBy(() ->
            service.importZip(1L, "1.0.0", new ObjectMapper().createObjectNode(), file))
            .isInstanceOf(KmaException.class)
            .hasMessageContaining("PORTAL_CODE_FILE_PATH_INVALID");
    }

    @SuppressWarnings("unchecked")
    private void stubPackage() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
            .thenReturn(List.of(Map.of("packageId", 1L, "packageKey", "example")));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubLifecycle(String versionStatus, String scanStatus) {
        Map<String, Object> packageRow = Map.of(
            "packageId", 1L, "packageKey", "example", "displayName", "Example", "status", "active");
        Map<String, Object> versionRow = Map.ofEntries(
            Map.entry("versionId", 10L),
            Map.entry("versionNo", 1),
            Map.entry("version", "1.0.0"),
            Map.entry("status", versionStatus),
            Map.entry("sourceMode", "editor"),
            Map.entry("entryPath", "index.html"),
            Map.entry("manifest", Map.of()),
            Map.entry("checksum", "abc123"),
            Map.entry("scanStatus", scanStatus),
            Map.entry("scanResult", Map.of()),
            Map.entry("fileCount", 3),
            Map.entry("compressedSize", 0L),
            Map.entry("expandedSize", 100L));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM knowledge_portal_code_file"))
                return List.of(
                    Map.of("filePath", "index.html", "mimeType", "text/html",
                        "content", "<main>safe</main>".getBytes(StandardCharsets.UTF_8)),
                    Map.of("filePath", "main.js", "mimeType", "text/javascript",
                        "content", "console.log('safe')".getBytes(StandardCharsets.UTF_8)));
            if (sql.contains("SELECT p.display_name"))
                return List.of(Map.of("displayName", "Example", "manifest", Map.of(), "checksum", "abc123"));
            if (sql.contains("SELECT p.package_id AS"))
                return List.of(Map.of("packageId", 1L, "versionId", 10L));
            if (sql.contains("FROM knowledge_portal_code_version")) return List.of(versionRow);
            return List.of(packageRow);
        });
        when(jdbc.queryForList(anyString())).thenReturn(List.of(packageRow));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L, 10L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    }
}
