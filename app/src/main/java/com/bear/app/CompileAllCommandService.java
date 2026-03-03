package com.bear.app;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

final class CompileAllCommandService {
    private CompileAllCommandService() {
    }

    static int runCompileAll(String[] args, PrintStream out, PrintStream err) {
        AllCompileOptions options = AllModeOptionParser.parseAllCompileOptions(args, err);
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
                "Fix `bear.blocks.yaml` and rerun `bear compile --all`."
            );
        } catch (IOException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_IO,
                "io: IO_ERROR: " + CliText.squash(e.getMessage()),
                CliCodes.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure `bear.blocks.yaml` is readable and rerun `bear compile --all`."
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
                    "compile: IO_ERROR: LEGACY_SURFACE_MARKER: " + legacyMarkers.get(0),
                    CliCodes.IO_ERROR,
                    legacyMarkers.get(0),
                    "Delete legacy marker paths and recompile managed blocks, then rerun `bear compile --all`."
                );
            }

            List<String> orphanMarkers = options.strictOrphans()
                ? AllModeBlockDiscovery.computeOrphanMarkersRepoWide(options.repoRoot(), index)
                : AllModeBlockDiscovery.computeOrphanMarkersInManagedRoots(options.repoRoot(), selected);
            if (!orphanMarkers.isEmpty()) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_IO,
                    "compile: IO_ERROR: ORPHAN_MARKER: " + orphanMarkers.get(0),
                    CliCodes.IO_ERROR,
                    orphanMarkers.get(0),
                    "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
                );
            }
        } catch (IOException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_IO,
                "compile: IO_ERROR: ORPHAN_SCAN_FAILED: " + CliText.squash(e.getMessage()),
                CliCodes.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure repo paths are readable and rerun `bear compile --all`."
            );
        }

        List<BlockExecutionResult> blockResults = new ArrayList<>();
        boolean failed = false;
        boolean failFastTriggered = false;
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                blockResults.add(BearCli.skipBlock(block, "DISABLED"));
                continue;
            }
            if (options.failFast() && failed) {
                failFastTriggered = true;
                blockResults.add(BearCli.skipBlock(block, "FAIL_FAST_ABORT"));
                continue;
            }

            CompileResult compileResult = BearCli.executeCompile(
                options.repoRoot().resolve(block.ir()).normalize(),
                options.repoRoot().resolve(block.projectRoot()).normalize(),
                block.name(),
                BearCli.indexLocator(block)
            );
            BlockExecutionResult blockResult = BearCli.toCompileBlockResult(block, compileResult);
            blockResults.add(blockResult);
            if (blockResult.status() == BlockStatus.FAIL) {
                failed = true;
            }
        }

        RepoAggregationResult summary = AllModeAggregation.aggregateCompileResults(blockResults, failFastTriggered);
        List<String> lines = AllModeRenderer.renderCompileAllOutput(blockResults, summary);
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
