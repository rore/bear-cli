package com.bear.kernel;

import com.bear.kernel.target.*;
import com.bear.kernel.target.jvm.JvmTarget;
import com.bear.kernel.target.jvm.JvmTargetDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TargetRegistryResolveTest {

    @Test
    void singleJvmProjectResolvesJvmTarget(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("build.gradle"), "apply plugin: 'java'");
        TargetRegistry registry = new TargetRegistry(
            Map.of(TargetId.JVM, new JvmTarget()),
            List.of(new JvmTargetDetector())
        );
        Target result = registry.resolve(tempDir);
        assertInstanceOf(JvmTarget.class, result);
        assertEquals(TargetId.JVM, result.targetId());
    }

    @Test
    void pinFileOverridesDetection(@TempDir Path tempDir) throws IOException {
        Path bearDir = tempDir.resolve(".bear");
        Files.createDirectories(bearDir);
        Files.writeString(bearDir.resolve("target.id"), "jvm");
        // No build.gradle present
        TargetRegistry registry = new TargetRegistry(
            Map.of(TargetId.JVM, new JvmTarget()),
            List.of(new JvmTargetDetector())
        );
        Target result = registry.resolve(tempDir);
        assertInstanceOf(JvmTarget.class, result);
    }

    @Test
    void noMatchThrowsTargetNotDetected(@TempDir Path tempDir) {
        // Empty dir, no build.gradle, no pin — throws TARGET_NOT_DETECTED (no silent JVM fallback)
        TargetRegistry registry = new TargetRegistry(
            Map.of(TargetId.JVM, new JvmTarget()),
            List.of(new JvmTargetDetector())
        );
        TargetResolutionException ex = assertThrows(
            TargetResolutionException.class,
            () -> registry.resolve(tempDir)
        );
        assertEquals("TARGET_NOT_DETECTED", ex.code());
    }

    @Test
    void ambiguousMatchThrowsTargetAmbiguous(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("build.gradle"), "apply plugin: 'java'");
        // Stub detector that always returns SUPPORTED for PYTHON
        TargetDetector pythonDetector = projectRoot -> DetectedTarget.supported(TargetId.PYTHON, "stub");
        TargetRegistry registry = new TargetRegistry(
            Map.of(TargetId.JVM, new JvmTarget()),
            List.of(new JvmTargetDetector(), pythonDetector)
        );
        TargetResolutionException ex = assertThrows(
            TargetResolutionException.class,
            () -> registry.resolve(tempDir)
        );
        assertEquals("TARGET_AMBIGUOUS", ex.code());
    }

    @Test
    void invalidPinFileThrowsException(@TempDir Path tempDir) throws IOException {
        Path bearDir = tempDir.resolve(".bear");
        Files.createDirectories(bearDir);
        Files.writeString(bearDir.resolve("target.id"), "invalid");
        TargetRegistry registry = new TargetRegistry(
            Map.of(TargetId.JVM, new JvmTarget()),
            List.of(new JvmTargetDetector())
        );
        TargetResolutionException ex = assertThrows(
            TargetResolutionException.class,
            () -> registry.resolve(tempDir)
        );
        assertEquals("TARGET_PIN_INVALID", ex.code());
    }

    @Test
    void unrelatedUnsupportedDoesNotBlockResolution(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("build.gradle"), "apply plugin: 'java'");
        TargetDetector unsupportedPython = projectRoot ->
            DetectedTarget.unsupported(TargetId.PYTHON, "not a python project");
        TargetRegistry registry = new TargetRegistry(
            Map.of(TargetId.JVM, new JvmTarget()),
            List.of(new JvmTargetDetector(), unsupportedPython)
        );
        Target result = registry.resolve(tempDir);
        assertInstanceOf(JvmTarget.class, result);
    }

    @Test
    void existingLegacyConstructorStillWorks() {
        TargetRegistry registry = new TargetRegistry(Map.of(TargetId.JVM, new JvmTarget()));
        Target result = registry.resolve(Path.of("."));
        assertInstanceOf(JvmTarget.class, result);
    }
}
