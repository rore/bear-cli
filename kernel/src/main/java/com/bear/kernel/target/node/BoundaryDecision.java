package com.bear.kernel.target.node;

/**
 * Result of a boundary resolution check.
 * Use {@link #allowed()} and {@link #fail(String)} factory methods.
 */
public final class BoundaryDecision {

    private final boolean pass;
    private final String failureReason;

    private BoundaryDecision(boolean pass, String failureReason) {
        this.pass = pass;
        this.failureReason = failureReason;
    }

    /** Factory: import is allowed. */
    public static BoundaryDecision allowed() {
        return new BoundaryDecision(true, null);
    }

    /** Factory: import is rejected with a structured reason code. */
    public static BoundaryDecision fail(String reason) {
        return new BoundaryDecision(false, reason);
    }

    /** Returns true if the import is allowed. */
    public boolean pass() {
        return pass;
    }

    /** Returns true if the import is rejected. */
    public boolean isFail() {
        return !pass;
    }

    /** Structured reason code when {@link #isFail()} is true, null otherwise. */
    public String failureReason() {
        return failureReason;
    }
}
