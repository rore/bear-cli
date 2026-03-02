package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllModeOptionParserTest {
    @Test
    void parseAllCheckOptionsMissingProjectReturnsNullAndUsageError() {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBytes);

        AllCheckOptions options = AllModeOptionParser.parseAllCheckOptions(new String[] { "check", "--all" }, err);

        assertNull(options);
        String stderr = CliText.normalizeLf(errBytes.toString());
        assertTrue(stderr.contains("usage: INVALID_ARGS: expected: bear check --all --project <repoRoot>"));
        assertTrue(stderr.contains("CODE=USAGE_INVALID_ARGS"));
    }

    @Test
    void parseAllCheckOptionsRejectsAbsoluteBlocksPath(@TempDir Path repoRoot) {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBytes);
        Path absoluteBlocksPath = repoRoot
            .resolveSibling("abs")
            .resolve("blocks.yaml")
            .toAbsolutePath()
            .normalize();

        AllCheckOptions options = AllModeOptionParser.parseAllCheckOptions(
            new String[] {
                "check", "--all", "--project", repoRoot.toString(), "--blocks", absoluteBlocksPath.toString()
            },
            err
        );

        assertNull(options);
        String stderr = CliText.normalizeLf(errBytes.toString());
        assertTrue(stderr.contains("usage: INVALID_ARGS: blocks path must be repo-relative"));
    }

    @Test
    void parseOnlyNamesRejectsEmptyList() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> AllModeOptionParser.parseOnlyNames(" , , "));
        assertEquals("--only requires at least one block name", ex.getMessage());
    }

    @Test
    void parseAllPrCheckOptionsParsesValidArgs(@TempDir Path repoRoot) {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBytes);

        AllPrCheckOptions options = AllModeOptionParser.parseAllPrCheckOptions(
            new String[] {
                "pr-check", "--all", "--project", repoRoot.toString(), "--base", "HEAD~1", "--only", "alpha,beta"
            },
            err
        );

        assertNotNull(options);
        assertEquals(repoRoot.toAbsolutePath().normalize(), options.repoRoot());
        assertEquals("HEAD~1", options.baseRef());
        assertEquals(Set.of("alpha", "beta"), options.onlyNames());
        assertEquals("", errBytes.toString());
    }

    @Test
    void parseAllCheckOptionsParsesStrictHygiene(@TempDir Path repoRoot) {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBytes);

        AllCheckOptions options = AllModeOptionParser.parseAllCheckOptions(
            new String[] { "check", "--all", "--project", repoRoot.toString(), "--strict-hygiene" },
            err
        );

        assertNotNull(options);
        assertTrue(options.strictHygiene());
        assertEquals("", errBytes.toString());
    }

    @Test
    void parseAllCompileOptionsParsesValidArgs(@TempDir Path repoRoot) {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBytes);

        AllCompileOptions options = AllModeOptionParser.parseAllCompileOptions(
            new String[] {
                "compile", "--all", "--project", repoRoot.toString(), "--only", "alpha,beta", "--fail-fast"
            },
            err
        );

        assertNotNull(options);
        assertEquals(repoRoot.toAbsolutePath().normalize(), options.repoRoot());
        assertEquals(Set.of("alpha", "beta"), options.onlyNames());
        assertTrue(options.failFast());
        assertEquals("", errBytes.toString());
    }
}
