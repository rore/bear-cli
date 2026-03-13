package com.bear.kernel.target.node;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.TargetDetector;
import com.bear.kernel.target.TargetId;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class NodeTargetDetector implements TargetDetector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public DetectedTarget detect(Path projectRoot) {
        // Check for package.json
        Path packageJson = projectRoot.resolve("package.json");
        if (!Files.exists(packageJson)) {
            return DetectedTarget.none();
        }

        // Parse package.json (strict JSON) to check type and packageManager
        try {
            String content = Files.readString(packageJson, StandardCharsets.UTF_8);
            LoaderOptions loaderOptions = new LoaderOptions();
            loaderOptions.setAllowDuplicateKeys(false);
            Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
            Object parsed = yaml.load(content);
            if (!(parsed instanceof Map)) {
                return DetectedTarget.none();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> pkg = OBJECT_MAPPER.readValue(packageJson.toFile(), Map.class);
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
