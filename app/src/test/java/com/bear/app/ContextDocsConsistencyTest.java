package com.bear.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextDocsConsistencyTest {
    @Test
    void contextDocLineBudgetsAreEnforced() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Map<String, Integer> budgets = new LinkedHashMap<>();
        budgets.put("docs/context/CONTEXT_BOOTSTRAP.md", 160);
        budgets.put("docs/context/state.md", 140);
        budgets.put("docs/context/start-here.md", 80);
        budgets.put("docs/context/program-board.md", 220);
        budgets.put("docs/context/prompt-bootstrap.md", 120);

        for (Map.Entry<String, Integer> entry : budgets.entrySet()) {
            Path path = repoRoot.resolve(entry.getKey());
            long lines = countLines(path);
            assertTrue(lines <= entry.getValue(),
                    entry.getKey() + " exceeds budget: " + lines + " > " + entry.getValue());
        }
    }

    @Test
    void stalePatternsAreBannedInActiveContextDocs() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path contextRoot = repoRoot.resolve("docs/context");
        List<Pattern> banned = List.of(
                Pattern.compile("v0 scope \\(locked\\)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("BEAR IR v0 canonical model", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\.bear/agent/BEAR_AGENT\\.md"),
                Pattern.compile("\\.bear/agent/WORKFLOW\\.md"),
                Pattern.compile("\\.bear/agent/doc/")
        );

        try (Stream<Path> files = Files.walk(contextRoot)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("docs/context/archive/"))
                    .forEach(path -> assertNoBannedPattern(path, banned));
        }
    }

    @Test
    void routingReferencesArePresent() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String agents = Files.readString(repoRoot.resolve("AGENTS.md"), StandardCharsets.UTF_8);
        String bootstrap = Files.readString(
                repoRoot.resolve("docs/context/CONTEXT_BOOTSTRAP.md"),
                StandardCharsets.UTF_8
        );

        assertTrue(agents.contains("docs/context/CONTEXT_BOOTSTRAP.md"));
        assertTrue(bootstrap.contains("docs/context/state.md"));
        assertTrue(bootstrap.contains("docs/context/program-board.md"));
        assertTrue(bootstrap.contains("docs/context/roadmap.md"));
        assertTrue(bootstrap.contains("docs/context/ir-spec.md"));
        assertTrue(bootstrap.contains("docs/context/governance.md"));
        assertTrue(bootstrap.contains("docs/context/safety-rules.md"));
        assertTrue(bootstrap.contains("docs/context/user-guide.md"));
        assertTrue(bootstrap.contains("docs/context/demo-agent-simulation.md"));
        assertTrue(bootstrap.contains("docs/context/prompt-bootstrap.md"));
        assertTrue(bootstrap.contains("docs/context/project-log.md"));
    }

    @Test
    void keyTopicRetentionChecksHold() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String bootstrap = Files.readString(
                repoRoot.resolve("docs/context/CONTEXT_BOOTSTRAP.md"),
                StandardCharsets.UTF_8
        );
        String startHere = Files.readString(
                repoRoot.resolve("docs/context/start-here.md"),
                StandardCharsets.UTF_8
        );
        String simulation = Files.readString(
                repoRoot.resolve("docs/context/demo-agent-simulation.md"),
                StandardCharsets.UTF_8
        );
        String safety = Files.readString(
                repoRoot.resolve("docs/context/safety-rules.md"),
                StandardCharsets.UTF_8
        );

        assertTrue(bootstrap.contains("bear check --all --project <repoRoot>"));
        assertTrue(bootstrap.contains("bear pr-check --all --project <repoRoot> --base <ref>"));
        assertTrue(startHere.contains("## Session Close Protocol"));
        assertTrue(simulation.contains("BEAR run grade:"));
        assertTrue(safety.contains("Never run recursive delete"));
    }

    @Test
    void stateDocHasRequiredStructureAndBoundedSessionNotes() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path statePath = repoRoot.resolve("docs/context/state.md");
        String state = Files.readString(statePath, StandardCharsets.UTF_8);

        assertTrue(state.contains("## Last Updated"));
        assertTrue(state.contains("## Current Focus"));
        assertTrue(state.contains("## Next Concrete Task"));
        assertTrue(state.contains("## Session Notes"));

        List<String> lines = Files.readAllLines(statePath, StandardCharsets.UTF_8);
        int sessionNotesHeader = indexOfLine(lines, "## Session Notes");
        assertTrue(sessionNotesHeader >= 0);
        int nextHeader = nextHeaderIndex(lines, sessionNotesHeader + 1);
        int end = nextHeader >= 0 ? nextHeader : lines.size();
        int sectionLines = end - sessionNotesHeader - 1;
        assertTrue(sectionLines <= 40, "Session Notes section is too large: " + sectionLines + " lines");
    }

    @Test
    void coverageMapIncludesRequiredTopics() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String map = Files.readString(
                repoRoot.resolve("docs/context/context-coverage-map.md"),
                StandardCharsets.UTF_8
        );

        assertTrue(map.contains("Session handoff protocol"));
        assertTrue(map.contains("Milestone status and ordered queue"));
        assertTrue(map.contains("Milestone definitions and done criteria"));
        assertTrue(map.contains("IR schema/normalization/semantic rule"));
        assertTrue(map.contains("Governance diff classification and enforcement intent"));
        assertTrue(map.contains("Safety cleanup/deletion guardrails"));
        assertTrue(map.contains("Demo simulation protocol and grading rubric"));
        assertTrue(map.contains("Operator command/failure guidance"));
        assertTrue(map.contains("Architecture scope lock and non-goals"));
        assertTrue(map.contains("Historical rationale trail"));
    }

    private static void assertNoBannedPattern(Path path, List<Pattern> banned) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            for (Pattern pattern : banned) {
                assertFalse(pattern.matcher(content).find(),
                        "Banned context pattern in " + path + ": " + pattern.pattern());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading " + path, e);
        }
    }

    private static long countLines(Path path) throws IOException {
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.count();
        }
    }

    private static int indexOfLine(List<String> lines, String exact) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(exact)) {
                return i;
            }
        }
        return -1;
    }

    private static int nextHeaderIndex(List<String> lines, int startInclusive) {
        for (int i = startInclusive; i < lines.size(); i++) {
            if (lines.get(i).startsWith("## ")) {
                return i;
            }
        }
        return -1;
    }
}
