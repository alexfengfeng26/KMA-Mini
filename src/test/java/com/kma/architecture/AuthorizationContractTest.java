package com.kma.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationContractTest {
    private static final Pattern PERMISSION_LITERAL = Pattern.compile("'([a-z][a-z0-9:-]+)'");
    private static final Pattern PRE_AUTHORIZE = Pattern.compile("@ss\\.has(?:Permi|Any)\\(([^)]*)\\)");
    private static final Pattern HTTP_MAPPING = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping\\b");
    private static final Pattern PERMISSION_INSERT = Pattern.compile(
        "INSERT\\s+INTO\\s+kma_permission[\\s\\S]*?(?:ON\\s+CONFLICT[\\s\\S]*?;|;)", Pattern.CASE_INSENSITIVE);
    private static final List<Pattern> FRONTEND_PERMISSIONS = List.of(
        Pattern.compile("permissions:\\s*\\[([^]]*)]"),
        Pattern.compile("v-permission\\s*=\\s*\"([^\"]*)\"")
    );

    @Test
    void controllersRoutesAndButtonsOnlyReferenceCataloguedPermissions() throws Exception {
        Set<String> catalog = new HashSet<>();
        try (var migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path migration : migrations.filter(path -> path.getFileName().toString().endsWith(".sql")).toList()) {
                var inserts = PERMISSION_INSERT.matcher(Files.readString(migration));
                while (inserts.find()) catalog.addAll(literals(inserts.group()));
            }
        }
        catalog.add("kma:admin");
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(Path.of("src/main/java/com/kma"))) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("Controller.java")).toList()) {
                var matcher = PRE_AUTHORIZE.matcher(Files.readString(file));
                while (matcher.find()) {
                    for (String code : literals(matcher.group(1))) {
                        if (!catalog.contains(code)) violations.add(file + " -> " + code);
                    }
                }
            }
        }
        try (var files = Files.walk(Path.of("kma-admin-web/src"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".vue")).toList()) {
                String source = Files.readString(file);
                for (Pattern pattern : FRONTEND_PERMISSIONS) {
                    var matcher = pattern.matcher(source);
                    while (matcher.find()) {
                        for (String code : literals(matcher.group(1))) {
                            if (!catalog.contains(code)) violations.add(file + " -> " + code);
                        }
                    }
                }
            }
        }
        assertThat(violations).as("Controller、菜单、路由和按钮权限必须存在于 Flyway 权限目录").isEmpty();
    }

    @Test
    void everyNonPublicControllerOperationDeclaresAuthorizationAndWritesDoNotShareCreateUpdateGate() throws Exception {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java/com/kma"))) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("Controller.java")).toList()) {
                String source = Files.readString(file);
                boolean publicController = false;
                var mappings = HTTP_MAPPING.matcher(source);
                while (mappings.find()) {
                    int nextMethod = source.indexOf("public ", mappings.end());
                    if (nextMethod < 0) continue;
                    String declaration = source.substring(mappings.start(), nextMethod);
                    boolean publicAuthOperation = file.getFileName().toString().equals("KmaAuthController.java")
                        && (declaration.contains("/login") || declaration.contains("/refresh"));
                    boolean classAuthorized = source.substring(0, source.indexOf("class ")).contains("@PreAuthorize");
                    if (!publicController && !publicAuthOperation && !classAuthorized
                        && !declaration.contains("@PreAuthorize")) {
                        violations.add(file + " -> mapping at " + mappings.start());
                    }
                }
                if (Pattern.compile("hasAny\\([^)]*create[^)]*update|hasAny\\([^)]*update[^)]*create")
                    .matcher(source).find()) {
                    violations.add(file + " -> create/update write semantics share hasAny");
                }
            }
        }
        assertThat(violations).as("公开认证接口外的 Controller 操作必须显式授权，创建与更新必须精确鉴权").isEmpty();
    }

    @Test
    void spaceScopedServicesKeepAclChecksAtBusinessBoundary() throws Exception {
        List<String> required = List.of(
            "KnowledgeSpaceServiceImpl.java", "KnowledgeIngestionServiceImpl.java",
            "KnowledgeFeedTaskServiceImpl.java", "KnowledgeRetrieveServiceImpl.java",
            "KnowledgeQAServiceImpl.java", "KnowledgeStreamQAServiceImpl.java",
            "KnowledgeChatSessionServiceImpl.java"
        );
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java/com/kma/knowledge"))) {
            var sources = files.filter(path -> required.contains(path.getFileName().toString())).toList();
            for (String name : required) {
                Path file = sources.stream().filter(path -> path.getFileName().toString().equals(name))
                    .findFirst().orElse(null);
                if (file == null || !Files.readString(file).contains("aclService")) violations.add(name);
            }
        }
        assertThat(violations).as("空间关联业务必须在服务层保留 ACL 校验").isEmpty();
    }

    private Set<String> literals(String text) {
        Set<String> result = new HashSet<>();
        var matcher = PERMISSION_LITERAL.matcher(text);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }
}
