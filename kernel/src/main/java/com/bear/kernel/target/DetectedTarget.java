package com.bear.kernel.target;

public record DetectedTarget(TargetId targetId, DetectionStatus status, String reason) {

    public static DetectedTarget supported(TargetId targetId, String reason) {
        return new DetectedTarget(targetId, DetectionStatus.SUPPORTED, reason);
    }

    public static DetectedTarget unsupported(TargetId targetId, String reason) {
        return new DetectedTarget(targetId, DetectionStatus.UNSUPPORTED, reason);
    }

    public static DetectedTarget none() {
        return new DetectedTarget(null, DetectionStatus.NONE, "");
    }
}
