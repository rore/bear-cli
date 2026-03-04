package com.bear.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunReportLintTest {
    @Test
    void requiresDecompositionCheckpointWhenIrDeltaIndicatesSpecAuthoring() {
        String report = """
            IR delta: added spec/wallet.bear.yaml
            Run outcome: BLOCKED
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("decomposition checkpoint")));
    }

    @Test
    void doesNotRequireDecompositionCheckpointWithoutSpecIrDelta() {
        String report = """
            IR delta: no IR change
            Run outcome: BLOCKED
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertTrue(lint.ok());
    }

    @Test
    void completeOutcomeRequiresCanonicalDoneGateEvidence() {
        String report = """
            IR delta: modified spec/withdraw.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            - bear check spec/withdraw.bear.yaml --project . => 0
            Run outcome: COMPLETE
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("done-gate")));
    }

    @Test
    void nonCompleteOutcomeDoesNotRequireCanonicalDoneGates() {
        String report = """
            IR delta: modified spec/withdraw.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            - bear check spec/withdraw.bear.yaml --project . => 7
            Run outcome: BLOCKED
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertTrue(lint.ok());
    }

    @Test
    void completeOutcomePassesWithCanonicalDoneGatesAndDecompositionCheckpoint() {
        String report = """
            IR delta: modified spec/withdraw.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            - bear check --all --project . => 0
            - bear pr-check --all --project . --base origin/main => 0
            Run outcome: COMPLETE
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertTrue(lint.ok());
    }
}
