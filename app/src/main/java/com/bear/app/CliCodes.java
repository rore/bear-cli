package com.bear.app;

final class CliCodes {
    private CliCodes() {
    }

    static final int EXIT_OK = 0;
    static final int EXIT_VALIDATION = 2;
    static final int EXIT_DRIFT = 3;
    static final int EXIT_TEST_FAILURE = 4;
    static final int EXIT_BOUNDARY_EXPANSION = 5;
    static final int EXIT_UNDECLARED_REACH = 6;
    static final int EXIT_BOUNDARY_BYPASS = 7;
    static final int EXIT_USAGE = 64;
    static final int EXIT_IO = 74;
    static final int EXIT_INTERNAL = 70;

    static final String USAGE_INVALID_ARGS = "USAGE_INVALID_ARGS";
    static final String USAGE_UNKNOWN_COMMAND = "USAGE_UNKNOWN_COMMAND";
    static final String IR_VALIDATION = "IR_VALIDATION";
    static final String INDEX_REQUIRED_MISSING = "INDEX_REQUIRED_MISSING";
    static final String NOT_SUPPORTED_YET = "NOT_SUPPORTED_YET";
    static final String POLICY_INVALID = "POLICY_INVALID";
    static final String IO_ERROR = "IO_ERROR";
    static final String IO_GIT = "IO_GIT";
    static final String DRIFT_MISSING_BASELINE = "DRIFT_MISSING_BASELINE";
    static final String DRIFT_DETECTED = "DRIFT_DETECTED";
    static final String TEST_FAILURE = "TEST_FAILURE";
    static final String COMPILE_FAILURE = "COMPILE_FAILURE";
    static final String TEST_TIMEOUT = "TEST_TIMEOUT";
    static final String BOUNDARY_EXPANSION = "BOUNDARY_EXPANSION";
    static final String UNDECLARED_REACH = "UNDECLARED_REACH";
    static final String REFLECTION_DISPATCH_FORBIDDEN = "REFLECTION_DISPATCH_FORBIDDEN";
    static final String BOUNDARY_BYPASS = "BOUNDARY_BYPASS";
    static final String PORT_IMPL_OUTSIDE_GOVERNED_ROOT = "PORT_IMPL_OUTSIDE_GOVERNED_ROOT";
    static final String MULTI_BLOCK_PORT_IMPL_FORBIDDEN = "MULTI_BLOCK_PORT_IMPL_FORBIDDEN";
    static final String HYGIENE_UNEXPECTED_PATHS = "HYGIENE_UNEXPECTED_PATHS";
    static final String CONTAINMENT_NOT_VERIFIED = "CONTAINMENT_NOT_VERIFIED";
    static final String CONTAINMENT_UNSUPPORTED_TARGET = "CONTAINMENT_UNSUPPORTED_TARGET";
    static final String CONTAINMENT_METADATA_MISMATCH = "CONTAINMENT_METADATA_MISMATCH";
    static final String REPO_MULTI_BLOCK_FAILED = "REPO_MULTI_BLOCK_FAILED";
    static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    static final String MANIFEST_INVALID = "MANIFEST_INVALID";
    static final String INVARIANT_VIOLATION = "INVARIANT_VIOLATION";
    static final String UNBLOCK_LOCKED = "UNBLOCK_LOCKED";
    static final String NEXT_ACTION_COMMAND_INVALID = "NEXT_ACTION_COMMAND_INVALID";
}