package com.bear.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDiagnosticsTest {
    @Test
    void payloadIsDeterministicAcrossInputOrder() {
        List<AgentDiagnostics.AgentProblem> problems = List.of(
            problem(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.BOUNDARY_BYPASS, "DIRECT_IMPL_USAGE", null, "src/main/java/blocks/a/impl/A.java"),
            problem(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, null, "PROJECT_TEST_LOCK", "build.gradle"),
            problem(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.UNDECLARED_REACH, CliCodes.UNDECLARED_REACH, null, "src/main/java/blocks/a/impl/B.java")
        );

        ArrayList<AgentDiagnostics.AgentProblem> shuffled = new ArrayList<>(problems);
        Collections.shuffle(shuffled);

        AgentDiagnostics.AgentPayload first = AgentDiagnostics.payload("check", "single", "all", CliCodes.EXIT_IO, problems);
        AgentDiagnostics.AgentPayload second = AgentDiagnostics.payload("check", "single", "all", CliCodes.EXIT_IO, shuffled);

        assertEquals(first.problems(), second.problems());
        assertEquals(first.clusters(), second.clusters());
        assertEquals(AgentDiagnostics.toJson(first), AgentDiagnostics.toJson(second));
        assertNotNull(first.nextAction());
        assertTrue(first.clusters().stream().anyMatch(cluster -> cluster.clusterId().equals(first.nextAction().primaryClusterId())));
    }

    @Test
    void payloadTruncationPreservesBreadthAndPrimaryCluster() {
        ArrayList<AgentDiagnostics.AgentProblem> problems = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            problems.add(AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.GOVERNANCE,
                CliCodes.BOUNDARY_BYPASS,
                "DIRECT_IMPL_USAGE",
                null,
                AgentDiagnostics.AgentSeverity.ERROR,
                "alpha",
                "src/main/java/blocks/a/impl/A.java",
                null,
                "DIRECT_IMPL_USAGE_" + i,
                "DIRECT_IMPL_USAGE_" + i,
                java.util.Map.of("templateVariant", "direct_" + i, "identityKey", "direct_" + i)
            ));
            problems.add(AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.GOVERNANCE,
                CliCodes.BOUNDARY_BYPASS,
                "IMPL_CONTAINMENT_BYPASS",
                null,
                AgentDiagnostics.AgentSeverity.ERROR,
                "alpha",
                "src/main/java/blocks/b/impl/B.java",
                null,
                "IMPL_CONTAINMENT_BYPASS_" + i,
                "IMPL_CONTAINMENT_BYPASS_" + i,
                java.util.Map.of("templateVariant", "containment_" + i, "identityKey", "containment_" + i)
            ));
            problems.add(AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.GOVERNANCE,
                CliCodes.BOUNDARY_BYPASS,
                "BLOCK_PORT_IMPL_INVALID",
                null,
                AgentDiagnostics.AgentSeverity.ERROR,
                "alpha",
                "src/main/java/blocks/c/impl/C.java",
                null,
                "BLOCK_PORT_IMPL_INVALID_" + i,
                "BLOCK_PORT_IMPL_INVALID_" + i,
                java.util.Map.of("templateVariant", "port_" + i, "identityKey", "port_" + i)
            ));
        }

        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload("check", "all", "all", CliCodes.EXIT_BOUNDARY_BYPASS, problems);

        assertTrue(payload.truncated());
        assertEquals(200, payload.problems().size());
        assertEquals(10, payload.suppressedViolations());
        assertEquals(3, payload.clusters().size());
        assertTrue(payload.problems().stream().anyMatch(problem -> "DIRECT_IMPL_USAGE".equals(problem.ruleId())));
        assertTrue(payload.problems().stream().anyMatch(problem -> "IMPL_CONTAINMENT_BYPASS".equals(problem.ruleId())));
        assertTrue(payload.problems().stream().anyMatch(problem -> "BLOCK_PORT_IMPL_INVALID".equals(problem.ruleId())));

        AgentDiagnostics.AgentCluster primaryCluster = payload.clusters().stream()
            .filter(cluster -> cluster.clusterId().equals(payload.nextAction().primaryClusterId()))
            .findFirst()
            .orElseThrow();
        assertEquals(70, primaryCluster.count());
        assertEquals(1, primaryCluster.files().size());
        assertFalse(primaryCluster.filesTruncated());
    }


    @Test
    void jsonSchemaOmitsResultCategoryAndIncludesVersionAnchor() {
        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
            "check",
            "single",
            "first",
            CliCodes.EXIT_IO,
            List.of(problem(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, null, "PROJECT_TEST_BOOTSTRAP", "project.tests"))
        );

        String json = AgentDiagnostics.toJson(payload);
        assertTrue(json.contains("\"schemaVersion\":\"bear.nextAction.v1\""));
        assertFalse(json.contains("resultCategory"));
    }

    @Test
    void governanceRegistryRuleIdsProduceTemplateBackedNextAction() {
        for (String ruleId : GovernanceRuleRegistry.PUBLIC_RULE_IDS) {
            AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
                "check",
                "single",
                "first",
                CliCodes.EXIT_BOUNDARY_BYPASS,
                List.of(problem(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.BOUNDARY_BYPASS, ruleId, null, "src/main/java/blocks/x/impl/X.java"))
            );
            assertNotNull(payload.nextAction(), "Missing nextAction for ruleId: " + ruleId);
            assertFalse(payload.nextAction().commands().isEmpty(), "Missing commands for ruleId: " + ruleId);
        }
    }

    @Test
    void unknownTemplateKeyFallsBackToSafeInfraTemplate() {
        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
            "check",
            "single",
            "first",
            CliCodes.EXIT_INTERNAL,
            List.of(problem(AgentDiagnostics.AgentCategory.INFRA, "SOME_NEW_FAILURE", null, "SOME_NEW_REASON", "internal"))
        );

        assertNotNull(payload.nextAction());
        assertEquals("INFRA", payload.nextAction().kind());
        assertTrue(payload.nextAction().title().toLowerCase().contains("capture"));
    }

    @Test
    void rerunCommandRoundTripsViaCliParserForCheckSingle() {
        AgentCommandContext expected = AgentCommandContext.forCheckSingle(
            Path.of("spec/withdraw.bear.yaml"),
            Path.of("."),
            Path.of("bear.blocks.yaml"),
            true,
            true,
            true
        );
        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
            expected,
            CliCodes.EXIT_IO,
            List.of(problem(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, null, "UNMAPPED_REASON", "project.tests")),
            true
        );

        assertNotNull(payload.nextAction());
        String rerun = payload.nextAction().commands().get(0);
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
    }

    @Test
    void rerunCommandRoundTripsViaCliParserForPrCheckSingle() {
        AgentCommandContext expected = AgentCommandContext.forPrCheckSingle(
            "spec/withdraw.bear.yaml",
            Path.of("."),
            "origin/main",
            Path.of("bear.blocks.yaml"),
            true,
            true
        );
        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
            expected,
            CliCodes.EXIT_IO,
            List.of(problem(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_GIT, null, "UNMAPPED_REASON", "git.repo")),
            true
        );

        assertNotNull(payload.nextAction());
        String rerun = payload.nextAction().commands().get(0);
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
    }

    @Test
    void rerunCommandRoundTripsViaCliParserForCheckAllAndPrCheckAll() {
        AgentCommandContext expectedCheckAll = new AgentCommandContext(
            "check",
            "all",
            null,
            ".",
            null,
            true,
            true,
            true,
            true,
            true,
            null,
            "bear.blocks.yaml",
            java.util.Set.of("alpha", "beta")
        );
        AgentDiagnostics.AgentPayload checkAllPayload = AgentDiagnostics.payload(
            expectedCheckAll,
            CliCodes.EXIT_IO,
            List.of(problem(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, null, "UNMAPPED_REASON", "project.tests")),
            true
        );
        String checkAllRerun = checkAllPayload.nextAction().commands().get(0);
        AgentCommandContext reparsedCheckAll = AgentCommandContextTestSupport.parseCommandContext(checkAllRerun);
        AgentCommandContextTestSupport.assertEquivalent(expectedCheckAll, reparsedCheckAll);

        AgentCommandContext expectedPrAll = new AgentCommandContext(
            "pr-check",
            "all",
            null,
            ".",
            "origin/main",
            true,
            true,
            false,
            true,
            false,
            null,
            "bear.blocks.yaml",
            java.util.Set.of("alpha", "beta")
        );
        AgentDiagnostics.AgentPayload prAllPayload = AgentDiagnostics.payload(
            expectedPrAll,
            CliCodes.EXIT_IO,
            List.of(problem(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_GIT, null, "UNMAPPED_REASON", "git.repo")),
            true
        );
        String prAllRerun = prAllPayload.nextAction().commands().get(0);
        AgentCommandContext reparsedPrAll = AgentCommandContextTestSupport.parseCommandContext(prAllRerun);
        AgentCommandContextTestSupport.assertEquivalent(expectedPrAll, reparsedPrAll);
    }
    @Test
    void rerunRepairEmitsWarningProblemAndUsesRepoRelativeBlocksPath() {
        Path repoRoot = Path.of(".").toAbsolutePath().normalize();
        String absoluteBlocks = repoRoot.resolve("bear.blocks.yaml").toString().replace('/', '\\');

        AgentCommandContext context = new AgentCommandContext(
            "check",
            "all",
            null,
            repoRoot.toString(),
            null,
            true,
            true,
            false,
            false,
            false,
            null,
            absoluteBlocks,
            java.util.Set.of()
        );

        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
            context,
            CliCodes.EXIT_IO,
            List.of(problem(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, null, "UNMAPPED_REASON", "project.tests")),
            true
        );

        assertNotNull(payload.nextAction());
        assertFalse(payload.nextAction().commands().isEmpty());
        String rerun = payload.nextAction().commands().get(0);
        assertTrue(rerun.contains("--blocks bear.blocks.yaml"), rerun);
        assertTrue(payload.problems().stream().anyMatch(problem ->
            CliCodes.NEXT_ACTION_COMMAND_INVALID.equals(problem.failureCode())
                && problem.severity() == AgentDiagnostics.AgentSeverity.WARNING
        ));
    }

    @Test
    void hardInvalidRerunRoutesToTroubleshootingWithoutCommands() {
        Path repoRoot = Path.of(".").toAbsolutePath().normalize();
        Path outsideRoot = repoRoot.getParent() == null
            ? repoRoot.resolveSibling("outside")
            : repoRoot.getParent().resolve("outside");
        String outsideBlocks = outsideRoot.resolve("bear.blocks.yaml").toString();

        AgentCommandContext context = new AgentCommandContext(
            "check",
            "all",
            null,
            repoRoot.toString(),
            null,
            true,
            true,
            false,
            false,
            false,
            null,
            outsideBlocks,
            java.util.Set.of()
        );

        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
            context,
            CliCodes.EXIT_IO,
            List.of(problem(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, null, "UNMAPPED_REASON", "project.tests")),
            true
        );

        assertNotNull(payload.nextAction());
        assertEquals("INFRA", payload.nextAction().kind());
        assertTrue(payload.nextAction().commands().isEmpty());
        assertTrue(payload.nextAction().links().contains("troubleshooting"));
        assertTrue(payload.problems().stream().anyMatch(problem ->
            CliCodes.NEXT_ACTION_COMMAND_INVALID.equals(problem.failureCode())
                && problem.severity() == AgentDiagnostics.AgentSeverity.ERROR
        ));
    }
    @Test
    void infraProblemsMustNotCarryRuleId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> AgentDiagnostics.problem(
            AgentDiagnostics.AgentCategory.INFRA,
            CliCodes.IO_ERROR,
            "SHOULD_NOT_BE_SET",
            "PROJECT_TEST_LOCK",
            AgentDiagnostics.AgentSeverity.ERROR,
            "alpha",
            "project.tests",
            null,
            "PROJECT_TEST_LOCK",
            "io lock",
            java.util.Map.of()
        ));
        assertTrue(ex.getMessage().contains("INFRA"));
    }

    @Test
    void repeatableRulesRequireIdentityKey() {
        for (String ruleId : RepeatableRuleRegistry.RULE_IDS) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.GOVERNANCE,
                CliCodes.BOUNDARY_BYPASS,
                ruleId,
                null,
                AgentDiagnostics.AgentSeverity.ERROR,
                "alpha",
                "src/main/java/blocks/a/impl/A.java",
                null,
                ruleId,
                "detail",
                java.util.Map.of()
            ));
            assertTrue(ex.getMessage().contains("identityKey"));
        }
    }

    @Test
    void repeatedPortImplFindingsHaveDistinctStableIds() {
        AgentDiagnostics.AgentProblem first = AgentDiagnostics.problem(
            AgentDiagnostics.AgentCategory.GOVERNANCE,
            CliCodes.BOUNDARY_BYPASS,
            "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
            null,
            AgentDiagnostics.AgentSeverity.ERROR,
            "alpha",
            "src/main/java/com/bear/account/demo/App.java",
            null,
            "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
            "detail 1",
            java.util.Map.of("identityKey", "AccountStorePort->InMemoryAccountStore")
        );
        AgentDiagnostics.AgentProblem second = AgentDiagnostics.problem(
            AgentDiagnostics.AgentCategory.GOVERNANCE,
            CliCodes.BOUNDARY_BYPASS,
            "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
            null,
            AgentDiagnostics.AgentSeverity.ERROR,
            "alpha",
            "src/main/java/com/bear/account/demo/App.java",
            null,
            "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
            "detail 2",
            java.util.Map.of("identityKey", "TransactionLogPort->InMemoryTransactionLog")
        );
        AgentDiagnostics.AgentProblem third = AgentDiagnostics.problem(
            AgentDiagnostics.AgentCategory.GOVERNANCE,
            CliCodes.BOUNDARY_BYPASS,
            "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
            null,
            AgentDiagnostics.AgentSeverity.ERROR,
            "alpha",
            "src/main/java/com/bear/account/demo/App.java",
            null,
            "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
            "detail 3",
            java.util.Map.of("identityKey", "IdempotencyPort->InMemoryIdempotencyStore")
        );

        assertFalse(first.id().equals(second.id()));
        assertFalse(first.id().equals(third.id()));
        assertFalse(second.id().equals(third.id()));

        AgentDiagnostics.AgentProblem firstRepeat = AgentDiagnostics.problem(
            AgentDiagnostics.AgentCategory.GOVERNANCE,
            CliCodes.BOUNDARY_BYPASS,
            "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
            null,
            AgentDiagnostics.AgentSeverity.ERROR,
            "alpha",
            "src/main/java/com/bear/account/demo/App.java",
            null,
            "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
            "detail 1",
            java.util.Map.of("identityKey", "AccountStorePort->InMemoryAccountStore")
        );
        assertEquals(first.id(), firstRepeat.id());
    }
    private static AgentDiagnostics.AgentProblem problem(
        AgentDiagnostics.AgentCategory category,
        String failureCode,
        String ruleId,
        String reasonKey,
        String file
    ) {
        java.util.Map<String, String> evidence = RepeatableRuleRegistry.requiresIdentityKey(ruleId)
            ? java.util.Map.of("identityKey", (file == null ? "" : file) + "|" + ruleId)
            : java.util.Map.of();
        return AgentDiagnostics.problem(
            category,
            failureCode,
            ruleId,
            reasonKey,
            AgentDiagnostics.AgentSeverity.ERROR,
            "alpha",
            file,
            null,
            ruleId != null ? ruleId : reasonKey,
            failureCode,
            evidence
        );
    }
}




