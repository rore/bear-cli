package com.bear.kernel.target.python;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.DetectionStatus;
import com.bear.kernel.target.TargetId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PythonTargetDetectorTest {

    private final PythonTargetDetector detector = new PythonTargetDetector();

    @Test
    void validPythonProject(@TempDir Path tempDir) throws IOException {
        createPyprojectToml(tempDir, true, true);
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createFile(tempDir.resolve("mypy.ini"));
        Files.createDirectories(tempDir.resolve("src/blocks/test-block"));
        Files.createFile(tempDir.resolve("src/blocks/test-block/__init__.py"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void validPythonProjectWithPoetry(@TempDir Path tempDir) throws IOException {
        createPyprojectToml(tempDir, true, true);
        Files.createFile(tempDir.resolve("poetry.lock"));
        Files.createFile(tempDir.resolve("mypy.ini"));
        Files.createDirectories(tempDir.resolve("src/blocks/test-block"));
        Files.createFile(tempDir.resolve("src/blocks/test-block/__init__.py"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void validPythonProjectWithMypyInToml(@TempDir Path tempDir) throws IOException {
        createPyprojectToml(tempDir, true, true);
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createDirectories(tempDir.resolve("src/blocks/test-block"));
        Files.createFile(tempDir.resolve("src/blocks/test-block/__init__.py"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void missingPyprojectToml(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createFile(tempDir.resolve("mypy.ini"));
        Files.createDirectories(tempDir.resolve("src/blocks"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void missingBuildSystem(@TempDir Path tempDir) throws IOException {
        createPyprojectToml(tempDir, false, true);
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createFile(tempDir.resolve("mypy.ini"));
        Files.createDirectories(tempDir.resolve("src/blocks"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void missingProject(@TempDir Path tempDir) throws IOException {
        createPyprojectToml(tempDir, true, false);
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createFile(tempDir.resolve("mypy.ini"));
        Files.createDirectories(tempDir.resolve("src/blocks"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void missingLockFile(@TempDir Path tempDir) throws IOException {
        createPyprojectToml(tempDir, true, true);
        Files.createFile(tempDir.resolve("mypy.ini"));
        Files.createDirectories(tempDir.resolve("src/blocks"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void missingMypy(@TempDir Path tempDir) throws IOException {
        // Create pyproject.toml without [tool.mypy]
        String content = """
            [build-system]
            requires = ["hatchling"]
            build-backend = "hatchling.build"
            
            [project]
            name = "test-project"
            version = "0.1.0"
            """;
        Files.writeString(tempDir.resolve("pyproject.toml"), content);
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createDirectories(tempDir.resolve("src/blocks"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void missingSrcBlocks(@TempDir Path tempDir) throws IOException {
        createPyprojectToml(tempDir, true, true);
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createFile(tempDir.resolve("mypy.ini"));
        // Create src/ directory but not src/blocks/
        Files.createDirectories(tempDir.resolve("src"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void workspaceProject(@TempDir Path tempDir) throws IOException {
        createPyprojectTomlWithWorkspace(tempDir);
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createFile(tempDir.resolve("mypy.ini"));
        Files.createDirectories(tempDir.resolve("src/blocks/test-block"));
        Files.createFile(tempDir.resolve("src/blocks/test-block/__init__.py"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void flatLayout(@TempDir Path tempDir) throws IOException {
        createPyprojectToml(tempDir, true, true);
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createFile(tempDir.resolve("mypy.ini"));
        Files.createDirectories(tempDir.resolve("blocks"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void namespacePackage(@TempDir Path tempDir) throws IOException {
        createPyprojectToml(tempDir, true, true);
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createFile(tempDir.resolve("mypy.ini"));
        Files.createDirectories(tempDir.resolve("src/blocks/test-block"));
        // Missing __init__.py

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void ambiguousProject(@TempDir Path tempDir) throws IOException {
        createPyprojectToml(tempDir, true, true);
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createFile(tempDir.resolve("mypy.ini"));
        Files.createDirectories(tempDir.resolve("src/blocks/test-block"));
        Files.createFile(tempDir.resolve("src/blocks/test-block/__init__.py"));
        Files.writeString(tempDir.resolve("package.json"), "{}");

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void pnpmWorkspaceDetected(@TempDir Path tempDir) throws IOException {
        createPyprojectToml(tempDir, true, true);
        Files.createFile(tempDir.resolve("uv.lock"));
        Files.createFile(tempDir.resolve("mypy.ini"));
        Files.createDirectories(tempDir.resolve("src/blocks/test-block"));
        Files.createFile(tempDir.resolve("src/blocks/test-block/__init__.py"));
        Files.createFile(tempDir.resolve("pnpm-workspace.yaml"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void jvmProject(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("build.gradle"), "");
        Files.writeString(tempDir.resolve("settings.gradle"), "");

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void nodeProject(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("package.json"), """
            {
              "name": "test",
              "type": "module",
              "packageManager": "pnpm@8.0.0"
            }
            """);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    private void createPyprojectToml(Path dir, boolean includeBuildSystem, boolean includeProject) throws IOException {
        StringBuilder content = new StringBuilder();
        
        if (includeBuildSystem) {
            content.append("[build-system]\n");
            content.append("requires = [\"hatchling\"]\n");
            content.append("build-backend = \"hatchling.build\"\n\n");
        }
        
        if (includeProject) {
            content.append("[project]\n");
            content.append("name = \"test-project\"\n");
            content.append("version = \"0.1.0\"\n\n");
        }
        
        // Always include [tool.mypy] for this helper
        content.append("[tool.mypy]\n");
        content.append("strict = true\n");
        
        Files.writeString(dir.resolve("pyproject.toml"), content.toString());
    }

    private void createPyprojectTomlWithWorkspace(Path dir) throws IOException {
        String content = """
            [build-system]
            requires = ["hatchling"]
            build-backend = "hatchling.build"
            
            [project]
            name = "test-project"
            version = "0.1.0"
            
            [tool.mypy]
            strict = true
            
            [tool.uv.workspace]
            members = ["packages/*"]
            """;
        Files.writeString(dir.resolve("pyproject.toml"), content);
    }
}
