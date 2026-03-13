package com.bear.kernel.target.node;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.TargetDetector;
import com.bear.kernel.target.TargetId;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

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
            LoaderOptions loaderOptions = new LoaderOptions();
            Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
            Object parsed = yaml.load(content);
            if (!(parsed instanceof Map)) {
                return DetectedTarget.none();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> pkg = (Map<String, Object>) parsed;
            Object typeVal = pkg.get("type");
            if (!"module".equals(typeVal)) {
                return DetectedTarget.none();
            }
            Object pmVal = pkg.get("packageManager");
            if (!(pmVal instanceof String) || !((String) pmVal).startsWith("pnpm")) {
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
