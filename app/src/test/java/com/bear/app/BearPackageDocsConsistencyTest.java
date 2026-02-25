package com.bear.app;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearPackageDocsConsistencyTest {
    @Test
    void packageAgentFileSetIsExact() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path agentRoot = repoRoot.resolve("docs/bear-package/.bear/agent");

        Set<String> actual = Files.walk(agentRoot)
                .filter(Files::isRegularFile)
                .map(path -> agentRoot.relativize(path).toString().replace('\\', '/'))
                .collect(Collectors.toSet());

        Set<String> expected = Set.of(
                "BOOTSTRAP.md",
                "CONTRACTS.md",
                "TROUBLESHOOTING.md",
                "REPORTING.md",
                "ref/IR_REFERENCE.md",
                "ref/BEAR_PRIMER.md",
                "ref/BLOCK_INDEX_QUICKREF.md"
        );

        assertEquals(expected, actual, "Agent package file set must match the canonical hard-cut layout");
    }

    @Test
    void legacyAgentFilesAreRemoved() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String[] legacyPaths = {
                "docs/bear-package/.bear/agent/BEAR_AGENT.md",
                "docs/bear-package/.bear/agent/WORKFLOW.md",
                "docs/bear-package/.bear/agent/doc/IR_QUICKREF.md",
                "docs/bear-package/.bear/agent/doc/IR_EXAMPLES.md"
        };

        for (String legacyPath : legacyPaths) {
            assertFalse(Files.exists(repoRoot.resolve(legacyPath)), "Legacy file must be removed: " + legacyPath);
        }
    }

    @Test
    void bootstrapIsBoundedAndContainsDoneGates() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path bootstrap = repoRoot.resolve("docs/bear-package/.bear/agent/BOOTSTRAP.md");
        String content = Files.readString(bootstrap, StandardCharsets.UTF_8);
        long lineCount;
        try (var lines = Files.lines(bootstrap)) {
            lineCount = lines.count();
        }

        assertTrue(lineCount <= 200, "BOOTSTRAP.md must stay within the 200-line budget");
        assertTrue(content.contains("bear check --all --project <repoRoot>"));
        assertTrue(content.contains("bear pr-check --all --project <repoRoot> --base <ref>"));
    }

    @Test
    void irReferenceUsesV1ForIrSchemas() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String content = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/ref/IR_REFERENCE.md"),
                StandardCharsets.UTF_8
        );

        Pattern deprecatedIrVersion = Pattern.compile("(?im)^\\s*version:\\s*v0\\s*\\R\\s*block:");
        assertFalse(deprecatedIrVersion.matcher(content).find(), "IR examples must use version: v1");
        assertFalse(content.contains("valid BEAR v0"), "IR examples must not describe BEAR v0 IR as canonical");
    }

    @Test
    void primerUsesV1InvariantLanguage() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String content = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/ref/BEAR_PRIMER.md"),
                StandardCharsets.UTF_8
        );
        assertFalse(content.contains("v0 supports `non_negative`"));
        assertTrue(content.contains("v1 supports `non_negative`"));
    }
}

