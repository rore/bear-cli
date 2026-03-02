package com.bear.app;

import org.junit.jupiter.api.Test;

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
    void legacyAgentFilesAreRemoved() {
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
    void requiredSectionAnchorsExist() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String bootstrap = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/BOOTSTRAP.md"));
        String troubleshooting = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/TROUBLESHOOTING.md"));
        String reporting = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/REPORTING.md"));

        assertMatchesHeading(bootstrap, "(?m)^##\\s+AGENT_PACKAGE_PARITY_PRECONDITION\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+GREENFIELD_HARD_STOP\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+INDEX_REQUIRED_PREFLIGHT\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+GREENFIELD_PR_CHECK_POLICY\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+DECOMPOSITION_DEFAULT\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+DECOMPOSITION_SPLIT_TRIGGERS\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+POLICY_SCOPE_MISMATCH\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+GREENFIELD_ARTIFACT_SOURCE_RULE\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+IO_LOCK\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+PR_CHECK_EXIT_ENVELOPE_ANOMALY\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+GREENFIELD_BASELINE_PR\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+POLICY_SCOPE_MISMATCH\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+PROCESS_VIOLATION\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+REACH_REMEDIATION_NON_SOLUTIONS\\s*$");
        assertMatchesHeading(reporting, "(?m)^##\\s+DEVELOPER_SUMMARY\\s*$");
        assertMatchesHeading(reporting, "(?m)^##\\s+GREENFIELD_BASELINE_WAITING_SEMANTICS\\s*$");
        assertMatchesHeading(reporting, "(?m)^##\\s+Blocker\\s+And\\s+Anomaly\\s+Reporting\\s*$");

        assertContains(
            reporting,
            "Decomposition rubric: state_domain_<same|split>; effects_<read_only|write>; idempotency_<same|split|n/a>; lifecycle_<same|split>; authority_<same|split>"
        );
        assertContains(
            reporting,
            "`Decomposition mode: grouped` => `Groups: [<group_name>:{<block1>,<block2>}; <group_name>:{<block3>}]`"
        );
        assertContains(reporting, "`Decomposition mode: single|multi` => `Groups: n/a`");
        assertContains(
            reporting,
            "first two entries are exactly `bear.blocks.yaml`, `spec/*.bear.yaml`"
        );

        assertContains(bootstrap, "1. `state_domain_split`");
        assertContains(bootstrap, "2. `effects_split`");
        assertContains(bootstrap, "3. `idempotency_split`");
        assertContains(bootstrap, "4. `lifecycle_split`");
        assertContains(bootstrap, "5. `authority_split`");
        assertContains(bootstrap, "6. `operation_multiplexer_anti_pattern`");
        assertContains(bootstrap, "Non-whitelisted trigger names are invalid in reports.");
    }

    @Test
    void bootstrapStaysWithinLineBudget() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String bootstrap = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/BOOTSTRAP.md"));
        String normalized = bootstrap.replace("\r\n", "\n");
        int lineCount = normalized.split("\n", -1).length;
        assertTrue(lineCount <= 200, "BOOTSTRAP.md must stay within 200 lines; found " + lineCount);
    }

    private static void assertMatchesHeading(String content, String headingRegex) {
        assertTrue(Pattern.compile(headingRegex).matcher(content).find(), "Expected heading anchor missing: " + headingRegex);
    }

    private static void assertContains(String content, String token) {
        assertTrue(content.contains(token), "Expected exact token missing: " + token);
    }
}
