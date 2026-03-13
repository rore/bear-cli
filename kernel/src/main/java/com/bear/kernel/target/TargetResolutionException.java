package com.bear.kernel.target;

public class TargetResolutionException extends RuntimeException {
    private final String code;
    private final String path;
    private final String remediation;
    private final int exitCode;

    public TargetResolutionException(String code, String path, String remediation) {
        this(code, path, remediation, 64);
    }

    public TargetResolutionException(String code, String path, String remediation, int exitCode) {
        super(code + ": " + path + " -- " + remediation);
        this.code = code;
        this.path = path;
        this.remediation = remediation;
        this.exitCode = exitCode;
    }

    public TargetResolutionException(String code, String path, String remediation, Throwable cause) {
        this(code, path, remediation, 64, cause);
    }

    public TargetResolutionException(String code, String path, String remediation, int exitCode, Throwable cause) {
        super(code + ": " + path + " -- " + remediation, cause);
        this.code = code;
        this.path = path;
        this.remediation = remediation;
        this.exitCode = exitCode;
    }

    public String code() { return code; }
    public String path() { return path; }
    public String remediation() { return remediation; }
    public int exitCode() { return exitCode; }
}
