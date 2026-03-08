package com.bear.app;

import com.bear.kernel.target.ProjectTestResult;
import java.io.IOException;

final class CheckDiagnosticsFormatter {
    private CheckDiagnosticsFormatter() {
    }

    static String testDiagnosticsSuffix(ProjectTestResult testResult) {
        String attempts = testResult.attemptTrail() == null || testResult.attemptTrail().isBlank()
            ? "<none>"
            : testResult.attemptTrail().trim();
        String cacheMode = testResult.cacheMode() == null || testResult.cacheMode().isBlank()
            ? "isolated"
            : testResult.cacheMode().trim();
        String fallback = testResult.fallbackToUserCache() ? "to_user_cache" : "none";
        return "; attempts=" + attempts + "; CACHE_MODE=" + cacheMode + "; FALLBACK=" + fallback;
    }

    static String markerWriteFailureSuffix(IOException error) {
        return "; markerWrite=failed:" + CliText.squash(error.getMessage());
    }

    static String phaseTaskSuffix(ProjectTestResult testResult) {
        String phase = testResult.phase() == null || testResult.phase().isBlank()
            ? "unknown"
            : testResult.phase().trim();
        String lastTask = testResult.lastObservedTask() == null || testResult.lastObservedTask().isBlank()
            ? "unknown"
            : testResult.lastObservedTask().trim();
        return "; phase=" + phase + "; lastTask=" + lastTask;
    }
}


