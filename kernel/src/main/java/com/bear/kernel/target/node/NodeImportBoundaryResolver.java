package com.bear.kernel.target.node;

import java.nio.file.Path;
import java.util.Set;

public class NodeImportBoundaryResolver {

    /**
     * Resolves an import specifier and determines if it violates boundaries.
     */
    public BoundaryDecision resolve(Path importingFile, String specifier, Set<Path> governedRoots, Path projectRoot) {
        // 1. Check for bare specifier (e.g., "lodash")
        if (isBareSpecifier(specifier)) {
            return BoundaryDecision.fail("BARE_PACKAGE_IMPORT");
        }

        // 2. Check for alias specifier (e.g., "#utils")
        if (isAliasSpecifier(specifier)) {
            return BoundaryDecision.fail("ALIAS_IMPORT");
        }

        // 3. Check for URL-like specifier
        if (isUrlSpecifier(specifier)) {
            return BoundaryDecision.fail("URL_IMPORT");
        }

        // 4. Check for absolute path specifier (e.g., "/absolute/path")
        if (specifier.startsWith("/")) {
            return BoundaryDecision.fail("ABSOLUTE_PATH_IMPORT");
        }

        // 5. Resolve relative specifier lexically
        Path resolved = resolveRelative(importingFile, specifier);

        // 5. Check if resolved path is within BEAR-generated directory
        Path generatedDir = projectRoot.resolve("build/generated/bear");
        if (resolved.startsWith(generatedDir)) {
            return BoundaryDecision.allowed();
        }

        // 6. Check if resolved path is within same governed root
        Path importingRoot = findGovernedRoot(importingFile, governedRoots);
        if (importingRoot != null && resolved.startsWith(importingRoot)) {
            return BoundaryDecision.allowed();
        }

        // 7. Check _shared boundary rules
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        if (importingFile.startsWith(sharedRoot)) {
            // _shared must not import block roots — only _shared-internal or generated is allowed
            if (!resolved.startsWith(sharedRoot) && !resolved.startsWith(generatedDir)) {
                return BoundaryDecision.fail("SHARED_IMPORTS_BLOCK");
            }
            return BoundaryDecision.allowed();
        }
        // Block importing _shared is allowed
        if (resolved.startsWith(sharedRoot)) {
            return BoundaryDecision.allowed();
        }

        // 8. All other cases are boundary bypass
        return BoundaryDecision.fail("BOUNDARY_BYPASS");
    }

    private boolean isBareSpecifier(String specifier) {
        // Bare specifier: no leading . or / and not a URL
        return !specifier.startsWith(".") && !specifier.startsWith("/") && !specifier.startsWith("http") && !specifier.startsWith("#");
    }

    private boolean isAliasSpecifier(String specifier) {
        return specifier.startsWith("#") && !specifier.startsWith("#/");
    }

    private boolean isUrlSpecifier(String specifier) {
        return specifier.startsWith("http://") || specifier.startsWith("https://");
    }

    private Path resolveRelative(Path importingFile, String specifier) {
        Path parentDir = importingFile.getParent();
        if (parentDir == null) {
            parentDir = Path.of(".");
        }
        return parentDir.resolve(specifier).normalize();
    }

    private Path findGovernedRoot(Path file, Set<Path> governedRoots) {
        for (Path root : governedRoots) {
            if (file.startsWith(root)) {
                return root;
            }
        }
        return null;
    }
}
