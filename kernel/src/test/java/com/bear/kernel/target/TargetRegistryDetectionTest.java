package com.bear.kernel.target;

import com.bear.kernel.target.jvm.JvmTarget;
import com.bear.kernel.target.node.NodeTarget;
import com.bear.kernel.target.python.PythonTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TargetRegistry detection behavior, specifically verifying:
 * - No silent JVM fallback when all detectors return NONE
 * - Single-target no-detector registry returns that target
 * - Multi-target no-detector registry throws TARGET_NOT_DETECTED
 * - Pin file overrides detection
 */
class TargetRegistryDetectionTest {

    // Stub detector that always returns NONE
    private static final TargetDetector NONE_DETECTOR = projectRoot -> DetectedTarget.none();

    @Test
    void allNoneDetectors_throwsTargetNotDetected(@TempDir Path tempDir) {
        // Registry with detectors that all return NONE
        TargetRegistry registry = new TargetRegistry(
            Map.of(
                TargetId.JVM, new JvmTarget(),
                TargetId.NODE, new NodeTarget(),
                TargetId.PYTHON, new PythonTarget()
            ),
            List.of(NONE_DETECTOR, NONE_DETECTOR, NONE_DETECTOR)
        );

        TargetResolutionException ex = assertThrows(
            TargetResolutionException.class,
            () -> registry.resolve(tempDir)
        );
        assertEquals("TARGET_NOT_DETECTED", ex.code());
        assertTrue(ex.remediation().contains(".bear/target.id"));
        assertTrue(ex.remediation().contains("build.gradle") || 
                   ex.remediation().contains("package.json") || 
                   ex.remediation().contains("pyproject.toml"));
    }

    @Test
    void singleTargetNoDetectorRegistry_returnsThatTarget(@TempDir Path tempDir) {
        // Single-target registry with no detectors — returns that single target
        TargetRegistry registry = new TargetRegistry(
            Map.of(TargetId.JVM, new JvmTarget())
        );

        Target result = registry.resolve(tempDir);
        assertInstanceOf(JvmTarget.class, result);
        assertEquals(TargetId.JVM, result.targetId());
    }

    @Test
    void singleTargetNoDetectorRegistry_worksWithNodeTarget(@TempDir Path tempDir) {
        // Single-target registry with Node — returns Node
        TargetRegistry registry = new TargetRegistry(
            Map.of(TargetId.NODE, new NodeTarget())
        );

        Target result = registry.resolve(tempDir);
        assertInstanceOf(NodeTarget.class, result);
        assertEquals(TargetId.NODE, result.targetId());
    }

    @Test
    void singleTargetNoDetectorRegistry_worksWithPythonTarget(@TempDir Path tempDir) {
        // Single-target registry with Python — returns Python
        TargetRegistry registry = new TargetRegistry(
            Map.of(TargetId.PYTHON, new PythonTarget())
        );

        Target result = registry.resolve(tempDir);
        assertInstanceOf(PythonTarget.class, result);
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void multiTargetNoDetectorRegistry_throwsTargetNotDetected(@TempDir Path tempDir) {
        // Multi-target registry with no detectors — throws TARGET_NOT_DETECTED
        TargetRegistry registry = new TargetRegistry(
            Map.of(
                TargetId.JVM, new JvmTarget(),
                TargetId.NODE, new NodeTarget()
            )
        );

        TargetResolutionException ex = assertThrows(
            TargetResolutionException.class,
            () -> registry.resolve(tempDir)
        );
        assertEquals("TARGET_NOT_DETECTED", ex.code());
        assertTrue(ex.remediation().contains(".bear/target.id"));
    }

    @Test
    void multiTargetNoDetectorRegistry_withJvm_throwsTargetNotDetected(@TempDir Path tempDir) {
        // Multi-target registry including JVM with no detectors — throws TARGET_NOT_DETECTED
        // (no silent JVM fallback even when JVM is registered)
        TargetRegistry registry = new TargetRegistry(
            Map.of(
                TargetId.JVM, new JvmTarget(),
                TargetId.PYTHON, new PythonTarget()
            )
        );

        TargetResolutionException ex = assertThrows(
            TargetResolutionException.class,
            () -> registry.resolve(tempDir)
        );
        assertEquals("TARGET_NOT_DETECTED", ex.code());
    }

    @Test
    void pinFileOverridesDetection_whenAllDetectorsReturnNone(@TempDir Path tempDir) throws IOException {
        // Pin file present, all detectors return NONE — pin file wins
        Path bearDir = tempDir.resolve(".bear");
        Files.createDirectories(bearDir);
        Files.writeString(bearDir.resolve("target.id"), "jvm");

        TargetRegistry registry = new TargetRegistry(
            Map.of(
                TargetId.JVM, new JvmTarget(),
                TargetId.NODE, new NodeTarget()
            ),
            List.of(NONE_DETECTOR, NONE_DETECTOR)
        );

        Target result = registry.resolve(tempDir);
        assertInstanceOf(JvmTarget.class, result);
        assertEquals(TargetId.JVM, result.targetId());
    }

    @Test
    void pinFileOverridesDetection_selectsNonJvmTarget(@TempDir Path tempDir) throws IOException {
        // Pin file specifies Python, detectors return NONE — Python is selected
        Path bearDir = tempDir.resolve(".bear");
        Files.createDirectories(bearDir);
        Files.writeString(bearDir.resolve("target.id"), "python");

        TargetRegistry registry = new TargetRegistry(
            Map.of(
                TargetId.JVM, new JvmTarget(),
                TargetId.PYTHON, new PythonTarget()
            ),
            List.of(NONE_DETECTOR)
        );

        Target result = registry.resolve(tempDir);
        assertInstanceOf(PythonTarget.class, result);
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void pinFileOverridesDetection_inNoDetectorRegistry(@TempDir Path tempDir) throws IOException {
        // Pin file present in multi-target no-detector registry — pin file wins
        Path bearDir = tempDir.resolve(".bear");
        Files.createDirectories(bearDir);
        Files.writeString(bearDir.resolve("target.id"), "node");

        TargetRegistry registry = new TargetRegistry(
            Map.of(
                TargetId.JVM, new JvmTarget(),
                TargetId.NODE, new NodeTarget(),
                TargetId.PYTHON, new PythonTarget()
            )
        );

        Target result = registry.resolve(tempDir);
        assertInstanceOf(NodeTarget.class, result);
        assertEquals(TargetId.NODE, result.targetId());
    }

    @Test
    void allNoneDetectors_withOnlyJvmRegistered_stillThrows(@TempDir Path tempDir) {
        // Even with only JVM registered, if detectors all return NONE, throw
        TargetRegistry registry = new TargetRegistry(
            Map.of(TargetId.JVM, new JvmTarget()),
            List.of(NONE_DETECTOR)
        );

        TargetResolutionException ex = assertThrows(
            TargetResolutionException.class,
            () -> registry.resolve(tempDir)
        );
        assertEquals("TARGET_NOT_DETECTED", ex.code());
    }
}
