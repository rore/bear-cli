package com.bear.app;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RepoArtifactPolicyTest {
    private static final Pattern TRACKED_BUILD_ARTIFACT_DIR = Pattern.compile("^build[0-9]+/.*");
    private static final Pattern STALE_BUILD_PATH_TOKEN = Pattern.compile("build[0-9]+[\\\\/]");

    @Test
    void trackedBuildArtifactDirectoriesAreForbidden() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        List<String> trackedFiles = gitTrackedFiles(repoRoot);
        for (String relPath : trackedFiles) {
            assertFalse(
                TRACKED_BUILD_ARTIFACT_DIR.matcher(relPath).matches(),
                "Tracked stale build artifact path is forbidden: " + relPath
            );
        }
    }

    @Test
    void sourceAndDocsMustNotReferenceStaleBuildPathTokens() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        List<String> trackedFiles = gitTrackedFiles(repoRoot);
        for (String relPath : trackedFiles) {
            if (!isPolicyScannedTextPath(relPath)) {
                continue;
            }
            String content = Files.readString(repoRoot.resolve(relPath), StandardCharsets.UTF_8);
            assertFalse(
                STALE_BUILD_PATH_TOKEN.matcher(content).find(),
                "Stale build path token forbidden in tracked file: " + relPath
            );
        }
    }

    private static boolean isPolicyScannedTextPath(String relPath) {
        if (relPath.startsWith("build/")) {
            return false;
        }
        return relPath.endsWith(".java")
            || relPath.endsWith(".md")
            || relPath.endsWith(".txt")
            || relPath.endsWith(".yaml")
            || relPath.endsWith(".yml")
            || relPath.endsWith(".ps1")
            || relPath.endsWith(".sh")
            || relPath.endsWith(".gradle");
    }

    private static List<String> gitTrackedFiles(Path repoRoot) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "-C", repoRoot.toString(), "ls-files");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (var input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exit = process.waitFor();
        assertEquals(0, exit, "git ls-files failed:\n" + output);

        List<String> results = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                results.add(trimmed.replace('\\', '/'));
            }
        }
        return results;
    }
}

