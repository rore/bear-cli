package com.bear.kernel.target.python.properties;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.DetectionStatus;
import com.bear.kernel.target.Target;
import com.bear.kernel.target.TargetId;
import com.bear.kernel.target.TargetRegistry;
import com.bear.kernel.target.python.PythonTarget;
import com.bear.kernel.target.python.PythonTargetDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for PythonTarget detection (implemented as parameterized JUnit 5 tests).
 * Feature: phase-p-python-scan-only
 */
class PythonDetectionProperties {

    private final PythonTargetDetector detector = new PythonTargetDetector();

    /**
     * Property 1: Valid Python project structure → SUPPORTED, targetId=PYTHON.
     * **Validates: Requirements 1.1 (Python Project Detection)**
     */
    @Test
    void validPythonProjectStructureReturnsSupported(@TempDir Path projectRoot) throws IOException {
        createValidPythonProject(projectRoot);
        DetectedTarget result = detector.detect(projectRoot);
        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    /**
     * Property 1 (variant): Multiple valid project shapes all return SUPPORTED.
     * **Validates: Requirements 1.1 (Python Project Detection)**
     */
    @Test
    void validPythonProjectWithPoetryReturnsSupported(@TempDir Path projectRoot) throws IOException {
        createPyprojectToml(projectRoot);
        Files.createFile(projectRoot.resolve("poetry.lock"));
        Files.createFile(projectRoot.resolve("mypy.ini"));
        Files.createDirectories(projectRoot.resolve("src/blocks/test-block"));
        Files.createFile(projectRoot.resolve("src/blocks/test-block/__init__.py"));

        DetectedTarget result = detector.detect(projectRoot);
        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    /**
     * Property 1 (variant): Valid project with mypy in pyproject.toml returns SUPPORTED.
     * **Validates: Requirements 1.1 (Python Project Detection)**
     */
    @Test
    void validPythonProjectWithMypyInTomlReturnsSupported(@TempDir Path projectRoot) throws IOException {
        createPyprojectToml(projectRoot);
        Files.createFile(projectRoot.resolve("uv.lock"));
        Files.createDirectories(projectRoot.resolve("src/blocks/test-block"));
        Files.createFile(projectRoot.resolve("src/blocks/test-block/__init__.py"));

        DetectedTarget result = detector.detect(projectRoot);
        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    /**
     * Property 2: TargetRegistry.resolve() on valid Python project → PythonTarget instance.
     * **Validates: Requirements 1.2 (Target Registry Integration)**
     */
    @Test
    void targetRegistryResolvesPythonTarget(@TempDir Path projectRoot) throws IOException {
        createValidPythonProject(projectRoot);
        Target target = TargetRegistry.defaultRegistry().resolve(projectRoot);
        assertInstanceOf(PythonTarget.class, target);
        assertEquals(TargetId.PYTHON, target.targetId());
    }

    /**
     * Property 2 (determinism): TargetRegistry.resolve() called twice → same result.
     * **Validates: Requirements 1.2 (Target Registry Integration)**
     */
    @Test
    void targetRegistryResolutionIsDeterministic(@TempDir Path projectRoot) throws IOException {
        createValidPythonProject(projectRoot);
        Target target1 = TargetRegistry.defaultRegistry().resolve(projectRoot);
        Target target2 = TargetRegistry.defaultRegistry().resolve(projectRoot);
        assertEquals(target1.getClass(), target2.getClass());
        assertEquals(target1.targetId(), target2.targetId());
    }

    private void createValidPythonProject(Path dir) throws IOException {
        createPyprojectToml(dir);
        Files.createFile(dir.resolve("uv.lock"));
        Files.createFile(dir.resolve("mypy.ini"));
        Files.createDirectories(dir.resolve("src/blocks/test-block"));
        Files.createFile(dir.resolve("src/blocks/test-block/__init__.py"));
    }

    private void createPyprojectToml(Path dir) throws IOException {
        String content = """
            [build-system]
            requires = ["hatchling"]
            build-backend = "hatchling.build"
            
            [project]
            name = "test-project"
            version = "0.1.0"
            
            [tool.mypy]
            strict = true
            """;
        Files.writeString(dir.resolve("pyproject.toml"), content);
    }
}
