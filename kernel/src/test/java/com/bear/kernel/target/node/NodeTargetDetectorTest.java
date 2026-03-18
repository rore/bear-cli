package com.bear.kernel.target.node;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.DetectionStatus;
import com.bear.kernel.target.TargetId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeTargetDetectorTest {

    private final NodeTargetDetector detector = new NodeTargetDetector();

    @Test
    void validNodeProject(@TempDir Path tempDir) throws IOException {
        createPackageJson(tempDir, "module", "pnpm@8.0.0");
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.NODE, result.targetId());
    }

    @Test
    void missingPackageJson(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void missingPnpmLock(@TempDir Path tempDir) throws IOException {
        createPackageJson(tempDir, "module", "pnpm@8.0.0");
        Files.createFile(tempDir.resolve("tsconfig.json"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void missingTsconfig(@TempDir Path tempDir) throws IOException {
        createPackageJson(tempDir, "module", "pnpm@8.0.0");
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void cjsProject(@TempDir Path tempDir) throws IOException {
        createPackageJson(tempDir, "commonjs", "pnpm@8.0.0");
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void npmProject(@TempDir Path tempDir) throws IOException {
        createPackageJson(tempDir, "module", "npm@9.0.0");
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void jvmProjectReturnsNone(@TempDir Path tempDir) throws IOException {
        // JVM project with build.gradle but no package.json — no false-positive cross-detection
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");
        Files.createFile(tempDir.resolve("gradlew"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void workspaceProject(@TempDir Path tempDir) throws IOException {
        createPackageJson(tempDir, "module", "pnpm@8.0.0");
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("pnpm-workspace.yaml"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.NODE, result.targetId());
    }

    private void createPackageJson(Path dir, String type, String packageManager) throws IOException {
        String content = """
            {
              "name": "test-project",
              "type": "%s",
              "packageManager": "%s"
            }
            """.formatted(type, packageManager);
        Files.writeString(dir.resolve("package.json"), content);
    }
}
