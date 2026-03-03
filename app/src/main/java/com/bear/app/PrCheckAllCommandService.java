package com.bear.app;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

final class PrCheckAllCommandService {
    private PrCheckAllCommandService() {
    }

    static int runPrCheckAll(String[] args, PrintStream out, PrintStream err) {
        AllPrCheckOptions options = AllModeOptionParser.parseAllPrCheckOptions(args, err);
        if (options == null) {
            return CliCodes.EXIT_USAGE;
        }
        Integer missingIndexExit = AllModeIndexPreflight.failIfMissing(options.blocksPath(), err);
        if (missingIndexExit != null) {
            return missingIndexExit;
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

        TreeMap<String, PrCheckCommandService.SharedPolicyDeltaComputation> sharedByRoot = new TreeMap<>();
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                continue;
            }
            sharedByRoot.computeIfAbsent(
                block.projectRoot(),
                root -> PrCheckCommandService.computeSharedPolicyDeltasForProjectRoot(
                    options.repoRoot().resolve(root).normalize(),
                    options.baseRef(),
                    root
                )
            );
        }
        List<BlockExecutionResult> blockResults = new ArrayList<>();
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                blockResults.add(BearCli.skipBlock(block, "DISABLED"));
                continue;
            }
            PrCheckCommandService.SharedPolicyDeltaComputation sharedComputation = sharedByRoot.get(block.projectRoot());
            if (sharedComputation != null && sharedComputation.failureResult() != null) {
                blockResults.add(BearCli.toPrBlockResult(block, sharedComputation.failureResult()));
                continue;
            }
            String mappingError = BearCli.validateIndexIrNameMatch(
                options.repoRoot().resolve(block.ir()).normalize(),
                block.name(),
                BearCli.indexLocator(block)
            );
            if (mappingError != null) {
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
                    "Set `block.name` to match index `name` and rerun `bear pr-check --all`.",
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
                block.projectRoot()
            );
            prResult = BearCli.enforcePrCheckExitEnvelope(prResult, block.ir());
            blockResults.add(BearCli.toPrBlockResult(block, prResult));
        }

        List<String> repoDeltaLines = new ArrayList<>();
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
