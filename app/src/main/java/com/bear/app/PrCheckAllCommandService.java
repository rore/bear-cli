package com.bear.app;

import com.bear.kernel.target.*;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class PrCheckAllCommandService {
    private static final TargetRegistry TARGET_REGISTRY = TargetRegistry.defaultRegistry();
    private PrCheckAllCommandService() {
    }

    static int runPrCheckAll(String[] args, PrintStream out, PrintStream err) {
        AllPrCheckOptions options = AllModeOptionParser.parseAllPrCheckOptions(args, err);
        if (options == null) {
            return CliCodes.EXIT_USAGE;
        }
        if (AllModeIndexPreflight.isMissing(options.blocksPath())) {
            if (options.agent()) {
                AgentDiagnostics.AgentProblem problem = AgentDiagnostics.problem(
                    AgentDiagnostics.AgentCategory.INFRA,
                    CliCodes.INDEX_REQUIRED_MISSING,
                    null,
                    CliCodes.INDEX_REQUIRED_MISSING,
                    AgentDiagnostics.AgentSeverity.ERROR,
                    null,
                    "bear.blocks.yaml",
                    null,
                    CliCodes.INDEX_REQUIRED_MISSING,
                    AllModeIndexPreflight.MISSING_INDEX_LINE,
                    Map.of()
                );
                AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
                    AgentCommandContext.forPrCheckAll(options),
                    CliCodes.EXIT_VALIDATION,
                    List.of(problem),
                    true
                );
                out.println(AgentDiagnostics.toJson(payload));
                return CliCodes.EXIT_VALIDATION;
            }
            Integer missingIndexExit = AllModeIndexPreflight.failIfMissing(options.blocksPath(), err);
            if (missingIndexExit != null) {
                return missingIndexExit;
            }
        }

        BlockIndex index;
        try {
            index = new BlockIndexParser().parse(options.repoRoot(), options.blocksPath(), true);
        } catch (BlockIndexValidationException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_VALIDATION,
                "index: VALIDATION_ERROR: " + e.getMessage(),
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear pr-check --all`."
            );
        } catch (IOException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_IO,
                "io: IO_ERROR: " + CliText.squash(e.getMessage()),
                CliCodes.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure `bear.blocks.yaml` is readable and rerun `bear pr-check --all`."
            );
        }

        List<BlockIndexEntry> selected = AllModeBlockDiscovery.selectBlocks(index, options.onlyNames());
        if (selected == null) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: unknown block in --only",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Use only block names declared in `bear.blocks.yaml`."
            );
        }

        try {
            List<String> legacyMarkers = options.strictOrphans()
                ? AllModeBlockDiscovery.computeLegacyMarkersRepoWide(options.repoRoot())
                : AllModeBlockDiscovery.computeLegacyMarkersInManagedRoots(options.repoRoot(), selected);
            if (!legacyMarkers.isEmpty()) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_IO,
                    "pr-check: IO_ERROR: LEGACY_SURFACE_MARKER: " + legacyMarkers.get(0),
                    CliCodes.IO_ERROR,
                    legacyMarkers.get(0),
                    "Delete legacy marker paths and recompile managed blocks, then rerun `bear pr-check --all`."
                );
            }

            List<String> orphanMarkers = options.strictOrphans()
                ? AllModeBlockDiscovery.computeOrphanMarkersRepoWide(options.repoRoot(), index)
                : AllModeBlockDiscovery.computeOrphanMarkersInManagedRoots(options.repoRoot(), selected);
            if (!orphanMarkers.isEmpty()) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_IO,
                    "pr-check: IO_ERROR: ORPHAN_MARKER: " + orphanMarkers.get(0),
                    CliCodes.IO_ERROR,
                    orphanMarkers.get(0),
                    "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
                );
            }
        } catch (IOException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_IO,
                "pr-check: IO_ERROR: ORPHAN_SCAN_FAILED: " + CliText.squash(e.getMessage()),
                CliCodes.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure repo paths are readable and rerun `bear pr-check --all`."
            );
        }

        Map<String, Target> targetsByRoot = new TreeMap<>();
        TreeMap<String, PrCheckCommandService.SharedPolicyDeltaComputation> sharedByRoot = new TreeMap<>();
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                continue;
            }
            sharedByRoot.computeIfAbsent(
                block.projectRoot(),
                root -> {
                    Path projectRoot = options.repoRoot().resolve(root).normalize();
                    targetsByRoot.computeIfAbsent(root, ignored -> TARGET_REGISTRY.resolve(projectRoot));
                    return PrCheckCommandService.computeSharedPolicyDeltasForProjectRoot(projectRoot, options.baseRef(), root);
                }
            );
        }
        List<BlockExecutionResult> blockResults = new ArrayList<>();
        List<PrGovernanceTelemetry.BlockSnapshot> blockSnapshots = new ArrayList<>();
        boolean telemetryAvailable = true;
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                blockResults.add(BearCli.skipBlock(block, "DISABLED"));
                continue;
            }
            PrCheckCommandService.SharedPolicyDeltaComputation sharedComputation = sharedByRoot.get(block.projectRoot());
            if (sharedComputation != null && sharedComputation.failureResult() != null) {
                blockResults.add(BearCli.toPrBlockResult(block, sharedComputation.failureResult()));
                telemetryAvailable = false;
                continue;
            }
            String mappingError = BearCli.validateIndexIrNameMatch(
                options.repoRoot().resolve(block.ir()).normalize(),
                block.name(),
                BearCli.indexLocator(block)
            );
            if (mappingError != null) {
                telemetryAvailable = false;
                blockResults.add(new BlockExecutionResult(
                    block.name(),
                    block.ir(),
                    block.projectRoot(),
                    BlockStatus.FAIL,
                    CliCodes.EXIT_VALIDATION,
                    "VALIDATION",
                    CliCodes.IR_VALIDATION,
                    "block.name",
                    mappingError,
                    "Set `block.name` to match index name and rerun `bear pr-check --all`.",
                    null,
                    null,
                    List.of()
                ));
                continue;
            }
            PrCheckResult prResult = PrCheckCommandService.executePrCheck(
                options.repoRoot(),
                block.ir(),
                options.baseRef(),
                false,
                block.projectRoot(),
                null,
                options.collectAll()
            );
            prResult = BearCli.enforcePrCheckExitEnvelope(prResult, block.ir());
            blockResults.add(BearCli.toPrBlockResult(block, prResult));
            if (prResult.governanceSnapshot() == null) {
                telemetryAvailable = false;
            } else {
                blockSnapshots.add(new PrGovernanceTelemetry.BlockSnapshot(
                    block.name(),
                    block.ir(),
                    prResult.governanceSnapshot().hasDeltas(),
                    prResult.governanceSnapshot().hasBoundaryExpansion(),
                    prResult.governanceSnapshot().classifications(),
                    prResult.governanceSnapshot().deltas(),
                    prResult.governanceSnapshot().governanceSignals()
                ));
            }
        }

        List<String> repoDeltaLines = new ArrayList<>();
        List<PrDelta> repoDeltas = new ArrayList<>();
        int aggregatedExitCode = CliCodes.EXIT_OK;
        int aggregatedRank = AllModeAggregation.severityRankPr(aggregatedExitCode);
        for (PrCheckCommandService.SharedPolicyDeltaComputation computation : sharedByRoot.values()) {
            if (computation.failureResult() != null) {
                int candidateRank = AllModeAggregation.severityRankPr(computation.failureResult().exitCode());
                if (candidateRank < aggregatedRank) {
                    aggregatedRank = candidateRank;
                    aggregatedExitCode = computation.failureResult().exitCode();
                }
                continue;
            }
            for (PrDelta delta : computation.deltas()) {
                repoDeltas.add(delta);
                repoDeltaLines.add(
                    "pr-delta: " + delta.clazz().label + ": " + delta.category().label + ": " + delta.change().label + ": " + delta.key()
                );
            }
            if (computation.hasBoundary()) {
                int candidateRank = AllModeAggregation.severityRankPr(CliCodes.EXIT_BOUNDARY_EXPANSION);
                if (candidateRank < aggregatedRank) {
                    aggregatedRank = candidateRank;
                    aggregatedExitCode = CliCodes.EXIT_BOUNDARY_EXPANSION;
                }
            }
        }
        repoDeltaLines.sort(String::compareTo);

        RepoAggregationResult summaryBase = AllModeAggregation.aggregatePrResults(blockResults);
        int finalExitCode = summaryBase.exitCode();
        if (AllModeAggregation.severityRankPr(aggregatedExitCode) < AllModeAggregation.severityRankPr(finalExitCode)) {
            finalExitCode = aggregatedExitCode;
        }
        RepoAggregationResult summary = new RepoAggregationResult(
            finalExitCode,
            summaryBase.total(),
            summaryBase.checked(),
            summaryBase.passed(),
            summaryBase.failed(),
            summaryBase.skipped(),
            summaryBase.failFastTriggered(),
            summaryBase.rootReachFailed(),
            summaryBase.rootTestFailed(),
            summaryBase.rootTestSkippedDueToReach(),
            List.copyOf(repoDeltaLines)
        );
        if (options.agent()) {
            ArrayList<AgentDiagnostics.AgentProblem> problems = new ArrayList<>();
            for (BlockExecutionResult blockResult : blockResults) {
                if (blockResult.problems() != null) {
                    problems.addAll(blockResult.problems());
                }
                if (blockResult.status() == BlockStatus.FAIL && (blockResult.problems() == null || blockResult.problems().isEmpty())) {
                    problems.add(AgentDiagnostics.problem(
                        blockResult.exitCode() == CliCodes.EXIT_BOUNDARY_EXPANSION || blockResult.exitCode() == CliCodes.EXIT_BOUNDARY_BYPASS
                            ? AgentDiagnostics.AgentCategory.GOVERNANCE
                            : AgentDiagnostics.AgentCategory.INFRA,
                        blockResult.blockCode() == null ? CliCodes.REPO_MULTI_BLOCK_FAILED : blockResult.blockCode(),
                        null,
                        blockResult.blockCode() == null ? CliCodes.REPO_MULTI_BLOCK_FAILED : blockResult.blockCode(),
                        AgentDiagnostics.AgentSeverity.ERROR,
                        blockResult.name(),
                        blockResult.blockPath(),
                        null,
                        blockResult.blockCode() == null ? CliCodes.REPO_MULTI_BLOCK_FAILED : blockResult.blockCode(),
                        blockResult.detail() == null ? "" : blockResult.detail(),
                        Map.of()
                    ));
                }
            }
            PrGovernanceTelemetry.Snapshot governanceSnapshot = telemetryAvailable
                ? PrGovernanceTelemetry.all(summary.exitCode(), repoDeltas, List.of(), blockSnapshots)
                : null;
            AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payloadForPrCheckAll(
                AgentCommandContext.forPrCheckAll(options),
                summary.exitCode(),
                problems,
                governanceSnapshot
            );
            out.println(AgentDiagnostics.toJson(payload));
            return summary.exitCode();
        }

        List<String> lines = AllModeRenderer.renderPrAllOutput(blockResults, summary);
        if (summary.exitCode() == CliCodes.EXIT_OK) {
            CliText.printLines(out, lines);
            return CliCodes.EXIT_OK;
        }
        CliText.printLines(err, lines);
        return BearCli.fail(
            err,
            summary.exitCode(),
            CliCodes.REPO_MULTI_BLOCK_FAILED,
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }
}

