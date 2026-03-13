package com.bear.kernel;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.DetectionStatus;
import com.bear.kernel.target.jvm.JvmTargetDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JvmTargetDetectorTest {

    private final JvmTargetDetector detector = new JvmTargetDetector();

    @Test
    void detectsGradleBuild(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("build.gradle"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
    }

    @Test
    void detectsGradleKtsBuild(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("build.gradle.kts"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
    }

    @Test
    void noGradleFile(@TempDir Path tempDir) {
        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }
}
