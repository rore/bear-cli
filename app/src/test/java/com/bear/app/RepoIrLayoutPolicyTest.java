package com.bear.app;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoIrLayoutPolicyTest {
    private static final Pattern INDEX_IR_LINE = Pattern.compile("(?m)^\\s*ir:\\s*['\"]?([^'\"\\s]+)['\"]?\\s*$");

    @Test
    void trackedBearYamlFilesMustBeUnderBearIrForThisRepositoryPolicy() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        List<String> trackedBearYaml = gitTrackedBearYaml(repoRoot);
        for (String relPath : trackedBearYaml) {
            assertTrue(
                relPath.startsWith("bear-ir/"),
                "Repository policy requires tracked *.bear.yaml under bear-ir/: " + relPath
            );
        }
    }

    @Test
    void rootIndexReferencedIrPathsMustExistWhenIndexIsPresent() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path index = repoRoot.resolve("bear.blocks.yaml");
        if (!Files.isRegularFile(index)) {
            return;
        }

        String content = Files.readString(index, StandardCharsets.UTF_8);
        Matcher matcher = INDEX_IR_LINE.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
            String relPath = matcher.group(1);
            Path resolved = repoRoot.resolve(relPath).normalize();
            assertTrue(
                resolved.startsWith(repoRoot),
                "Index ir path must stay under repo root: " + relPath
            );
            assertTrue(
                Files.isRegularFile(resolved),
                "Index ir path must exist: " + relPath
            );
        }

        assertTrue(count > 0, "bear.blocks.yaml must contain at least one ir: path");
    }


    @Test
    void ciIndexIsValidV1WhenPresent() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path ciIndex = repoRoot.resolve(".ci/bear.blocks.yaml");
        if (!Files.isRegularFile(ciIndex)) {
            return;
        }

        BlockIndex index = new BlockIndexParser().parse(repoRoot, ciIndex, true);
        assertEquals("v1", index.version());
        assertTrue(!index.blocks().isEmpty(), ".ci/bear.blocks.yaml must contain at least one block entry");

        for (BlockIndexEntry entry : index.blocks()) {
            Path irPath = repoRoot.resolve(entry.ir()).normalize();
            assertTrue(irPath.startsWith(repoRoot), "index ir path must stay under repo root: " + entry.ir());
            assertTrue(Files.isRegularFile(irPath), "index ir path must exist: " + entry.ir());
        }
    }
    private static List<String> gitTrackedBearYaml(Path repoRoot) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "-C", repoRoot.toString(), "ls-files", "--", "*.bear.yaml");
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


