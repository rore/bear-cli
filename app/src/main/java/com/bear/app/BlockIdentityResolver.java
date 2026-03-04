package com.bear.app;

import com.bear.kernel.identity.BlockIdentityCanonicalizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class BlockIdentityResolver {
    private static final String DEFAULT_INDEX_FILE = "bear.blocks.yaml";

    private BlockIdentityResolver() {
    }

    static BlockIdentityResolution resolveSingleCommandIdentity(
        Path irFile,
        Path projectRoot,
        String irBlockName
    ) throws IOException, BlockIndexValidationException, BlockIdentityResolutionException {
        return resolveSingleCommandIdentity(irFile, projectRoot, irBlockName, null);
    }

    static BlockIdentityResolution resolveSingleCommandIdentity(
        Path irFile,
        Path projectRoot,
        String irBlockName,
        Path explicitIndexPath
    ) throws IOException, BlockIndexValidationException, BlockIdentityResolutionException {
        Path projectRootAbsolute = projectRoot.toAbsolutePath().normalize();
        Path irAbsolute = irFile.toAbsolutePath().normalize();

        if (explicitIndexPath != null) {
            Path indexAbsolute = explicitIndexPath.toAbsolutePath().normalize();
            Path repoRoot = indexAbsolute.getParent();
            if (repoRoot == null) {
                throw new BlockIdentityResolutionException(
                    "index: VALIDATION_ERROR: INDEX_INVALID: index path has no parent",
                    "bear.blocks.yaml",
                    "Pass a valid `--index` path and rerun the command."
                );
            }
            BlockIndex index = new BlockIndexParser().parse(repoRoot, indexAbsolute, true);
            String irRelative = toRepoRelativeOrNull(repoRoot, irAbsolute);
            if (irRelative == null) {
                String line = "index: VALIDATION_ERROR: INDEX_MEMBERSHIP_MISMATCH: ir="
                    + normalizePathForDisplay(irAbsolute)
                    + ", projectRoot="
                    + normalizePathForDisplay(projectRootAbsolute);
                throw new BlockIdentityResolutionException(
                    line,
                    "bear.blocks.yaml",
                    "Ensure the IR path is declared in the index under the same `(ir, projectRoot)` tuple, then rerun the command."
                );
            }
            return resolveFromIndexTuple(index, repoRoot, irRelative, projectRootAbsolute, irBlockName);
        }

        Path repoRoot = findNearestIndexRoot(projectRootAbsolute);
        if (repoRoot == null) {
            return BlockIdentityResolution.singleIrFallback(canonicalize(irBlockName));
        }

        Path indexPath = repoRoot.resolve(DEFAULT_INDEX_FILE);
        BlockIndex index = new BlockIndexParser().parse(repoRoot, indexPath);
        String irRelative = toRepoRelativeOrNull(repoRoot, irAbsolute);
        if (irRelative == null) {
            return BlockIdentityResolution.singleIrFallback(canonicalize(irBlockName));
        }
        try {
            return resolveFromIndexTuple(index, repoRoot, irRelative, projectRootAbsolute, irBlockName);
        } catch (BlockIdentityResolutionException e) {
            if (e.line() != null && e.line().contains("INDEX_MEMBERSHIP_MISMATCH")) {
                return BlockIdentityResolution.singleIrFallback(canonicalize(irBlockName));
            }
            throw e;
        }
    }

    static BlockIdentityResolution resolveIndexIdentity(
        String indexName,
        String indexLocator,
        String irBlockName
    ) throws BlockIdentityResolutionException {
        String indexCanonical = canonicalize(indexName);
        String irCanonical = canonicalize(irBlockName);
        if (!indexCanonical.equals(irCanonical)) {
            String line = "schema at block.name: INVALID_VALUE: canonical block identity mismatch: index.name="
                + indexName
                + " ("
                + indexLocator
                + ") -> "
                + indexCanonical
                + ", block.name="
                + irBlockName
                + " -> "
                + irCanonical;
            throw new BlockIdentityResolutionException(
                line,
                "block.name",
                "Align `block.name` with index identity intent and rerun the command."
            );
        }
        return BlockIdentityResolution.indexResolved(indexCanonical, indexName, indexLocator);
    }

    static String canonicalize(String raw) {
        return BlockIdentityCanonicalizer.canonicalizeBlockKey(raw);
    }

    static String formatIndexLocator(BlockIndexEntry entry) {
        return "bear.blocks.yaml:name=" + entry.name()
            + ",ir=" + entry.ir()
            + ",projectRoot=" + entry.projectRoot();
    }

    private static BlockIdentityResolution resolveFromIndexTuple(
        BlockIndex index,
        Path repoRoot,
        String irRelative,
        Path projectRootAbsolute,
        String irBlockName
    ) throws BlockIdentityResolutionException {
        List<BlockIndexEntry> matches = new ArrayList<>();
        for (BlockIndexEntry entry : index.blocks()) {
            if (!entry.ir().equals(irRelative)) {
                continue;
            }
            Path entryProjectRoot = repoRoot.resolve(entry.projectRoot()).normalize();
            if (!entryProjectRoot.equals(projectRootAbsolute)) {
                continue;
            }
            matches.add(entry);
        }

        if (matches.isEmpty()) {
            String tupleProjectRoot = normalizePathForDisplay(projectRootAbsolute);
            String line = "index: VALIDATION_ERROR: INDEX_MEMBERSHIP_MISMATCH: ir="
                + irRelative
                + ", projectRoot="
                + tupleProjectRoot;
            throw new BlockIdentityResolutionException(
                line,
                "bear.blocks.yaml",
                "Ensure `bear.blocks.yaml` contains exactly one matching `(ir, projectRoot)` tuple and rerun the command."
            );
        }
        if (matches.size() > 1) {
            String tupleProjectRoot = normalizePathForDisplay(projectRootAbsolute);
            String line = "index: VALIDATION_ERROR: AMBIGUOUS_INDEX_ENTRIES: ir="
                + irRelative
                + ", projectRoot="
                + tupleProjectRoot;
            throw new BlockIdentityResolutionException(
                line,
                "bear.blocks.yaml",
                "Deduplicate `bear.blocks.yaml` so exactly one entry matches `(ir, projectRoot)`, then rerun the command."
            );
        }

        BlockIndexEntry match = matches.get(0);
        String indexLocator = formatIndexLocator(match);
        return resolveIndexIdentity(match.name(), indexLocator, irBlockName);
    }

    private static Path findNearestIndexRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.isRegularFile(current.resolve(DEFAULT_INDEX_FILE))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String toRepoRelativeOrNull(Path repoRoot, Path path) {
        if (!path.startsWith(repoRoot)) {
            return null;
        }
        Path relative = repoRoot.relativize(path).normalize();
        String value = relative.toString().replace('\\', '/');
        return value.isBlank() ? null : value;
    }

    private static String normalizePathForDisplay(Path path) {
        return path.toString().replace('\\', '/');
    }
}

record BlockIdentityResolution(
    String blockKey,
    boolean indexResolved,
    String indexName,
    String indexLocator
) {
    static BlockIdentityResolution singleIrFallback(String blockKey) {
        return new BlockIdentityResolution(blockKey, false, null, null);
    }

    static BlockIdentityResolution indexResolved(String blockKey, String indexName, String indexLocator) {
        return new BlockIdentityResolution(blockKey, true, indexName, indexLocator);
    }
}

final class BlockIdentityResolutionException extends Exception {
    private final String line;
    private final String path;
    private final String remediation;

    BlockIdentityResolutionException(String line, String path, String remediation) {
        super(line);
        this.line = line;
        this.path = path;
        this.remediation = remediation;
    }

    String line() {
        return line;
    }

    String path() {
        return path;
    }

    String remediation() {
        return remediation;
    }
}
