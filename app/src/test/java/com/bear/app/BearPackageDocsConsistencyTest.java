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

        assertMatchesHeading(reporting, "(?m)^##\\s+Agent\\s+Loop\\s+Contract\\s*$");
        assertContains(reporting, "Automation MUST parse only stdout JSON in `--agent` mode");
        assertContains(reporting, "If `status=fail` and `nextAction.commands` exists, execute only those BEAR commands");
        assertContains(reporting, "If `status=fail` and `nextAction` is `null`, route to `.bear/agent/TROUBLESHOOTING.md`");
        assertContains(reporting, "Field-level quickref: `.bear/agent/ref/AGENT_JSON_QUICKREF.md`.");

        assertMatchesHeading(troubleshooting, "(?m)^##\\s+Agent\\s+JSON-First\\s+Protocol\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+Registry-Synced\\s+Template\\s+Keys\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+GREENFIELD_PR_CHECK_POLICY\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^###\\s+Exact\\s+Template\\s+Keys\\s+\\(AgentTemplateRegistry\\.EXACT\\)\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^###\\s+Failure\\s+Default\\s+Keys\\s+\\(AgentTemplateRegistry\\.FAILURE_DEFAULTS\\)\\s*$");

        assertContains(contracts, "In automation, `--agent` JSON on stdout is the authoritative control interface.");

        assertFalse(bootstrap.contains("## AGENT_PACKAGE_PARITY_PRECONDITION"));
        assertFalse(bootstrap.contains("## GREENFIELD_HARD_STOP"));
        assertFalse(bootstrap.contains("## INDEX_REQUIRED_PREFLIGHT"));
        assertFalse(bootstrap.contains("## GREENFIELD_PR_CHECK_POLICY"));
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
    void bootstrapStaysWithinLineBudget() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String bootstrap = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/BOOTSTRAP.md"));
        String normalized = bootstrap.replace("\r\n", "\n");
        int lineCount = normalized.split("\n", -1).length;
        assertTrue(lineCount <= 200, "BOOTSTRAP.md must stay within 200 lines; found " + lineCount);
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
