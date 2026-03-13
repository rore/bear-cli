package com.bear.kernel.target.node;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.DetectionStatus;
import com.bear.kernel.target.TargetDetector;
import com.bear.kernel.target.TargetId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class NodeTargetDetector implements TargetDetector {

    @Override
    public DetectedTarget detect(Path projectRoot) {
        // Check for package.json
        Path packageJson = projectRoot.resolve("package.json");
        if (!Files.exists(packageJson)) {
            return DetectedTarget.none();
        }

        // Parse package.json to check type and packageManager
        try {
            String content = Files.readString(packageJson);
            if (!content.contains("\"type\": \"module\"")) {
                return DetectedTarget.none();
            }
            if (!content.contains("\"packageManager\": \"pnpm")) {
                return DetectedTarget.none();
            }
        } catch (Exception e) {
            return DetectedTarget.none();
        }

        // Check for pnpm-lock.yaml
        Path pnpmLock = projectRoot.resolve("pnpm-lock.yaml");
        if (!Files.exists(pnpmLock)) {
            return DetectedTarget.none();
        }

        // Check for tsconfig.json
        Path tsconfig = projectRoot.resolve("tsconfig.json");
        if (!Files.exists(tsconfig)) {
            return DetectedTarget.none();
        }

        // Check for pnpm-workspace.yaml (unsupported)
        Path workspaceYaml = projectRoot.resolve("pnpm-workspace.yaml");
        if (Files.exists(workspaceYaml)) {
            return DetectedTarget.unsupported(TargetId.NODE, "pnpm workspace detected");
        }

        return DetectedTarget.supported(TargetId.NODE, "Node project detected");
    }
}
