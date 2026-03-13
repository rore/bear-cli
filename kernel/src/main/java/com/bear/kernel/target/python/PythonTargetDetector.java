package com.bear.kernel.target.python;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.DetectionStatus;
import com.bear.kernel.target.TargetDetector;
import com.bear.kernel.target.TargetId;

import java.nio.file.Files;
import java.nio.file.Path;

public class PythonTargetDetector implements TargetDetector {

    @Override
    public DetectedTarget detect(Path projectRoot) {
        // 1. Check pyproject.toml at projectRoot
        Path pyprojectToml = projectRoot.resolve("pyproject.toml");
        if (!Files.exists(pyprojectToml)) {
            return DetectedTarget.none();
        }

        // Parse pyproject.toml minimally for [build-system], [project], and [tool.mypy]
        try {
            String content = Files.readString(pyprojectToml);
            
            // Check for [build-system] section (PEP 517 backend)
            if (!content.contains("[build-system]")) {
                return DetectedTarget.none();
            }
            
            // Check for [project] section
            if (!content.contains("[project]")) {
                return DetectedTarget.none();
            }
            
            // 2. Check uv.lock OR poetry.lock at projectRoot
            Path uvLock = projectRoot.resolve("uv.lock");
            Path poetryLock = projectRoot.resolve("poetry.lock");
            if (!Files.exists(uvLock) && !Files.exists(poetryLock)) {
                return DetectedTarget.none();
            }
            
            // 3. Check mypy.ini OR [tool.mypy] in pyproject.toml
            Path mypyIni = projectRoot.resolve("mypy.ini");
            boolean hasMypyIni = Files.exists(mypyIni);
            boolean hasMypyInToml = content.contains("[tool.mypy]");
            if (!hasMypyIni && !hasMypyInToml) {
                return DetectedTarget.none();
            }
            
            // 4. Check for flat layout (no src/ directory) - must check before src/blocks/
            Path srcDir = projectRoot.resolve("src");
            if (!Files.exists(srcDir) || !Files.isDirectory(srcDir)) {
                return DetectedTarget.unsupported(TargetId.PYTHON, "flat layout (no src/ directory)");
            }
            
            // 5. Check src/blocks/ directory
            Path srcBlocks = projectRoot.resolve("src/blocks");
            if (!Files.exists(srcBlocks) || !Files.isDirectory(srcBlocks)) {
                return DetectedTarget.none();
            }
            
            // 6. Check for workspace indicators
            if (content.contains("uv.workspace")) {
                return DetectedTarget.unsupported(TargetId.PYTHON, "uv workspace detected");
            }
            
            Path pnpmWorkspace = projectRoot.resolve("pnpm-workspace.yaml");
            if (Files.exists(pnpmWorkspace)) {
                return DetectedTarget.unsupported(TargetId.PYTHON, "pnpm workspace detected (Node project)");
            }
            
            // 7. Check for ambiguous signals (package.json + pyproject.toml)
            Path packageJson = projectRoot.resolve("package.json");
            if (Files.exists(packageJson)) {
                return DetectedTarget.unsupported(TargetId.PYTHON, "ambiguous project (both package.json and pyproject.toml present)");
            }
            
            // 8. Check for namespace packages (directories in src/blocks/ without __init__.py)
            if (Files.isDirectory(srcBlocks)) {
                try (var stream = Files.list(srcBlocks)) {
                    boolean hasNamespacePackage = stream
                        .filter(Files::isDirectory)
                        .anyMatch(dir -> !Files.exists(dir.resolve("__init__.py")));
                    
                    if (hasNamespacePackage) {
                        return DetectedTarget.unsupported(TargetId.PYTHON, "namespace package detected (missing __init__.py in src/blocks/)");
                    }
                }
            }
            
            // 9. All checks passed
            return DetectedTarget.supported(TargetId.PYTHON, "Python project detected");
            
        } catch (Exception e) {
            return DetectedTarget.none();
        }
    }
}
