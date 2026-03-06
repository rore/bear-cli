package com.bear.app;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
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
            "ref/BLOCK_INDEX_QUICKREF.md",
            "ref/AGENT_JSON_QUICKREF.md",
            "ref/WINDOWS_QUICKREF.md"
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
        String contracts = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/CONTRACTS.md"));

        assertMatchesHeading(bootstrap, "(?m)^##\\s+Command\\s+Surface\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+Machine\\s+Gate\\s+Loop\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+Routing\\s+Map\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+Hard-Stop\\s+Routing\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+Implementation\\s+Preconditions\\s*$");

        assertMatchesHeading(reporting, "(?m)^##\\s+Agent\\s+Loop\\s+Contract\\s*$");
        assertContains(reporting, "Automation MUST parse only stdout JSON in `--agent` mode");
        assertContains(reporting, "If `status=fail` and `nextAction.commands` exists, execute only those BEAR commands in listed order (no ad-hoc retries).");
        assertContains(reporting, "If `status=fail` and `nextAction` is `null`, route to `.bear/agent/TROUBLESHOOTING.md`");
        assertContains(reporting, "Field-level quickref: `.bear/agent/ref/AGENT_JSON_QUICKREF.md`.");
        assertMatchesHeading(reporting, "(?m)^##\\s+Required\\s+Fields\\s+\\(Minimal\\s+Core\\)\\s*$");
        assertContains(reporting, "`Status: tests=<PASS|FAIL>; check=<code>; pr-check=<code> base=<ref>; outcome=<token>`");
        assertContains(reporting, "`Run outcome: COMPLETE|BLOCKED|WAITING_FOR_BASELINE_REVIEW`");
        assertContains(reporting, "`Gate results:`");
        assertContains(reporting, "`Required next action: <...>`");
        assertContains(reporting, "`Gate blocker: <...>`");
        assertContains(reporting, "`Baseline review scope: ...` including `bear.blocks.yaml` and `spec/*.bear.yaml` (pinned v1 contract)");
        assertContains(reporting, "`Decomposition contract consulted: yes (before IR authoring)` is required when `IR delta` indicates `spec/*.bear.yaml` authoring/modification.");
        assertContains(reporting, "Additional fields are allowed but ignored by core lint.");
        assertContains(reporting, "Allowed `Run outcome` values are exactly: `COMPLETE | BLOCKED | WAITING_FOR_BASELINE_REVIEW`.");
        assertContains(reporting, "`Status:` and `Run outcome:` are both mandatory and MUST agree on outcome token.");
        assertContains(reporting, "When `Run outcome: COMPLETE`, `Gate results` must include canonical repo-level done gates");
        assertContains(reporting, "(pinned v1 contract)");

        assertMatchesHeading(troubleshooting, "(?m)^##\\s+Agent\\s+JSON-First\\s+Protocol\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+Registry-Synced\\s+Template\\s+Keys\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+GREENFIELD_PR_CHECK_POLICY\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^###\\s+Exact\\s+Template\\s+Keys\\s+\\(AgentTemplateRegistry\\.EXACT\\)\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^###\\s+Failure\\s+Default\\s+Keys\\s+\\(AgentTemplateRegistry\\.FAILURE_DEFAULTS\\)\\s*$");
        assertContains(troubleshooting, "`POST_FAILURE_DISCIPLINE`");
        assertContains(troubleshooting, "do not move/copy impl or exception classes into `_shared` as a containment workaround.");

        assertContains(contracts, "In automation, `--agent` JSON on stdout is the authoritative control interface.");

        assertContains(bootstrap, "Before implementation edits, load `.bear/agent/TROUBLESHOOTING.md` and `.bear/agent/REPORTING.md`.");
        assertContains(bootstrap, "GREENFIELD_HARD_STOP");
        assertContains(bootstrap, "INDEX_REQUIRED_PREFLIGHT");
        assertContains(bootstrap, "POST_FAILURE_DISCIPLINE");
        assertContains(bootstrap, "COMPLETE_DISCIPLINE");
        assertContains(bootstrap, "allowed run outcomes are fixed: `COMPLETE | BLOCKED | WAITING_FOR_BASELINE_REVIEW`.");
        assertContains(bootstrap, "do not run ad-hoc gate reruns unless rerun is explicitly listed in `nextAction.commands`.");
        assertContains(bootstrap, "never move/copy impl or exception classes into `_shared`.");
        assertContains(bootstrap, "AGENT_PACKAGE_PARITY_PRECONDITION");
        assertContains(bootstrap, "PROCESS_VIOLATION|AGENT_PACKAGE_PARITY_PRECONDITION|<missingPath>");
        assertContains(bootstrap, "`bear check --all --project <repoRoot> [--collect=all] --agent`");
        assertContains(bootstrap, "`bear pr-check --all --project <repoRoot> --base <ref> [--collect=all] --agent`");
        assertContains(bootstrap, "`bear check --all --project <repoRoot> [--collect=all] --agent => 0`");
        assertContains(bootstrap, "`bear pr-check --all --project <repoRoot> --base <ref> [--collect=all] --agent => 0`");
        assertContains(bootstrap, "Execute `nextAction.commands` exactly as written.");
        assertContains(bootstrap, "If a command starts with `bear` and `bear` is not on PATH");
        assertFalse(bootstrap.contains("[--collect=all] [--agent]"), "Bootstrap done-gate examples must require --agent in agent protocol docs");
    }

    @Test
    void troubleshootingTemplateKeyTablesStayInSyncWithRegistry() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String troubleshooting = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/TROUBLESHOOTING.md"));

        Set<String> documentedExact = parseBacktickKeysFromSection(
            troubleshooting,
            "### Exact Template Keys (AgentTemplateRegistry.EXACT)"
        );
        Set<String> documentedDefaults = parseBacktickKeysFromSection(
            troubleshooting,
            "### Failure Default Keys (AgentTemplateRegistry.FAILURE_DEFAULTS)"
        );

        Set<String> runtimeExact = new TreeSet<>(templateMap("EXACT").keySet());
        Set<String> runtimeDefaults = new TreeSet<>(templateMap("FAILURE_DEFAULTS").keySet());

        assertEquals(runtimeExact, documentedExact, "Exact template key docs must match AgentTemplateRegistry.EXACT");
        assertEquals(runtimeDefaults, documentedDefaults, "Failure default key docs must match AgentTemplateRegistry.FAILURE_DEFAULTS");
    }

    @Test
    void packageDocsStayWithinLineBudgets() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();

        String bootstrap = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/BOOTSTRAP.md"));
        int bootstrapLines = bootstrap.replace("\r\n", "\n").split("\n", -1).length;
        assertTrue(bootstrapLines <= 200, "BOOTSTRAP.md must stay within 200 lines; found " + bootstrapLines);

        String reporting = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/REPORTING.md"));
        int reportingLines = reporting.replace("\r\n", "\n").split("\n", -1).length;
        assertTrue(reportingLines <= 220, "REPORTING.md must stay within 220 lines; found " + reportingLines);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> templateMap(String fieldName) throws Exception {
        Field field = AgentTemplateRegistry.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }

    private static Set<String> parseBacktickKeysFromSection(String markdown, String heading) {
        String normalized = markdown.replace("\r\n", "\n");
        int start = normalized.indexOf(heading);
        assertTrue(start >= 0, "Missing section heading: " + heading);

        int contentStart = normalized.indexOf('\n', start);
        assertTrue(contentStart >= 0, "Malformed heading block: " + heading);
        String remainder = normalized.substring(contentStart + 1);

        int nextH3 = remainder.indexOf("\n### ");
        int nextH2 = remainder.indexOf("\n## ");
        int end = remainder.length();
        if (nextH3 >= 0) {
            end = Math.min(end, nextH3);
        }
        if (nextH2 >= 0) {
            end = Math.min(end, nextH2);
        }

        String block = remainder.substring(0, end);
        Matcher matcher = Pattern.compile("`([^`]+)`").matcher(block);
        Set<String> keys = new TreeSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static void assertMatchesHeading(String content, String headingRegex) {
        assertTrue(Pattern.compile(headingRegex).matcher(content).find(), "Expected heading anchor missing: " + headingRegex);
    }

    private static void assertContains(String content, String token) {
        assertTrue(content.contains(token), "Expected exact token missing: " + token);
    }
}


