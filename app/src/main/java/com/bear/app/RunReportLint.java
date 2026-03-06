package com.bear.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RunReportLint {
    private static final Set<String> ALLOWED_OUTCOMES = Set.of("COMPLETE", "BLOCKED", "WAITING_FOR_BASELINE_REVIEW");
    private static final Pattern STATUS_PATTERN = Pattern.compile(
        "^Status:\\s*tests=(PASS|FAIL);\\s*check=(-?\\d+)\\s*;\\s*pr-check=(-?\\d+)\\s+base=([^;]+);\\s*outcome=([A-Z_]+)\\s*$"
    );
    private static final Pattern POSITIVE_COMPLETION_PATTERN = Pattern.compile("\\b(all\\s+passing|complete|completed|done)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATED_COMPLETION_PATTERN = Pattern.compile("\\b(not\\s+complete|not\\s+completed|not\\s+done)\\b", Pattern.CASE_INSENSITIVE);

    private RunReportLint() {
    }

    static ReportLintResult lint(String report) {
        String normalized = report == null ? "" : report.replace("\r\n", "\n");
        List<String> lines = List.of(normalized.split("\\n", -1));
        ArrayList<String> violations = new ArrayList<>();

        String statusLine = firstLineStartingWith(lines, "Status:");
        ParsedStatus parsedStatus = parseStatus(statusLine, violations);

        String runOutcomeLine = firstLineStartingWith(lines, "Run outcome:");
        String runOutcome = null;
        if (runOutcomeLine == null) {
            violations.add("Missing required field: Status line must be accompanied by Run outcome.");
        } else {
            runOutcome = runOutcomeLine.substring("Run outcome:".length()).trim();
            if (!ALLOWED_OUTCOMES.contains(runOutcome)) {
                violations.add("Run outcome must be one of COMPLETE|BLOCKED|WAITING_FOR_BASELINE_REVIEW.");
            }
        }

        if (parsedStatus != null) {
            if (!ALLOWED_OUTCOMES.contains(parsedStatus.outcome())) {
                violations.add("Status outcome must be one of COMPLETE|BLOCKED|WAITING_FOR_BASELINE_REVIEW.");
            }
            if (runOutcome != null && !runOutcome.equals(parsedStatus.outcome())) {
                violations.add("Status outcome must match Run outcome.");
            }
        }

        String irDelta = fieldValue(lines, "IR delta:");
        String decomposition = fieldValue(lines, "Decomposition contract consulted:");
        if (requiresDecompositionCheckpoint(irDelta)) {
            if (decomposition == null || !decomposition.toLowerCase(Locale.ROOT).startsWith("yes")) {
                violations.add("Missing decomposition checkpoint for IR authoring/modification run.");
            }
        }

        String gateResultsHeader = firstLineStartingWith(lines, "Gate results:");
        if (gateResultsHeader == null) {
            violations.add("Missing required field: Gate results.");
        }

        List<String> gateResultLines = extractGateResultLines(lines);
        if (gateResultLines.isEmpty()) {
            violations.add("Gate results must include at least one '- bear ... => <exit>' line.");
        }

        if ("COMPLETE".equals(runOutcome)) {
            if (parsedStatus == null) {
                violations.add("Run outcome COMPLETE requires valid Status line.");
            } else {
                if (parsedStatus.checkExit() != 0 || parsedStatus.prCheckExit() != 0) {
                    violations.add("Run outcome COMPLETE requires check=0 and pr-check=0 in Status.");
                }
            }
            enforceCanonicalDoneGate(
                gateResultLines,
                CanonicalDoneGateMatcher.GateKind.CHECK,
                "Run outcome COMPLETE requires canonical check done gate with => 0 and --agent.",
                violations
            );
            enforceCanonicalDoneGate(
                gateResultLines,
                CanonicalDoneGateMatcher.GateKind.PR_CHECK,
                "Run outcome COMPLETE requires canonical pr-check done gate with => 0 and --agent.",
                violations
            );
        }

        if ("BLOCKED".equals(runOutcome) || "WAITING_FOR_BASELINE_REVIEW".equals(runOutcome)) {
            String requiredNextAction = fieldValue(lines, "Required next action:");
            if (requiredNextAction == null || requiredNextAction.isBlank()) {
                violations.add("Run outcome BLOCKED/WAITING_FOR_BASELINE_REVIEW requires Required next action.");
            }
            String blocker = fieldValue(lines, "Gate blocker:");
            if (blocker == null || blocker.isBlank()) {
                violations.add("Run outcome BLOCKED/WAITING_FOR_BASELINE_REVIEW requires Gate blocker.");
            }
        }

        if ("WAITING_FOR_BASELINE_REVIEW".equals(runOutcome)) {
            if (parsedStatus != null && parsedStatus.prCheckExit() != 5) {
                violations.add("WAITING_FOR_BASELINE_REVIEW requires pr-check=5.");
            }
            String blocker = fieldValue(lines, "Gate blocker:");
            if (!"BOUNDARY_EXPANSION".equals(blocker)) {
                violations.add("WAITING_FOR_BASELINE_REVIEW requires Gate blocker=BOUNDARY_EXPANSION.");
            }
            String baselineScope = fieldValue(lines, "Baseline review scope:");
            if (baselineScope == null
                || !baselineScope.contains("bear.blocks.yaml")
                || !baselineScope.contains("bear-ir/*.bear.yaml")) {
                violations.add("Baseline review scope must include bear.blocks.yaml and bear-ir/*.bear.yaml (pinned v1 contract).");
            }
        }

        if (runOutcome != null && !"COMPLETE".equals(runOutcome)) {
            if (containsPositiveCompletionClaim(lines, statusLine, runOutcomeLine)) {
                violations.add("Non-COMPLETE outcome must not claim completion in scoped summary/status lines.");
            }
        }

        return new ReportLintResult(List.copyOf(violations));
    }

    static boolean requiresDecompositionCheckpoint(String irDelta) {
        if (irDelta == null || irDelta.isBlank()) {
            return false;
        }
        String normalized = irDelta.replace('\\', '/').toLowerCase(Locale.ROOT);
        boolean referencesBearIr = normalized.contains("bear-ir/*.bear.yaml")
            || normalized.matches(".*bear-ir/[^\\s,;]+\\.bear\\.ya?ml.*");
        boolean indicatesChange = normalized.contains("added")
            || normalized.contains("modified")
            || normalized.contains("authored")
            || normalized.contains("changed")
            || normalized.contains("updated");
        return referencesBearIr && indicatesChange;
    }

    private static ParsedStatus parseStatus(String statusLine, List<String> violations) {
        if (statusLine == null) {
            violations.add("Missing required field: Status.");
            return null;
        }
        Matcher matcher = STATUS_PATTERN.matcher(statusLine.trim());
        if (!matcher.matches()) {
            violations.add("Status line must match canonical format: tests=<PASS|FAIL>; check=<code>; pr-check=<code> base=<ref>; outcome=<token>.");
            return null;
        }
        try {
            int checkExit = Integer.parseInt(matcher.group(2));
            int prCheckExit = Integer.parseInt(matcher.group(3));
            String outcome = matcher.group(5);
            return new ParsedStatus(checkExit, prCheckExit, outcome);
        } catch (NumberFormatException ex) {
            violations.add("Status line has invalid numeric gate code.");
            return null;
        }
    }

    private static void enforceCanonicalDoneGate(
        List<String> gateResultLines,
        CanonicalDoneGateMatcher.GateKind gateKind,
        String missingMessage,
        List<String> violations
    ) {
        List<String> candidates = gateResultLines.stream()
            .filter(line -> gateKind == CanonicalDoneGateMatcher.GateKind.CHECK
                ? line.contains("bear check")
                : line.contains("bear pr-check"))
            .toList();

        if (candidates.isEmpty()) {
            violations.add(missingMessage);
            return;
        }

        String firstFailureReason = null;
        for (String candidate : candidates) {
            CanonicalDoneGateMatcher.MatchResult result = CanonicalDoneGateMatcher.match(candidate, gateKind);
            if (result.matched()) {
                return;
            }
            if (firstFailureReason == null) {
                firstFailureReason = result.reason();
            }
        }

        violations.add(missingMessage + " Reason: " + firstFailureReason);
    }

    private static boolean containsPositiveCompletionClaim(List<String> lines, String statusLine, String runOutcomeLine) {
        LinkedHashSet<String> scope = new LinkedHashSet<>();
        int maxLines = Math.min(10, lines.size());
        for (int i = 0; i < maxLines; i++) {
            scope.add(lines.get(i));
        }
        if (statusLine != null) {
            scope.add(statusLine);
        }
        if (runOutcomeLine != null) {
            scope.add(runOutcomeLine);
        }

        for (String line : scope) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String lowered = line.toLowerCase(Locale.ROOT);
            if (NEGATED_COMPLETION_PATTERN.matcher(lowered).find()) {
                continue;
            }
            if (POSITIVE_COMPLETION_PATTERN.matcher(lowered).find()) {
                return true;
            }
        }
        return false;
    }

    private static String firstLineStartingWith(List<String> lines, String prefix) {
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        return null;
    }

    private static String fieldValue(List<String> lines, String fieldPrefix) {
        String line = firstLineStartingWith(lines, fieldPrefix);
        if (line == null) {
            return null;
        }
        return line.substring(fieldPrefix.length()).trim();
    }

    private static List<String> extractGateResultLines(List<String> lines) {
        ArrayList<String> out = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- bear ") && trimmed.contains("=>")) {
                out.add(trimmed);
            }
        }
        return List.copyOf(out);
    }

    private record ParsedStatus(int checkExit, int prCheckExit, String outcome) {
    }

    record ReportLintResult(List<String> violations) {
        boolean ok() {
            return violations.isEmpty();
        }
    }
}

