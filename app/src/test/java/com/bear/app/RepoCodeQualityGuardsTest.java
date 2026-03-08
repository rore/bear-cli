package com.bear.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoCodeQualityGuardsTest {
    @Test
    void coreClassLineBudgetsAreBounded() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Map<String, Integer> budgets = Map.of(
            "app/src/main/java/com/bear/app/BearCli.java", 2000,
            "app/src/main/java/com/bear/app/CheckCommandService.java", 1600,
            "kernel/src/main/java/com/bear/kernel/target/jvm/BoundaryBypassScanner.java", 1600,
            "kernel/src/main/java/com/bear/kernel/target/jvm/JvmTarget.java", 2200
        );
        for (Map.Entry<String, Integer> entry : budgets.entrySet()) {
            Path file = repoRoot.resolve(entry.getKey());
            long lines = Files.readAllLines(file, StandardCharsets.UTF_8).size();
            assertTrue(lines <= entry.getValue(), entry.getKey() + " exceeds budget: " + lines + " > " + entry.getValue());
        }
    }

    @Test
    void directImplRegexPatternsAreCentralized() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String bearCli = Files.readString(repoRoot.resolve("app/src/main/java/com/bear/app/BearCli.java"), StandardCharsets.UTF_8);
        String scanner = Files.readString(repoRoot.resolve("kernel/src/main/java/com/bear/kernel/target/jvm/BoundaryBypassScanner.java"), StandardCharsets.UTF_8);
        String patterns = Files.readString(repoRoot.resolve("kernel/src/main/java/com/bear/kernel/target/jvm/PolicyPatterns.java"), StandardCharsets.UTF_8);

        assertFalse(bearCli.contains("DIRECT_IMPL_IMPORT_PATTERN = Pattern.compile("));
        assertFalse(scanner.contains("DIRECT_IMPL_IMPORT_PATTERN = Pattern.compile("));
        assertFalse(bearCli.contains("SUPPRESSION_PATTERN = Pattern.compile("));
        assertFalse(scanner.contains("SUPPRESSION_PATTERN = Pattern.compile("));

        assertTrue(patterns.contains("DIRECT_IMPL_IMPORT_PATTERN = Pattern.compile("));
        assertTrue(patterns.contains("PORT_USED_SUPPRESSION_PATTERN = Pattern.compile("));
    }

    @Test
    void orchestrationDoesNotConstructJvmTargetDirectly() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String bearCli = Files.readString(repoRoot.resolve("app/src/main/java/com/bear/app/BearCli.java"), StandardCharsets.UTF_8);
        String checkService = Files.readString(repoRoot.resolve("app/src/main/java/com/bear/app/CheckCommandService.java"), StandardCharsets.UTF_8);
        String prCheckService = Files.readString(repoRoot.resolve("app/src/main/java/com/bear/app/PrCheckCommandService.java"), StandardCharsets.UTF_8);
        String checkAllService = Files.readString(repoRoot.resolve("app/src/main/java/com/bear/app/CheckAllCommandService.java"), StandardCharsets.UTF_8);

        assertFalse(bearCli.contains("new JvmTarget("));
        assertFalse(checkService.contains("new JvmTarget("));
        assertFalse(checkService.contains("BoundaryBypassScanner."));
        assertFalse(checkService.contains("GovernedReflectionDispatchScanner."));
        assertFalse(checkService.contains("UndeclaredReachScanner."));
        assertFalse(prCheckService.contains("new JvmTarget("));
        assertFalse(checkAllService.contains("new JvmTarget("));
    }

    @Test
    void blockedMarkerLiteralIsCentralized() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        int count = 0;
        try (Stream<Path> files = Files.walk(repoRoot.resolve("app/src/main/java"))) {
            List<Path> javaFiles = files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.contains("build/bear/check.blocked.marker")) {
                    count++;
                }
            }
        }
        assertEquals(1, count, "blocked marker path literal should exist in one main-source location");
    }
}

