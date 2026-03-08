package com.bear.kernel.target;

public enum ProjectTestStatus {
    PASSED,
    FAILED,
    COMPILE_FAILURE,
    SHARED_DEPS_VIOLATION,
    INVARIANT_VIOLATION,
    TIMEOUT,
    LOCKED,
    BOOTSTRAP_IO
}

