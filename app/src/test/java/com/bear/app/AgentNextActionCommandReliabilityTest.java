package com.bear.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentNextActionCommandReliabilityTest {
    @Test
    void nextActionCommandsRoundTripViaRealParserAcrossSingleAndAllModes() {
        Path repoRoot = Path.of(".").toAbsolutePath().normalize();
        Path blocksPath = repoRoot.resolve("bear.blocks.yaml");

        AgentCommandContext checkSingle = AgentCommandContext.forCheckSingle(
            Path.of("spec/account-service.bear.yaml"),
            repoRoot,
            null,
            true,
            true,
            true
        );
        AgentCommandContext prCheckSingle = AgentCommandContext.forPrCheckSingle(
            "spec/account-service.bear.yaml",
            repoRoot,
            "origin/main",
            null,
            true,
            true
        );
        AgentCommandContext checkAll = AgentCommandContext.forCheckAll(
            new AllCheckOptions(repoRoot, blocksPath, Set.of(), false, false, true, true, true)
        );
        AgentCommandContext prCheckAll = AgentCommandContext.forPrCheckAll(
            new AllPrCheckOptions(repoRoot, blocksPath, Set.of(), false, "origin/main", true, true)
        );

        assertRoundTrip(checkSingle, CliCodes.EXIT_IO);
        assertRoundTrip(prCheckSingle, CliCodes.EXIT_BOUNDARY_EXPANSION);
        assertRoundTrip(checkAll, CliCodes.EXIT_IO);
        assertRoundTrip(prCheckAll, CliCodes.EXIT_BOUNDARY_EXPANSION);
    }

    @Test
    void containmentMetadataMismatchTemplateIncludesBoundedCompileAndRoundTripsRerun() {
        Path repoRoot = Path.of(".").toAbsolutePath().normalize();
        Path blocksPath = repoRoot.resolve("bear.blocks.yaml");
        AgentCommandContext expected = AgentCommandContext.forCheckAll(
            new AllCheckOptions(repoRoot, blocksPath, Set.of(), false, false, true, true, true)
        );

        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
            expected,
            CliCodes.EXIT_TEST_FAILURE,
            List.of(AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.INFRA,
                CliCodes.CONTAINMENT_NOT_VERIFIED,
                null,
                CliCodes.CONTAINMENT_METADATA_MISMATCH,
                AgentDiagnostics.AgentSeverity.ERROR,
                null,
                "project.tests",
                null,
                CliCodes.CONTAINMENT_METADATA_MISMATCH,
                "containment compile mismatch",
                java.util.Map.of()
            )),
            true
        );

        assertNotNull(payload.nextAction());
        assertEquals("bear compile --all --project " + repoRoot.toString().replace('\\', '/'), payload.nextAction().commands().get(0));
        String rerun = AgentCommandContextTestSupport.firstRerunCommand(AgentDiagnostics.toJson(payload));
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
    }

    private static void assertRoundTrip(AgentCommandContext expected, int exitCode) {
        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
            expected,
            exitCode,
            List.of(AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.INFRA,
                CliCodes.IO_ERROR,
                null,
                "PROJECT_TEST_BOOTSTRAP",
                AgentDiagnostics.AgentSeverity.ERROR,
                null,
                "project.tests",
                null,
                "PROJECT_TEST_BOOTSTRAP",
                "bootstrap IO",
                java.util.Map.of()
            )),
            true
        );
        assertNotNull(payload.nextAction());
        String rerun = AgentCommandContextTestSupport.firstRerunCommand(AgentDiagnostics.toJson(payload));
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
    }
}