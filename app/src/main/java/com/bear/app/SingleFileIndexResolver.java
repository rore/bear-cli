package com.bear.app;

import java.nio.file.Files;
import java.nio.file.Path;

final class SingleFileIndexResolver {
    private static final String DEFAULT_INDEX_FILE = "bear.blocks.yaml";

    private SingleFileIndexResolver() {
    }

    static Path resolveForBlockPorts(Path projectRoot, Path explicitIndexPath, String commandName)
        throws BlockIdentityResolutionException {
        Path indexAbsolute = explicitIndexPath != null
            ? explicitIndexPath.toAbsolutePath().normalize()
            : projectRoot.resolve(DEFAULT_INDEX_FILE).toAbsolutePath().normalize();
        if (explicitIndexPath == null && !Files.isRegularFile(indexAbsolute)) {
            String line = "index: VALIDATION_ERROR: BLOCK_PORT_INDEX_REQUIRED: missing inferred index at --project/bear.blocks.yaml";
            throw new BlockIdentityResolutionException(
                line,
                DEFAULT_INDEX_FILE,
                "Create `bear.blocks.yaml` under `--project` or pass `--index <path-to-bear.blocks.yaml>`, then rerun `bear " + commandName + "`."
            );
        }
        if (indexAbsolute.getParent() == null) {
            String line = "index: VALIDATION_ERROR: BLOCK_PORT_INDEX_REQUIRED: invalid --index path";
            throw new BlockIdentityResolutionException(
                line,
                DEFAULT_INDEX_FILE,
                "Pass a valid `--index` path and rerun `bear " + commandName + "`."
            );
        }
        return indexAbsolute;
    }
}
