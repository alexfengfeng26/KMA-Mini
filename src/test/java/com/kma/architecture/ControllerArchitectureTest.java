package com.kma.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerArchitectureTest {

    @Test
    void controllersMustNotAccessPersistenceOrOwnTransactions() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java", "com", "kma");
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("Controller.java")).toList()) {
                String source = Files.readString(file);
                check(file, source, "com.kma.knowledge.mapper", violations);
                check(file, source, "JdbcTemplate", violations);
                check(file, source, "@Transactional", violations);
            }
        }
        assertThat(violations)
            .as("Controller 只能依赖应用服务，不能直接访问持久化或声明事务")
            .isEmpty();
    }

    private void check(Path file, String source, String forbidden, List<String> violations) {
        if (source.contains(forbidden)) {
            violations.add(file + " contains " + forbidden);
        }
    }
}
