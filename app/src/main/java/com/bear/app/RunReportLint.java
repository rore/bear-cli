package com.bear.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RunReportLint {
    private RunReportLint() {
    }

    static ReportLintResult lint(String report) {
        String normalized = report == null ? "" : report.replace("\r\n", "\n");
        ArrayList<String> violations = new ArrayList<>();

        String irDelta = fieldValue(normalized, "IR delta:");
        String decomposition = fieldValue(normalized, "Decomposition contract consulted:");
        if (requiresDecompositionCheckpoint(irDelta)) {
            if (decomposition == null || !decomposition.toLowerCase(Locale.ROOT).startsWith("yes")) {
                violations.add("Missing decomposition checkpoint for IR authoring/modification run.");
            }
        }

        String runOutcome = fieldValue(normalized, "Run outcome:");
        if ("COMPLETE".equals(runOutcome)) {
            boolean hasCheckDoneGate = normalized.contains("bear check --all --project");
            boolean hasPrCheckDoneGate = normalized.contains("bear pr-check --all --project")
                && normalized.contains("--base");
            if (!hasCheckDoneGate || !hasPrCheckDoneGate) {
                violations.add("Run outcome COMPLETE requires canonical done-gate evidence in Gate results.");
            }
        }

        return new ReportLintResult(List.copyOf(violations));
    }

    static boolean requiresDecompositionCheckpoint(String irDelta) {
        if (irDelta == null || irDelta.isBlank()) {
            return false;
        }
        String normalized = irDelta.replace('\\', '/').toLowerCase(Locale.ROOT);
        boolean referencesSpecIr = normalized.contains("spec/*.bear.yaml")
            || normalized.matches(".*spec/[^\\s,;]+\\.bear\\.ya?ml.*");
        boolean indicatesChange = normalized.contains("added")
            || normalized.contains("modified")
            || normalized.contains("authored")
            || normalized.contains("changed")
            || normalized.contains("updated");
        return referencesSpecIr && indicatesChange;
    }

    private static String fieldValue(String report, String fieldPrefix) {
        for (String line : report.split("\n")) {
            if (line.startsWith(fieldPrefix)) {
                return line.substring(fieldPrefix.length()).trim();
            }
        }
        return null;
    }

    record ReportLintResult(List<String> violations) {
        boolean ok() {
            return violations.isEmpty();
        }
    }
}
