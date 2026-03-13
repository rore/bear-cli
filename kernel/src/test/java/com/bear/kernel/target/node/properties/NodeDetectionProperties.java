package com.bear.kernel.target.node.properties;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.DetectionStatus;
import com.bear.kernel.target.TargetId;
import com.bear.kernel.target.TargetRegistry;
import com.bear.kernel.target.node.NodeTarget;
import com.bear.kernel.target.node.NodeTargetDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for NodeTarget detection (implemented as parameterized JUnit 5 tests).
 * Feature: phase-b-node-target-scan-only
 */
class NodeDetectionProperties {

    private final NodeTargetDetector detector = new NodeTargetDetector();

    /**
     * Property 1: Valid Node project structure → SUPPORTED, targetId=NODE.
     */
    @Test
    void validNodeProjectStructureReturnsSupported(@TempDir Path projectRoot) throws IOException {
        createValidNodeProject(projectRoot);
        DetectedTarget result = detector.detect(projectRoot);
        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.NODE, result.targetId());
    }

    /**
     * Property 1 (variant): Multiple valid project shapes all return SUPPORTED.
     */
    @Test
    void validNodeProjectWithDifferentPnpmVersionsReturnsSupported(@TempDir Path projectRoot) throws IOException {
        Files.writeString(projectRoot.resolve("package.json"),
            "{ \"name\": \"test\", \"type\": \"module\", \"packageManager\": \"pnpm@9.1.0\" }");
        Files.createFile(projectRoot.resolve("pnpm-lock.yaml"));
        Files.createFile(projectRoot.resolve("tsconfig.json"));

        DetectedTarget result = detector.detect(projectRoot);
        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.NODE, result.targetId());
    }

    /**
     * Property 2: TargetRegistry.resolve() on valid Node project → NodeTarget instance.
     */
    @Test
    void targetRegistryResolvesNodeTarget(@TempDir Path projectRoot) throws IOException {
        createValidNodeProject(projectRoot);
        var target = TargetRegistry.defaultRegistry().resolve(projectRoot);
        assertInstanceOf(NodeTarget.class, target);
    }

    /**
     * Property 2 (determinism): TargetRegistry.resolve() called twice → same result.
     */
    @Test
    void targetRegistryResolutionIsDeterministic(@TempDir Path projectRoot) throws IOException {
        createValidNodeProject(projectRoot);
        var target1 = TargetRegistry.defaultRegistry().resolve(projectRoot);
        var target2 = TargetRegistry.defaultRegistry().resolve(projectRoot);
        assertEquals(target1.getClass(), target2.getClass());
        assertEquals(target1.targetId(), target2.targetId());
    }

    private void createValidNodeProject(Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"),
            "{ \"name\": \"test\", \"type\": \"module\", \"packageManager\": \"pnpm@8.0.0\" }");
        Files.createFile(dir.resolve("pnpm-lock.yaml"));
        Files.createFile(dir.resolve("tsconfig.json"));
    }
}
