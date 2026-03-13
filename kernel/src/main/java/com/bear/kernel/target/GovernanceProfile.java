package com.bear.kernel.target;

/**
 * Pairs a target ecosystem with a governance profile identifier.
 * Known profiles:
 * - jvm/backend-service (current JVM behavior)
 * - node/backend-service (Node Phase B)
 * - python/service (Python strict)
 * - python/service-relaxed (Python pragmatic)
 * - react/feature-ui (React future)
 */
public record GovernanceProfile(TargetId target, String profileId) {

    public static GovernanceProfile of(TargetId target, String profileId) {
        return new GovernanceProfile(target, profileId);
    }

    @Override
    public String toString() {
        return target.value() + "/" + profileId;
    }
}
