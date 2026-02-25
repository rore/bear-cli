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
    void hardeningClausesArePresentAndDocPathDriftIsBlocked() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String bootstrap = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/BOOTSTRAP.md"),
                StandardCharsets.UTF_8
        );
        String contracts = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/CONTRACTS.md"),
                StandardCharsets.UTF_8
        );
        String reporting = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/REPORTING.md"),
                StandardCharsets.UTF_8
        );
        String troubleshooting = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/TROUBLESHOOTING.md"),
                StandardCharsets.UTF_8
        );
        String irReference = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/ref/IR_REFERENCE.md"),
                StandardCharsets.UTF_8
        );

        assertContainsTokens(bootstrap,
                "bear.blocks.yaml",
                "IR files MUST be created under `spec/`",
                "Decomposition signals are defined in `CONTRACTS.md`",
                "action/command enum multiplexer",
                "Do not use `--base HEAD` unless explicitly instructed.",
                "validate that exact created path",
                "TODO: replace this entire method body|Do not append logic below this placeholder return",
                "Do not self-edit build/policy/runtime harness files",
                "build.gradle",
                "gradlew",
                "containment-required.json",
                "Do not interpret containment metadata preemptively.",
                "containment/classpath signatures",
                "bear compile --all --project <repoRoot>",
                "CONTAINMENT_METADATA_MISMATCH",
                "moving impl seams",
                "duplicate shim copies in `_shared`",
                "_shared` must not depend on app packages",
                "app packages must not implement generated ports",
                "Never leave generated placeholder returns before real logic",
                "For expected `BOUNDARY_EXPANSION_DETECTED`, do not attempt to force green",
                "mark run `BLOCKED` with required governance next action.",
                "2 failed retries"
        );

        assertContainsTokens(contracts,
                "## Decomposition Signals (Normative)",
                "## Contract Modeling Anti-Patterns (Normative)",
                "## Conflict Definition (Normative)",
                "SPEC_POLICY_CONFLICT",
                "Positive examples",
                "Non-examples",
                "Do not edit `build.gradle`",
                "No action/command multiplexer rule does not imply multi-block by itself.",
                "IR v1 supports one `logic` block per IR file.",
                "_shared` MUST NOT import or depend on app packages",
                "App packages MUST NOT implement generated `com.bear.generated.*Port` interfaces"
        );
        assertFalse(contracts.contains("Completion is valid only with both gates evidenced green"));

        assertContainsTokens(reporting,
                "Copy this count from the `pr-check` output of that exact completion run; do not infer.",
                "Run outcome: COMPLETE|BLOCKED",
                "Required next action: <...>",
                "PR base used: <ref>",
                "PR base rationale:",
                "PR classification interpretation:",
                "Constraint conflicts encountered: none|<list>",
                "Escalation decision: none|<reason>",
                "Containment sanity check: pass|fail|n/a - <evidence>",
                "Infra edits: none|<list>",
                "Unblock used: no|yes - <reason>",
                "Gate policy acknowledged: yes|no",
                "`pr-check` exit is non-zero -> `Run outcome` MUST be `BLOCKED`."
        );
        assertFalse(reporting.contains("--base HEAD"));

        assertContainsTokens(troubleshooting,
                "Schema/path mismatch or missing routed docs",
                "verify destination `.bear/agent/**` tree exactly matches source package tree.",
                "## BOUNDARY_EXPANSION_DETECTED",
                "`--base HEAD` can misclassify or hide intended delta unless explicitly instructed.",
                "## SPEC_POLICY_CONFLICT",
                "## CONTAINMENT_METADATA_MISMATCH",
                "containment/classpath signatures",
                "run exactly one deterministic repair: `bear compile --all --project <repoRoot>`",
                "Rerun the same `bear check` command.",
                "## Forbidden Actions",
                "Do not edit `build.gradle`",
                "Do not move impl seams",
                "Do not override containment excludes",
                "Do not use `bear unblock` for intentional boundary expansion.",
                "report `BLOCKED` with required governance next action.",
                "Do not use `bear unblock` to force expected boundary expansion green.",
                "Retry budget is max 2 failed retries."
        );

        assertContainsTokens(irReference,
                "generated logic signatures exclude the idempotency store port",
                "wrapper enforcement binds idempotency via IR-declared `idempotency.store`",
                "Strict policy format contract: see `.bear/agent/CONTRACTS.md`."
        );

        Path agentRoot = repoRoot.resolve("docs/bear-package/.bear/agent");
        try (var files = Files.walk(agentRoot)) {
            for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(content.contains(".bear/agent/doc/"),
                        "Packaged agent docs must not reference retired doc/ paths: " + file);
                assertFalse(content.contains("com.bear.account.demo"),
                        "Packaged agent docs must remain BEAR-generic (no demo package references): " + file);
                assertFalse(content.contains("bear-account-demo"),
                        "Packaged agent docs must remain BEAR-generic (no demo repo references): " + file);
            }
        }
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

    private static void assertContainsTokens(String content, String... tokens) {
        for (String token : tokens) {
            assertTrue(content.contains(token), "Expected token missing: " + token);
        }
    }
}

