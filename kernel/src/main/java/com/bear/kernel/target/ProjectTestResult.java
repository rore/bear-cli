package com.bear.kernel.target;

public record ProjectTestResult(
    ProjectTestStatus status,
    String output,
    String attemptTrail,
    String firstLockLine,
    String firstBootstrapLine,
    String firstSharedDepsViolationLine,
    String cacheMode,
    boolean fallbackToUserCache,
    String phase,
    String lastObservedTask
) {
}

