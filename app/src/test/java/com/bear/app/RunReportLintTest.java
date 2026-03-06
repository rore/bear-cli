package com.bear.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunReportLintTest {
    @Test
    void missingStatusFails() {
        String report = """
            Run outcome: BLOCKED
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("Missing required field: Status")));
    }

    @Test
    void missingRunOutcomeFails() {
        String report = """
            Status: tests=PASS; check=0; pr-check=0 base=origin/main; outcome=COMPLETE
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("Run outcome")));
    }

    @Test
    void missingGateResultsFails() {
        String report = """
            Status: tests=PASS; check=0; pr-check=0 base=origin/main; outcome=COMPLETE
            IR delta: modified bear-ir/withdraw.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Run outcome: COMPLETE
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("Missing required field: Gate results")));
    }

    @Test
    void gateResultsWithoutGateLinesFails() {
        String report = """
            Status: tests=PASS; check=0; pr-check=0 base=origin/main; outcome=COMPLETE
            IR delta: modified bear-ir/withdraw.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            Run outcome: COMPLETE
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("at least one '- bear ... => <exit>'")));
    }

    @Test
    void invalidOutcomeTokenFails() {
        String report = """
            Status: tests=PASS; check=0; pr-check=0 base=origin/main; outcome=DONE
            Gate results:
            - bear check --all --project . --collect=all --agent => 0
            - bear pr-check --all --project . --base origin/main --collect=all --agent => 0
            Run outcome: DONE
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("must be one of COMPLETE|BLOCKED|WAITING_FOR_BASELINE_REVIEW")));
    }

    @Test
    void statusAndRunOutcomeMustMatch() {
        String report = """
            Status: tests=PASS; check=0; pr-check=5 base=origin/main; outcome=BLOCKED
            Gate results:
            - bear check --all --project . --collect=all --agent => 0
            - bear pr-check --all --project . --base origin/main --collect=all --agent => 5
            Run outcome: WAITING_FOR_BASELINE_REVIEW
            Required next action: boundary governance review and baseline merge
            Gate blocker: BOUNDARY_EXPANSION
            Baseline review scope: bear.blocks.yaml, bear-ir/*.bear.yaml
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("Status outcome must match Run outcome")));
    }

    @Test
    void completeRequiresZeroStatusAndCanonicalDoneGates() {
        String report = """
            Status: tests=PASS; check=0; pr-check=7 base=origin/main; outcome=COMPLETE
            IR delta: modified bear-ir/withdraw.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            - bear check --all --project . --agent => 0
            - bear pr-check --all --project . --base origin/main --agent => 7
            Run outcome: COMPLETE
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("check=0 and pr-check=0")));
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("canonical pr-check done gate")));
    }

    @Test
    void blockedRequiresGateBlocker() {
        String report = """
            Status: tests=PASS; check=0; pr-check=5 base=origin/main; outcome=BLOCKED
            IR delta: modified bear-ir/account.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            - bear check --all --project . --collect=all --agent => 0
            - bear pr-check --all --project . --base origin/main --collect=all --agent => 5
            Run outcome: BLOCKED
            Required next action: fix governance issue and rerun
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("requires Gate blocker")));
    }

    @Test
    void blockedPassesWithRequiredCoreFields() {
        String report = """
            Status: tests=PASS; check=0; pr-check=5 base=origin/main; outcome=BLOCKED
            IR delta: modified bear-ir/account.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            - bear check --all --project . --collect=all --agent => 0
            - bear pr-check --all --project . --base origin/main --collect=all --agent => 5
            Run outcome: BLOCKED
            Required next action: fix governance issue and rerun
            Gate blocker: OTHER
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertTrue(lint.ok());
    }

    @Test
    void waitingRequiresPinnedBaselineRules() {
        String report = """
            Status: tests=PASS; check=0; pr-check=5 base=origin/main; outcome=WAITING_FOR_BASELINE_REVIEW
            IR delta: added bear-ir/wallet.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            - bear check --all --project . --collect=all --agent => 0
            - bear pr-check --all --project . --base origin/main --collect=all --agent => 5
            Run outcome: WAITING_FOR_BASELINE_REVIEW
            Required next action: boundary governance review and baseline merge
            Gate blocker: BOUNDARY_EXPANSION
            Baseline review scope: bear-ir/*.bear.yaml
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("pinned v1 contract")));
    }

    @Test
    void nonCompleteMustNotClaimDoneInScopedSummary() {
        String report = """
            Developer Summary
            Status: tests=PASS; check=0; pr-check=5 base=origin/main; outcome=BLOCKED
            Blocking: boundary governance review required
            Next action: waiting for review
            Everything is complete and all passing.
            Gate results:
            - bear check --all --project . --collect=all --agent => 0
            - bear pr-check --all --project . --base origin/main --collect=all --agent => 5
            Run outcome: BLOCKED
            Required next action: boundary governance review and baseline merge
            Gate blocker: OTHER
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertFalse(lint.ok());
        assertTrue(lint.violations().stream().anyMatch(v -> v.contains("must not claim completion")));
    }


    @Test
    void productSpecDeltaDoesNotRequireIrDecompositionCheckpoint() {
        String report = """
            Status: tests=PASS; check=0; pr-check=0 base=origin/main; outcome=COMPLETE
            IR delta: modified spec/SPEC.feature-extension.md
            Gate results:
            - bear check --all --project . --collect=all --agent => 0
            - bear pr-check --all --project . --base origin/main --collect=all --agent => 0
            Run outcome: COMPLETE
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertTrue(lint.ok());
    }
    @Test
    void completePassesWithCanonicalZeroDoneGatesAndAgentMode() {
        String report = """
            Status: tests=PASS; check=0; pr-check=0 base=origin/main; outcome=COMPLETE
            IR delta: modified bear-ir/withdraw.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            - bear check --all --project . --collect=all --agent => 0
            - bear pr-check --all --project . --base origin/main --collect=all --agent => 0
            Run outcome: COMPLETE
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertTrue(lint.ok());
    }

    @Test
    void extrasAreAllowedWhenCoreIsValid() {
        String report = """
            Status: tests=PASS; check=0; pr-check=0 base=origin/main; outcome=COMPLETE
            IR delta: modified bear-ir/withdraw.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            - bear check --all --project . --collect=all --agent => 0
            - bear pr-check --all --project . --base origin/main --collect=all --agent => 0
            Run outcome: COMPLETE
            Request summary: optional narrative field
            Tooling anomaly: no
            Final git status: clean
            """;

        RunReportLint.ReportLintResult lint = RunReportLint.lint(report);
        assertTrue(lint.ok());
    }
}
