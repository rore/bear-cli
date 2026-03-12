package com.bear.kernel.target;

public class TargetResolutionException extends RuntimeException {
    private final String code;
    private final String path;
    private final String remediation;

    public TargetResolutionException(String code, String path, String remediation) {
        super(code + ": " + path + " -- " + remediation);
        this.code = code;
        this.path = path;
        this.remediation = remediation;
    }

    public String code() { return code; }
    public String path() { return path; }
    public String remediation() { return remediation; }
}
