package com.bear.kernel.target.jvm;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.TargetDetector;
import com.bear.kernel.target.TargetId;

import java.nio.file.Files;
import java.nio.file.Path;

public class JvmTargetDetector implements TargetDetector {

    @Override
    public DetectedTarget detect(Path projectRoot) {
        if (Files.exists(projectRoot.resolve("build.gradle"))
                || Files.exists(projectRoot.resolve("build.gradle.kts"))) {
            return DetectedTarget.supported(TargetId.JVM, "Gradle build file detected");
        }
        if (Files.exists(projectRoot.resolve("gradlew"))
                || Files.exists(projectRoot.resolve("gradlew.bat"))) {
            return DetectedTarget.supported(TargetId.JVM, "Gradle wrapper detected");
        }
        return DetectedTarget.none();
    }
}
