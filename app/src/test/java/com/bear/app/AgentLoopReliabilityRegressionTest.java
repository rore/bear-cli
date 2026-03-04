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
            Path.of("spec/account-service.bear.yaml"),
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
            IR delta: added spec/account-service.bear.yaml
            Gate results:
            - bear check spec/account-service.bear.yaml --project . --collect=all --agent => 7
            Run outcome: COMPLETE
            """;
        RunReportLint.ReportLintResult badLint = RunReportLint.lint(incompleteReport);
        assertTrue(badLint.violations().stream().anyMatch(v -> v.contains("decomposition checkpoint")));
        assertTrue(badLint.violations().stream().anyMatch(v -> v.contains("done-gate")));

        String completeReport = """
            IR delta: added spec/account-service.bear.yaml
            Decomposition contract consulted: yes (before IR authoring)
            Gate results:
            - bear check --all --project . => 0
            - bear pr-check --all --project . --base origin/main => 0
            Run outcome: COMPLETE
            """;
        RunReportLint.ReportLintResult okLint = RunReportLint.lint(completeReport);
        assertTrue(okLint.ok());
    }
}
