package com.bear.kernel.target;

import com.bear.kernel.target.jvm.JvmTarget;
import com.bear.kernel.target.jvm.JvmTargetDetector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TargetRegistry {
    private static final TargetRegistry DEFAULT = new TargetRegistry(
        Map.of(TargetId.JVM, new JvmTarget()),
        List.of(new JvmTargetDetector())
    );

    private final Map<TargetId, Target> targets;
    private final List<TargetDetector> detectors;

    /**
     * Backward-compatible constructor. Creates a registry with no detectors.
     * Requires a JVM target for backward compatibility.
     */
    public TargetRegistry(Map<TargetId, Target> targets) {
        this.targets = Map.copyOf(targets);
        this.detectors = List.of();
        if (!this.targets.containsKey(TargetId.JVM)) {
            throw new IllegalArgumentException("TargetRegistry requires a JVM target");
        }
    }

    /**
     * Full constructor with detectors for the detection pipeline.
     */
    public TargetRegistry(Map<TargetId, Target> targets, List<TargetDetector> detectors) {
        this.targets = Map.copyOf(targets);
        this.detectors = List.copyOf(detectors);
    }

    public static TargetRegistry defaultRegistry() {
        return DEFAULT;
    }

    public Target resolve(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");

        // Step 1: Check for pin file
        Path bearDir = projectRoot.resolve(".bear");
        try {
            var pinned = TargetPinFile.read(bearDir);
            if (pinned.isPresent()) {
                TargetId pinnedId = pinned.get();
                Target target = targets.get(pinnedId);
                if (target == null) {
                    throw new TargetResolutionException(
                        "TARGET_PIN_INVALID",
                        bearDir.resolve("target.id").toString(),
                        "Pin file specifies '" + pinnedId.value() + "' but no target is registered for it. "
                            + "Remove or correct .bear/target.id."
                    );
                }
                return target;
            }
        } catch (IllegalArgumentException e) {
            throw new TargetResolutionException(
                "TARGET_PIN_INVALID",
                bearDir.resolve("target.id").toString(),
                e.getMessage()
            );
        }

        // Step 2: If no detectors, fall back to legacy behavior (backward compat)
        if (detectors.isEmpty()) {
            return targets.get(TargetId.JVM);
        }

        // Step 3: Run detectors
        List<DetectedTarget> results = new ArrayList<>();
        for (TargetDetector detector : detectors) {
            results.add(detector.detect(projectRoot));
        }

        // Step 4: Collect SUPPORTED and UNSUPPORTED
        List<DetectedTarget> supported = new ArrayList<>();
        List<DetectedTarget> unsupported = new ArrayList<>();
        for (DetectedTarget result : results) {
            if (result.status() == DetectionStatus.SUPPORTED) {
                supported.add(result);
            } else if (result.status() == DetectionStatus.UNSUPPORTED) {
                unsupported.add(result);
            }
        }

        // Step 5: Filter out SUPPORTED results blocked by same-ecosystem UNSUPPORTED
        List<DetectedTarget> unblocked = new ArrayList<>();
        for (DetectedTarget sup : supported) {
            boolean blocked = false;
            for (DetectedTarget unsup : unsupported) {
                if (unsup.targetId() != null && unsup.targetId() == sup.targetId()) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                unblocked.add(sup);
            }
        }

        // Step 6: Resolution
        if (unblocked.isEmpty()) {
            throw new TargetResolutionException(
                "TARGET_NOT_DETECTED",
                projectRoot.toString(),
                "No target detector matched this project. Add a .bear/target.id pin file or ensure the project has recognized build files."
            );
        }
        if (unblocked.size() > 1) {
            throw new TargetResolutionException(
                "TARGET_AMBIGUOUS",
                projectRoot.toString(),
                "Multiple targets detected. Add a .bear/target.id pin file to disambiguate."
            );
        }

        DetectedTarget winner = unblocked.get(0);
        Target target = targets.get(winner.targetId());
        if (target == null) {
            throw new TargetResolutionException(
                "TARGET_NOT_DETECTED",
                projectRoot.toString(),
                "Detected target '" + winner.targetId().value() + "' but no Target implementation is registered for it."
            );
        }
        return target;
    }
}
