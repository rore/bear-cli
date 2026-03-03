package com.bear.app;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class AllModeIndexPreflight {
    private static final String MISSING_INDEX_LINE =
        "index: VALIDATION_ERROR: INDEX_REQUIRED_MISSING: bear.blocks.yaml: project=.";
    private static final String MISSING_INDEX_REMEDIATION =
        "Create bear.blocks.yaml or run non---all command";

    private AllModeIndexPreflight() {
    }

    static Integer failIfMissing(Path blocksPath, PrintStream err) {
        if (Files.isRegularFile(blocksPath)) {
            return null;
        }
        return BearCli.failWithLegacy(
            err,
            CliCodes.EXIT_VALIDATION,
            MISSING_INDEX_LINE,
            CliCodes.INDEX_REQUIRED_MISSING,
            "bear.blocks.yaml",
            MISSING_INDEX_REMEDIATION
        );
    }
}
