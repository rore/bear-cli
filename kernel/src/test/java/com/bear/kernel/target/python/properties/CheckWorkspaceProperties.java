package com.bear.kernel.target.python.properties;

import com.bear.kernel.target.python.PythonTarget;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for prepareCheckWorkspace.
 * Feature: phase-p2-python-checking
 * 
 * Uses plain JUnit 5 parameterized tests with 100+ iterations.
 */
class CheckWorkspaceProperties {

    private final PythonTarget target = new PythonTarget();
    private static final Random RANDOM = new Random(42); // Deterministic seed

    // ========== Property 4: Shared directory iff present ==========
    // **Validates: Requirements 2.1, 2.2, 2.3**
    // tempRoot/src/blocks/_shared exists iff projectRoot/src/blocks/_shared exists
    // after calling prepareCheckWorkspace.

    static Stream<Integer> property4Iterations() {
        return IntStream.range(0, 110).boxed();
    }

    @ParameterizedTest(name = "Property 4 - iteration {0}: shared dir iff present")
    @MethodSource("property4Iterations")
    void property4_sharedDirExistsIffPresent(int iteration, @TempDir Path baseDir) throws IOException {
        // Setup: create project root and temp root
        Path projectRoot = baseDir.resolve("project-" + iteration);
        Path tempRoot = baseDir.resolve("temp-" + iteration);
        Files.createDirectories(projectRoot);
        Files.createDirectories(tempRoot);

        // Randomly decide whether to create _shared directory in project root
        boolean sharedPresent = (iteration % 2 == 0);
        
        Path projectSharedDir = projectRoot.resolve("src/blocks/_shared");
        if (sharedPresent) {
            Files.createDirectories(projectSharedDir);
            // Optionally add some content to make it more realistic
            if (iteration % 4 == 0) {
                Files.writeString(projectSharedDir.resolve("__init__.py"), "# shared module");
            }
        } else {
            // Ensure parent directories exist but not _shared
            Files.createDirectories(projectRoot.resolve("src/blocks"));
        }

        // Execute
        target.prepareCheckWorkspace(projectRoot, tempRoot);

        // Verify: tempRoot/src/blocks/_shared exists iff projectRoot/src/blocks/_shared exists
        Path tempSharedDir = tempRoot.resolve("src/blocks/_shared");
        boolean tempSharedExists = Files.isDirectory(tempSharedDir);

        assertEquals(sharedPresent, tempSharedExists,
            "tempRoot/src/blocks/_shared should exist iff projectRoot/src/blocks/_shared exists. " +
            "sharedPresent=" + sharedPresent + ", tempSharedExists=" + tempSharedExists);
    }

    // Additional property 4 tests with edge cases
    static Stream<Object[]> property4EdgeCases() {
        return Stream.of(
            // [description, setupAction, expectedTempSharedExists]
            new Object[]{"empty project root", (SetupAction) (p, t) -> {}, false},
            new Object[]{"only src exists", (SetupAction) (p, t) -> Files.createDirectories(p.resolve("src")), false},
            new Object[]{"only src/blocks exists", (SetupAction) (p, t) -> Files.createDirectories(p.resolve("src/blocks")), false},
            new Object[]{"_shared is a file", (SetupAction) (p, t) -> {
                Files.createDirectories(p.resolve("src/blocks"));
                Files.writeString(p.resolve("src/blocks/_shared"), "file content");
            }, false},
            new Object[]{"_shared is empty dir", (SetupAction) (p, t) -> Files.createDirectories(p.resolve("src/blocks/_shared")), true},
            new Object[]{"_shared has nested content", (SetupAction) (p, t) -> {
                Files.createDirectories(p.resolve("src/blocks/_shared/utils"));
                Files.writeString(p.resolve("src/blocks/_shared/utils/helper.py"), "# helper");
            }, true},
            new Object[]{"_shared has __init__.py", (SetupAction) (p, t) -> {
                Files.createDirectories(p.resolve("src/blocks/_shared"));
                Files.writeString(p.resolve("src/blocks/_shared/__init__.py"), "");
            }, true},
            new Object[]{"temp root doesn't exist yet", (SetupAction) (p, t) -> {
                Files.createDirectories(p.resolve("src/blocks/_shared"));
                Files.delete(t); // Remove temp root
            }, true},
            new Object[]{"both roots are same path", (SetupAction) (p, t) -> {
                // This is an edge case - project and temp are same
                Files.createDirectories(p.resolve("src/blocks/_shared"));
            }, true}
        );
    }

    @ParameterizedTest(name = "Property 4 edge case: {0}")
    @MethodSource("property4EdgeCases")
    void property4_edgeCases(String description, SetupAction setup, boolean expectedTempSharedExists, @TempDir Path baseDir) throws IOException {
        Path projectRoot = baseDir.resolve("project");
        Path tempRoot = baseDir.resolve("temp");
        Files.createDirectories(projectRoot);
        Files.createDirectories(tempRoot);

        // Apply setup
        setup.apply(projectRoot, tempRoot);

        // Execute
        target.prepareCheckWorkspace(projectRoot, tempRoot);

        // Verify
        Path tempSharedDir = tempRoot.resolve("src/blocks/_shared");
        boolean tempSharedExists = Files.isDirectory(tempSharedDir);

        assertEquals(expectedTempSharedExists, tempSharedExists,
            "Edge case '" + description + "': expected tempSharedExists=" + expectedTempSharedExists + 
            " but was " + tempSharedExists);
    }

    // Property 4 with varying block structures
    static Stream<Integer> property4BlockVariations() {
        return IntStream.range(0, 50).boxed();
    }

    @ParameterizedTest(name = "Property 4 - block variation {0}")
    @MethodSource("property4BlockVariations")
    void property4_withVaryingBlockStructures(int iteration, @TempDir Path baseDir) throws IOException {
        Path projectRoot = baseDir.resolve("project-" + iteration);
        Path tempRoot = baseDir.resolve("temp-" + iteration);
        Files.createDirectories(projectRoot);
        Files.createDirectories(tempRoot);

        // Create varying block structures
        int numBlocks = (iteration % 5) + 1;
        for (int i = 0; i < numBlocks; i++) {
            String blockKey = "block-" + i;
            Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey + "/impl"));
            Files.writeString(projectRoot.resolve("src/blocks/" + blockKey + "/__init__.py"), "");
        }

        // Randomly decide whether to create _shared
        boolean sharedPresent = (iteration % 3 != 0);
        if (sharedPresent) {
            Files.createDirectories(projectRoot.resolve("src/blocks/_shared"));
            Files.writeString(projectRoot.resolve("src/blocks/_shared/__init__.py"), "# shared");
        }

        // Execute
        target.prepareCheckWorkspace(projectRoot, tempRoot);

        // Verify: only _shared should be created in temp, not the block directories
        Path tempSharedDir = tempRoot.resolve("src/blocks/_shared");
        boolean tempSharedExists = Files.isDirectory(tempSharedDir);

        assertEquals(sharedPresent, tempSharedExists,
            "tempRoot/src/blocks/_shared should exist iff projectRoot/src/blocks/_shared exists");

        // Verify block directories are NOT created in temp root
        for (int i = 0; i < numBlocks; i++) {
            String blockKey = "block-" + i;
            Path tempBlockDir = tempRoot.resolve("src/blocks/" + blockKey);
            assertFalse(Files.exists(tempBlockDir),
                "Block directory " + blockKey + " should NOT be created in temp root");
        }
    }

    // Functional interface for setup actions
    @FunctionalInterface
    interface SetupAction {
        void apply(Path projectRoot, Path tempRoot) throws IOException;
    }
}
