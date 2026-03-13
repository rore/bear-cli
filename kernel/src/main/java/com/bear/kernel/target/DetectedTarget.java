package com.bear.kernel.target;

import java.util.Objects;

public record DetectedTarget(TargetId targetId, DetectionStatus status, String reason) {

    public static DetectedTarget supported(TargetId targetId, String reason) {
        Objects.requireNonNull(targetId, "targetId must not be null for SUPPORTED detection");
        return new DetectedTarget(targetId, DetectionStatus.SUPPORTED, reason != null ? reason : "");
    }

    public static DetectedTarget unsupported(TargetId targetId, String reason) {
        Objects.requireNonNull(targetId, "targetId must not be null for UNSUPPORTED detection");
        return new DetectedTarget(targetId, DetectionStatus.UNSUPPORTED, reason != null ? reason : "");
    }

    public static DetectedTarget none() {
        return new DetectedTarget(null, DetectionStatus.NONE, "");
    }
}
