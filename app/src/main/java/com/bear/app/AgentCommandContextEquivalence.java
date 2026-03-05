package com.bear.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class AgentCommandContextEquivalence {
    private AgentCommandContextEquivalence() {
    }

    static ComparisonResult compare(AgentCommandContext expected, AgentCommandContext actual) {
        ArrayList<String> differences = new ArrayList<>();
        if (!Objects.equals(expected.command(), actual.command())) {
            differences.add("command mismatch");
        }
        if (!Objects.equals(expected.mode(), actual.mode())) {
            differences.add("mode mismatch");
        }
        if (!Objects.equals(expected.baseRef(), actual.baseRef())) {
            differences.add("baseRef mismatch");
        }
        if (expected.collectAll() != actual.collectAll()) {
            differences.add("collectAll mismatch");
        }
        if (expected.agent() != actual.agent()) {
            differences.add("agent mismatch");
        }
        if (expected.strictHygiene() != actual.strictHygiene()) {
            differences.add("strictHygiene mismatch");
        }
        if (expected.strictOrphans() != actual.strictOrphans()) {
            differences.add("strictOrphans mismatch");
        }
        if (expected.failFast() != actual.failFast()) {
            differences.add("failFast mismatch");
        }
        if (!Objects.equals(expected.onlyNames(), actual.onlyNames())) {
            differences.add("onlyNames mismatch");
        }

        if (!equivalentProjectPath(expected.projectPath(), actual.projectPath())) {
            differences.add("projectPath mismatch");
        }

        Path pathAnchor = pathAnchor(expected.projectPath(), actual.projectPath());
        if (!equivalentPathMeaning(expected.irPath(), actual.irPath(), pathAnchor)) {
            differences.add("irPath mismatch");
        }
        if (!equivalentPathMeaning(expected.indexPath(), actual.indexPath(), pathAnchor)) {
            differences.add("indexPath mismatch");
        }
        if (!equivalentPathMeaning(expected.blocksPath(), actual.blocksPath(), pathAnchor)) {
            differences.add("blocksPath mismatch");
        }

        if (!differences.isEmpty() && expected.blocksPath() != null && actual.blocksPath() != null) {
            if (!isRepoRelativeSlashPath(actual.blocksPath())) {
                differences.add("blocksPath must be repo-relative and use '/'");
            }
        }

        return new ComparisonResult(differences.isEmpty(), List.copyOf(differences));
    }

    static boolean equivalent(AgentCommandContext expected, AgentCommandContext actual) {
        return compare(expected, actual).equivalent();
    }

    static boolean isRepoRelativeSlashPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = RepoPathNormalizer.normalizePathForIdentity(path);
        if (normalized.contains("\\")) {
            return false;
        }
        if (normalized.startsWith("/") || normalized.startsWith("..")) {
            return false;
        }
        return !normalized.matches("^[A-Za-z]:/.*");
    }

    private static boolean equivalentProjectPath(String expected, String actual) {
        if (isBlank(expected) && isBlank(actual)) {
            return true;
        }
        return RepoPathNormalizer.equivalentPathMeaning(expected, actual, null);
    }

    private static boolean equivalentPathMeaning(String expected, String actual, Path anchor) {
        if (isBlank(expected) && isBlank(actual)) {
            return true;
        }
        if (isBlank(expected) || isBlank(actual)) {
            return false;
        }
        return RepoPathNormalizer.equivalentPathMeaning(expected, actual, anchor);
    }

    private static Path pathAnchor(String expectedProjectPath, String actualProjectPath) {
        String anchor = !isBlank(expectedProjectPath) ? expectedProjectPath : actualProjectPath;
        if (isBlank(anchor)) {
            return null;
        }
        try {
            return Path.of(anchor).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record ComparisonResult(boolean equivalent, List<String> differences) {
        String summary() {
            if (equivalent) {
                return "equivalent";
            }
            return String.join("; ", differences);
        }
    }
}
