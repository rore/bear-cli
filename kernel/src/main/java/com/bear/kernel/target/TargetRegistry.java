package com.bear.kernel.target;

import com.bear.kernel.target.jvm.JvmTarget;
import com.bear.kernel.target.jvm.JvmTargetDetector;
import com.bear.kernel.target.node.NodeTarget;
import com.bear.kernel.target.node.NodeTargetDetector;
import com.bear.kernel.target.python.PythonTarget;
import com.bear.kernel.target.python.PythonTargetDetector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TargetRegistry {
    private static final TargetRegistry DEFAULT = new TargetRegistry(
        Map.of(
            TargetId.JVM, new JvmTarget(),
            TargetId.NODE, new NodeTarget(),
            TargetId.PYTHON, new PythonTarget()
        ),
        List.of(
            new JvmTargetDetector(),
            new NodeTargetDetector(),
            new PythonTargetDetector()
        )
    );

    private final Map<TargetId, Target> targets;
    private final List<TargetDetector> detectors;

    /**
     * Backward-compatible constructor. Creates a registry with no detectors.
     * Uses the provided targets as-is, without enforcing a particular TargetId.
     */
    public TargetRegistry(Map<TargetId, Target> targets) {
        this.targets = Map.copyOf(targets);
        this.detectors = List.of();
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

    /**
     * Resolves the Target for the given project root directory.
     * The path must be the project root (not a file within the project).
     * Detection checks for .bear/target.id pin files and build files relative to this path.
     */
    public Target resolve(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");

        // Normalize: if a file path was passed (e.g., an IR file), use its parent directory.
        if (Files.isRegularFile(projectRoot)) {
            Path parent = projectRoot.getParent();
            // For relative file paths like "ir.bear.yaml", getParent() may be null.
            // In that case, treat the current working directory as the project root.
            projectRoot = (parent != null) ? parent : Path.of(".");
        }

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
                e.getMessage(),
                e
            );
        } catch (java.io.UncheckedIOException e) {
            throw new TargetResolutionException(
                "TARGET_PIN_UNREADABLE",
                bearDir.resolve("target.id").toString(),
                "Failed to read .bear/target.id: " + e.getMessage(),
                74,
                e
            );
        }

        // Step 2: If no detectors, resolve only in unambiguous configurations
        if (detectors.isEmpty()) {
            if (targets.size() == 1) {
                return targets.values().iterator().next();
            }
            Target jvmFallback = targets.get(TargetId.JVM);
            if (jvmFallback != null) {
                return jvmFallback;
            }
            throw new TargetResolutionException(
                "TARGET_NOT_DETECTED",
                projectRoot.toString(),
                "No target detectors are configured and automatic resolution is ambiguous. "
                    + "Either register exactly one target, or add detectors or a .bear/target.id pin file."
            );
        }

        // Step 3: Run detectors
        List<DetectedTarget> results = new ArrayList<>();
        for (TargetDetector detector : detectors) {
            DetectedTarget result = detector.detect(projectRoot);
            if (result == null) {
                throw new TargetResolutionException(
                    "TARGET_DETECTOR_INVALID",
                    projectRoot.toString(),
                    "A target detector returned null instead of a DetectedTarget. "
                        + "This indicates a bug in the detector implementation."
                );
            }
            results.add(result);
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

        // Step 5: Filter out SUPPORTED results blocked by same-ecosystem-family UNSUPPORTED.
        // Use ecosystem family instead of TargetId equality so that Node/React can share a family.
        List<DetectedTarget> unblocked = new ArrayList<>();
        for (DetectedTarget sup : supported) {
            boolean blocked = false;
            String supFamily = sup.targetId() != null ? sup.targetId().ecosystemFamily() : null;
            for (DetectedTarget unsup : unsupported) {
                String unsupFamily = unsup.targetId() != null ? unsup.targetId().ecosystemFamily() : null;
                if (supFamily != null && supFamily.equals(unsupFamily)) {
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
            // Check if any SUPPORTED results were blocked by same-ecosystem UNSUPPORTED
            if (!supported.isEmpty()) {
                // Find the UNSUPPORTED result that blocked the SUPPORTED one
                DetectedTarget blocker = null;
                for (DetectedTarget sup : supported) {
                    String supFamily = sup.targetId() != null ? sup.targetId().ecosystemFamily() : null;
                    for (DetectedTarget unsup : unsupported) {
                        String unsupFamily = unsup.targetId() != null ? unsup.targetId().ecosystemFamily() : null;
                        if (supFamily != null && supFamily.equals(unsupFamily)) {
                            blocker = unsup;
                            break;
                        }
                    }
                    if (blocker != null) break;
                }
                String reason = blocker != null ? blocker.reason() : "unsupported project shape";
                throw new TargetResolutionException(
                    "TARGET_UNSUPPORTED",
                    projectRoot.toString(),
                    "Target ecosystem recognized but project shape is unsupported: " + reason
                );
            }
            // No detector matched (all returned NONE). Fall back to JVM for backward compatibility.
            Target jvmFallback = targets.get(TargetId.JVM);
            if (jvmFallback != null) {
                return jvmFallback;
            }
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
        TargetId winnerId = winner.targetId();
        if (winnerId == null) {
            throw new TargetResolutionException(
                "TARGET_DETECTOR_INVALID",
                projectRoot.toString(),
                "A target detector reported a SUPPORTED target with a null TargetId. "
                    + "This indicates a bug in the detector implementation."
            );
        }
        Target target = targets.get(winnerId);
        if (target == null) {
            throw new TargetResolutionException(
                "TARGET_NOT_DETECTED",
                projectRoot.toString(),
                "Detected target '" + winnerId.value() + "' but no Target implementation is registered for it."
            );
        }
        return target;
    }
}
