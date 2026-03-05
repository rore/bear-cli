package com.bear.app;

import java.nio.file.Path;
import java.util.Locale;

final class RepoPathNormalizer {
    private RepoPathNormalizer() {
    }

    static String normalizePathForIdentity(Path path) {
        if (path == null) {
            return "";
        }
        return normalizePathForIdentity(path.toString());
    }

    static String normalizePathForIdentity(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String normalized = rawPath.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.endsWith("/") && !isRootSentinel(normalized)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static String normalizePathForPrefix(Path path) {
        if (path == null) {
            return "";
        }
        return normalizePathForPrefix(path.toString());
    }

    static String normalizePathForPrefix(String rawPath) {
        String normalized = normalizePathForIdentity(rawPath);
        if (normalized.isBlank() || isRootSentinel(normalized)) {
            return normalized;
        }
        return normalized + "/";
    }

    static boolean hasSegmentPrefix(String pathIdentity, String prefixIdentityOrPrefix) {
        String normalizedPath = normalizePathForIdentity(pathIdentity);
        String normalizedPrefix = normalizePathForPrefix(prefixIdentityOrPrefix);
        if (normalizedPath.isBlank() || normalizedPrefix.isBlank()) {
            return false;
        }
        String prefixIdentity = stripTrailingSlash(normalizedPrefix);
        if (normalizedPath.equals(prefixIdentity)) {
            return true;
        }
        return normalizedPath.startsWith(normalizedPrefix);
    }

    static String toRepoRelativeOrThrow(Path repoRoot, Path candidatePath) {
        if (repoRoot == null || candidatePath == null) {
            throw new IllegalArgumentException("repoRoot and candidatePath are required");
        }
        Path normalizedRoot = repoRoot.toAbsolutePath().normalize();
        Path normalizedCandidate = candidatePath.isAbsolute()
            ? candidatePath.normalize()
            : normalizedRoot.resolve(candidatePath).normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("path must be inside repo root");
        }
        Path relative = normalizedRoot.relativize(normalizedCandidate);
        String normalizedRelative = normalizePathForIdentity(relative.toString());
        if (normalizedRelative.isBlank() || normalizedRelative.startsWith("..")) {
            throw new IllegalArgumentException("path must be repo-relative");
        }
        return normalizedRelative;
    }

    static boolean equivalentPathMeaning(String left, String right, Path anchor) {
        return normalizePathForComparison(left, anchor).equals(normalizePathForComparison(right, anchor));
    }

    static String normalizePathForComparison(String rawPath, Path anchor) {
        if (rawPath == null || rawPath.isBlank()) {
            return "";
        }
        String normalized;
        try {
            Path candidate = Path.of(rawPath);
            Path resolved;
            if (candidate.isAbsolute()) {
                resolved = candidate.normalize();
            } else if (anchor != null) {
                resolved = anchor.resolve(candidate).normalize();
            } else {
                resolved = candidate.toAbsolutePath().normalize();
            }
            normalized = normalizePathForIdentity(resolved.toString());
        } catch (RuntimeException ex) {
            normalized = normalizePathForIdentity(rawPath);
        }
        if (isWindows()) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.endsWith("/") && !isRootSentinel(value)) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static boolean isRootSentinel(String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            return true;
        }
        return normalizedPath.matches("^[A-Za-z]:/$");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
