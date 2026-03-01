package com.bear.app;

import java.io.PrintStream;

final class FailureEnvelopeEmitter {
    private FailureEnvelopeEmitter() {
    }

    static int failWithLegacy(
        PrintStream err,
        int exitCode,
        String legacyLine,
        String code,
        String pathLocator,
        String remediation
    ) {
        err.println(legacyLine);
        return fail(err, exitCode, code, pathLocator, remediation);
    }

    static int fail(PrintStream err, int exitCode, String code, String pathLocator, String remediation) {
        String locator = normalizeLocator(pathLocator);
        err.println("CODE=" + code);
        err.println("PATH=" + locator);
        err.println("REMEDIATION=" + remediation);
        return exitCode;
    }

    static String normalizeLocator(String raw) {
        if (raw == null) {
            return "internal";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "internal";
        }
        if (looksAbsolute(trimmed)) {
            return "internal";
        }
        return trimmed.replace('\\', '/');
    }

    private static boolean looksAbsolute(String value) {
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/")) {
            return true;
        }
        if (normalized.startsWith("//")) {
            return true;
        }
        return normalized.matches("^[A-Za-z]:/.*");
    }
}
