package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyAllowlistParserTest {
    @Test
    void missingAllowlistFileReturnsEmptySet(@TempDir Path tempDir) throws Exception {
        Set<String> parsed = PolicyAllowlistParser.parseExactPathAllowlist(
            tempDir,
            PolicyAllowlistParser.REFLECTION_ALLOWLIST_PATH
        );
        assertEquals(Set.of(), parsed);
    }

    @Test
    void parserRejectsUnsortedEntries(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve(PolicyAllowlistParser.REFLECTION_ALLOWLIST_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "z/path.java\na/path.java\n", StandardCharsets.UTF_8);

        PolicyValidationException ex = assertThrows(
            PolicyValidationException.class,
            () -> PolicyAllowlistParser.parseExactPathAllowlist(tempDir, PolicyAllowlistParser.REFLECTION_ALLOWLIST_PATH)
        );
        assertEquals(PolicyAllowlistParser.REFLECTION_ALLOWLIST_PATH, ex.policyPath());
        assertTrue(ex.getMessage().contains("sorted"));
    }

    @Test
    void parserRejectsDuplicateEntries(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve(PolicyAllowlistParser.HYGIENE_ALLOWLIST_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "a/path.java\na/path.java\n", StandardCharsets.UTF_8);

        PolicyValidationException ex = assertThrows(
            PolicyValidationException.class,
            () -> PolicyAllowlistParser.parseExactPathAllowlist(tempDir, PolicyAllowlistParser.HYGIENE_ALLOWLIST_PATH)
        );
        assertEquals(PolicyAllowlistParser.HYGIENE_ALLOWLIST_PATH, ex.policyPath());
        assertTrue(ex.getMessage().contains("duplicate"));
    }

    @Test
    void parserRejectsTrailingSlashAndWildcard(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve(PolicyAllowlistParser.HYGIENE_ALLOWLIST_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "a/path/\n", StandardCharsets.UTF_8);

        PolicyValidationException slashEx = assertThrows(
            PolicyValidationException.class,
            () -> PolicyAllowlistParser.parseExactPathAllowlist(tempDir, PolicyAllowlistParser.HYGIENE_ALLOWLIST_PATH)
        );
        assertTrue(slashEx.getMessage().contains("must not end with '/'"));

        Files.writeString(file, "a/*/path.java\n", StandardCharsets.UTF_8);
        PolicyValidationException wildcardEx = assertThrows(
            PolicyValidationException.class,
            () -> PolicyAllowlistParser.parseExactPathAllowlist(tempDir, PolicyAllowlistParser.HYGIENE_ALLOWLIST_PATH)
        );
        assertTrue(wildcardEx.getMessage().contains("wildcard"));
    }

    @Test
    void fqcnAllowlistMissingFileReturnsEmptySet(@TempDir Path tempDir) throws Exception {
        Set<String> parsed = PolicyAllowlistParser.parseFqcnAllowlist(
            tempDir,
            PolicyAllowlistParser.PURE_SHARED_IMMUTABLE_TYPES_ALLOWLIST_PATH
        );
        assertEquals(Set.of(), parsed);
    }

    @Test
    void fqcnAllowlistRejectsSimpleName(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve(PolicyAllowlistParser.PURE_SHARED_IMMUTABLE_TYPES_ALLOWLIST_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "String\n", StandardCharsets.UTF_8);

        PolicyValidationException ex = assertThrows(
            PolicyValidationException.class,
            () -> PolicyAllowlistParser.parseFqcnAllowlist(
                tempDir,
                PolicyAllowlistParser.PURE_SHARED_IMMUTABLE_TYPES_ALLOWLIST_PATH
            )
        );
        assertTrue(ex.getMessage().contains("fully-qualified class name"));
    }

    @Test
    void fqcnAllowlistParsesSortedUniqueWithComments(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve(PolicyAllowlistParser.PURE_SHARED_IMMUTABLE_TYPES_ALLOWLIST_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(
            file,
            "# immutable types\n"
                + "\n"
                + "java.time.Clock\n"
                + "java.time.Instant\n",
            StandardCharsets.UTF_8
        );

        Set<String> parsed = PolicyAllowlistParser.parseFqcnAllowlist(
            tempDir,
            PolicyAllowlistParser.PURE_SHARED_IMMUTABLE_TYPES_ALLOWLIST_PATH
        );
        assertEquals(Set.of("java.time.Clock", "java.time.Instant"), parsed);
    }
}
