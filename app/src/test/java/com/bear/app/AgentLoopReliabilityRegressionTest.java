package com.bear.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopReliabilityRegressionTest {
    @Test
    void regressionScenarioKeepsRerunEquivalentIdsUniqueAndReportGuarded() {
        AgentCommandContext context = AgentCommandContext.forCheckSingle(
            Path.of("bear-ir/account-service.bear.yaml"),
            Path.of("."),
            null,
            false,
            true,
            true
        );

        List<AgentDiagnostics.AgentProblem> problems = List.of(
            AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.GOVERNANCE,
                CliCodes.BOUNDARY_BYPASS,
                "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
                null,
                AgentDiagnostics.AgentSeverity.ERROR,
                "account-service",
                "src/main/java/com/bear/account/demo/App.java",
                null,
                "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
                "AccountStorePort -> InMemoryAccountStore",
                Map.of("identityKey", "AccountStorePort->InMemoryAccountStore")
            ),
            AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.GOVERNANCE,
                CliCodes.BOUNDARY_BYPASS,
                "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
                null,
                AgentDiagnostics.AgentSeverity.ERROR,
                "account-service",
                "src/main/java/com/bear/account/demo/App.java",
                null,
                "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
                "TransactionLogPort -> InMemoryTransactionLog",
                Map.of("identityKey", "TransactionLogPort->InMemoryTransactionLog")
            ),
            AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.GOVERNANCE,
                CliCodes.BOUNDARY_BYPASS,
                "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
                null,
                AgentDiagnostics.AgentSeverity.ERROR,
                "account-service",
                "src/main/java/com/bear/account/demo/App.java",
                null,
                "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
                "IdempotencyPort -> InMemoryIdempotencyStore",
                Map.of("identityKey", "IdempotencyPort->InMemoryIdempotencyStore")
            )
        );

        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(context, CliCodes.EXIT_BOUNDARY_BYPASS, problems, true);
        assertNotNull(payload.nextAction());

        String rerun = AgentCommandContextTestSupport.firstRerunCommand(AgentDiagnostics.toJson(payload));
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContextTestSupport.assertEquivalent(context, reparsed);

        Set<String> ids = payload.problems().stream().map(AgentDiagnostics.AgentProblem::id).collect(java.util.stream.Collectors.toSet());
        assertEquals(3, ids.size(), "Repeated findings must have unique deterministic problem IDs");

        String incompleteReport = """
            Status: tests=PASS; check=7; pr-check=5 base=origin/main; outcome=COMPLETE
            IR delta: added bear-ir/account-service.bear.yaml
            Gate results:
            - bear check --all --project . --agent => 7
            Run outcome: COMPLETE
            """;
        RunReportLint.ReportLintResult badLint = RunReportLint.lint(incompleteReport);
        assertTrue(badLint.violations().stream().anyMatch(v -> v.contains("decomposition checkpoint")));
        assertTrue(badLint.violations().stream().anyMatch(v -> v.contains("check=0 and pr-check=0")));

        String completeReport = """
            Status: tests=PASS; check=0; pr-check=0 base=origin/main; outcome=COMPLETE
            IR delta: added bear-ir/account-service.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            - bear check --all --project . --collect=all --agent => 0
            - bear pr-check --all --project . --base origin/main --collect=all --agent => 0
            Run outcome: COMPLETE
            """;
        RunReportLint.ReportLintResult okLint = RunReportLint.lint(completeReport);
        assertTrue(okLint.ok());
    }

    @Test
    void eventLintPassesForExactNextActionCommandSequence() {
        List<String> violations = AgentLoopEventLint.lint(List.of(
            new AgentLoopEventLint.GateRun(
                "bear pr-check --all --project . --base origin/main --collect=all --agent",
                5,
                true,
                List.of(
                    "bear pr-check --all --project . --base origin/main --blocks bear.blocks.yaml --collect=all --agent"
                )
            ),
            new AgentLoopEventLint.Exec("bear pr-check --all --project . --base origin/main --blocks bear.blocks.yaml --collect=all --agent"),
            new AgentLoopEventLint.GateRun("bear pr-check --all --project . --base origin/main --collect=all --agent", 5, true, List.of())
        ));

        assertTrue(violations.isEmpty(), String.join("; ", violations));
    }

    @Test
    void eventLintFailsOnAdHocCommandAndOrderDrift() {
        List<String> violations = AgentLoopEventLint.lint(List.of(
            new AgentLoopEventLint.GateRun(
                "bear pr-check --all --project . --base origin/main --collect=all --agent",
                5,
                true,
                List.of(
                    "bear compile --all --project .",
                    "bear pr-check --all --project . --base origin/main --collect=all --agent"
                )
            ),
            new AgentLoopEventLint.Exec("bear pr-check --all --project . --base origin/dev --collect=all --agent"),
            new AgentLoopEventLint.Exec("bear compile --all --project .")
        ));

        assertTrue(violations.stream().anyMatch(v -> v.contains("Command drift")), String.join("; ", violations));
        assertTrue(violations.stream().anyMatch(v -> v.contains("Missing nextAction command")), String.join("; ", violations));
    }
}
