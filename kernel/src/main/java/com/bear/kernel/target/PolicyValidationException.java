package com.bear.kernel.target;

public final class PolicyValidationException extends Exception {
    private final String policyPath;

    public PolicyValidationException(String policyPath, String message) {
        super(message);
        this.policyPath = policyPath;
    }

    public String policyPath() {
        return policyPath;
    }
}


