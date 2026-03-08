package com.bear.kernel.target;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectTestDiagnostics {
    private static final String INVARIANT_MARKER_PREFIX = "BEAR_INVARIANT_VIOLATION|";
    private static final String SHARED_DEPS_VIOLATION_MARKER_PREFIX = "BEAR_SHARED_DEPS_VIOLATION|";
    private static final List<String> INVARIANT_MARKER_KEYS =
        List.of("block", "kind", "field", "observed", "rule");
    private static final int COMPILE_MARKER_TAIL_WINDOW = 400;
    private static final Pattern FAILED_DELETE_FILE_PATTERN = Pattern.compile("(?i)failed to delete file:\\s*(.+)$");
    private static final List<Pattern> COMPILE_MARKER_PATTERNS = List.of(
        Pattern.compile("^>\\s*Task\\s+:.+compile.+\\s+FAILED$"),
        Pattern.compile("^:compileJava FAILED$"),
        Pattern.compile("^:compileTestJava FAILED$"),
        Pattern.compile("^Execution failed for task ':compileJava'.$"),
        Pattern.compile("^Execution failed for task ':compileTestJava'.$"),
        Pattern.compile("^> Compilation failed; see the compiler error output for details.$"),
        Pattern.compile("^Compilation failed$"),
        Pattern.compile(".*error: illegal character:.*"),
        Pattern.compile(".*illegal character:.*")
    );

    private ProjectTestDiagnostics() {
    }

    public static int testTimeoutSeconds() {
        String raw = System.getProperty("bear.check.testTimeoutSeconds");
        if (raw == null || raw.isBlank()) {
            return 300;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed <= 0 ? 300 : parsed;
        } catch (NumberFormatException e) {
            return 300;
        }
    }

    public static String firstGradleLockLine(String output) {
        return firstGradleLockLine(output, null);
    }

    public static String firstGradleBootstrapIoLine(String output) {
        return firstGradleBootstrapIoLine(output, null);
    }

    public static String firstRelevantProjectTestFailureLine(String output) {
        List<String> lines = normalizeLf(output).lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .toList();
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("exception")
                || lower.contains("error")
                || lower.contains("failed")
                || lower.contains("failure")
                || lower.contains("could not")) {
                return line;
            }
        }
        if (!lines.isEmpty()) {
            return lines.get(0);
        }
        return null;
    }

    public static String firstCompileFailureLine(String output) {
        List<String> tail = tailNormalizedLines(output, COMPILE_MARKER_TAIL_WINDOW);
        for (Pattern markerPattern : COMPILE_MARKER_PATTERNS) {
            for (String line : tail) {
                if (markerPattern.matcher(line).matches()) {
                    return line;
                }
            }
        }
        return null;
    }

    public static String firstSharedDepsViolationLine(String output) {
        for (String line : normalizeLf(output).lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith(SHARED_DEPS_VIOLATION_MARKER_PREFIX)) {
                return trimmed;
            }
        }
        return null;
    }

    public static String firstInvariantViolationLine(String output) {
        for (String line : normalizeLf(output).lines().toList()) {
            String trimmed = line.trim();
            int markerIndex = trimmed.indexOf(INVARIANT_MARKER_PREFIX);
            if (markerIndex >= 0) {
                String marker = parseInvariantMarker(trimmed.substring(markerIndex));
                if (marker != null) {
                    return marker;
                }
            }
        }
        return null;
    }

    public static String projectTestDetail(String base, String firstLine, String tail) {
        StringBuilder detail = new StringBuilder(base);
        if (firstLine != null && !firstLine.isBlank()) {
            detail.append("; line: ").append(firstLine.trim());
        }
        if (tail != null && !tail.isBlank()) {
            String normalizedTail = tail.trim();
            if (firstLine == null || !normalizedTail.equals(firstLine.trim())) {
                detail.append("; tail: ").append(normalizedTail);
            }
        }
        return detail.toString();
    }

    static String firstGradleLockLine(String output, String gradleUserHome) {
        for (String line : normalizeLf(output).lines().toList()) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains(".zip.lck") || lower.contains("access is denied")) {
                return line.trim();
            }
            if (isGradleScopedTempDeleteFailureLine(line, gradleUserHome)) {
                return line.trim();
            }
        }
        return null;
    }

    static String firstGradleBootstrapIoLine(String output, String gradleUserHome) {
        for (String line : normalizeLf(output).lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            boolean mentionsGradleZip = lower.contains("gradle-") && lower.contains("-bin.zip");
            if ((mentionsGradleZip
                && (lower.contains("nosuchfileexception")
                || lower.contains("filenotfoundexception")
                || lower.contains("zipexception")
                || lower.contains("error in opening zip file")
                || lower.contains("end header not found")
                || lower.contains("cannot unzip")
                || lower.contains("unable to unzip")
                || lower.contains("unable to install gradle")))
                || lower.contains("project_test_gradle_bootstrap_simulated")) {
                return trimmed;
            }
        }
        return null;
    }

    private static String parseInvariantMarker(String marker) {
        if (marker == null || !marker.startsWith(INVARIANT_MARKER_PREFIX)) {
            return null;
        }
        String payload = marker.substring(INVARIANT_MARKER_PREFIX.length());
        List<String> fields = splitEscapedByPipe(payload);
        if (fields == null || fields.size() != INVARIANT_MARKER_KEYS.size()) {
            return null;
        }
        for (int i = 0; i < INVARIANT_MARKER_KEYS.size(); i++) {
            String field = fields.get(i);
            int eq = firstUnescapedEquals(field);
            if (eq <= 0) {
                return null;
            }
            String key = field.substring(0, eq);
            if (!INVARIANT_MARKER_KEYS.get(i).equals(key)) {
                return null;
            }
        }
        return marker;
    }

    private static List<String> splitEscapedByPipe(String payload) {
        ArrayList<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder(payload.length());
        boolean escaped = false;
        for (int i = 0; i < payload.length(); i++) {
            char c = payload.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                current.append(c);
                escaped = true;
                continue;
            }
            if (c == '|') {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (escaped) {
            return null;
        }
        parts.add(current.toString());
        return parts;
    }

    private static int firstUnescapedEquals(String value) {
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '=') {
                return i;
            }
        }
        return -1;
    }

    private static boolean isGradleScopedTempDeleteFailureLine(String line, String gradleUserHome) {
        if (line == null || gradleUserHome == null || gradleUserHome.isBlank()) {
            return false;
        }
        Matcher matcher = FAILED_DELETE_FILE_PATTERN.matcher(line);
        if (!matcher.find()) {
            return false;
        }
        String parsedPath = extractPathToken(matcher.group(1));
        if (parsedPath == null) {
            return false;
        }
        java.nio.file.Path failedPath;
        try {
            failedPath = java.nio.file.Path.of(parsedPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            return false;
        }
        java.nio.file.Path gradleHomeRoot;
        try {
            gradleHomeRoot = java.nio.file.Path.of(gradleUserHome).toAbsolutePath().normalize();
        } catch (Exception e) {
            return false;
        }
        if (!isPathInsideRoot(failedPath, gradleHomeRoot)) {
            return false;
        }
        String rel = normalizePathForComparison(gradleHomeRoot.relativize(failedPath));
        return isTmpTempRelative(rel) || isGroovyDslInstrumentedTempRelative(rel);
    }

    private static boolean isTmpTempRelative(String relativePath) {
        if (relativePath == null
            || !relativePath.startsWith(".tmp/")
            || !relativePath.endsWith(".tmp")) {
            return false;
        }
        String leaf = relativePath.substring(".tmp/".length());
        return !leaf.isBlank() && !leaf.contains("/");
    }

    private static boolean isGroovyDslInstrumentedTempRelative(String relativePath) {
        return relativePath != null
            && relativePath.startsWith("caches/")
            && relativePath.endsWith(".tmp")
            && relativePath.contains("/groovy-dsl/")
            && relativePath.contains("/instrumented/");
    }

    private static String extractPathToken(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
            || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static boolean isPathInsideRoot(java.nio.file.Path child, java.nio.file.Path root) {
        String childNorm = normalizePathForComparison(child);
        String rootNorm = normalizePathForComparison(root);
        if (rootNorm.isEmpty()) {
            return false;
        }
        if (childNorm.equals(rootNorm)) {
            return true;
        }
        return childNorm.startsWith(rootNorm + "/");
    }

    private static String normalizePathForComparison(java.nio.file.Path path) {
        return normalizePathForComparison(path == null ? null : path.toString());
    }

    private static String normalizePathForComparison(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.replace('\\', '/');
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static List<String> tailNormalizedLines(String output, int maxLines) {
        List<String> lines = normalizeLf(output).lines().toList();
        int from = Math.max(0, lines.size() - maxLines);
        ArrayList<String> tail = new ArrayList<>();
        for (int i = from; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (!trimmed.isEmpty()) {
                tail.add(trimmed);
            }
        }
        return tail;
    }

    private static String normalizeLf(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    }
}