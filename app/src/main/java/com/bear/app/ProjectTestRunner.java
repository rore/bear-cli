package com.bear.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProjectTestRunner {
    private static final String INVARIANT_MARKER_PREFIX = "BEAR_INVARIANT_VIOLATION|";
    private static final String SHARED_DEPS_VIOLATION_MARKER_PREFIX = "BEAR_SHARED_DEPS_VIOLATION|";
    private static final List<String> INVARIANT_MARKER_KEYS =
        List.of("block", "kind", "field", "observed", "rule");
    private static final long RETRY_BACKOFF_MILLIS = 200L;
    private static final Duration STALE_ARTIFACT_THRESHOLD = Duration.ofMinutes(10);
    private static final Pattern FAILED_DELETE_FILE_PATTERN = Pattern.compile("(?i)failed to delete file:\\s*(.+)$");

    private enum GradleHomeMode {
        EXTERNAL_ENV("external-env", "external-env-retry"),
        ISOLATED("isolated", "isolated-retry"),
        USER_CACHE("user-cache", "user-cache-retry");

        private final String initialLabel;
        private final String retryLabel;

        GradleHomeMode(String initialLabel, String retryLabel) {
            this.initialLabel = initialLabel;
            this.retryLabel = retryLabel;
        }
    }

    private record ProjectTestAttempt(
        String label,
        String gradleUserHome,
        ProjectTestStatus status,
        String output
    ) {
    }

    private ProjectTestRunner() {
    }

    static ProjectTestResult runProjectTests(Path projectRoot) throws IOException, InterruptedException {
        return runProjectTests(projectRoot, null);
    }

    static ProjectTestResult runProjectTests(Path projectRoot, String initScriptRelativePath) throws IOException, InterruptedException {
        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
        Path wrapper = resolveWrapper(normalizedProjectRoot);
        List<ProjectTestAttempt> attempts = new ArrayList<>();

        String externalGradleUserHome = configuredExternalGradleUserHome();
        if (externalGradleUserHome != null) {
            ProjectTestAttempt first = runProjectTestsOnce(
                normalizedProjectRoot,
                wrapper,
                externalGradleUserHome,
                GradleHomeMode.EXTERNAL_ENV.initialLabel,
                initScriptRelativePath
            );
            attempts.add(first);
            ProjectTestAttempt latest = first;
            if (isLockOrBootstrap(latest.status())) {
                safeSelfHealGradleHome(latest.gradleUserHome());
                deterministicBackoff();
                latest = runProjectTestsOnce(
                    normalizedProjectRoot,
                    wrapper,
                    externalGradleUserHome,
                    GradleHomeMode.EXTERNAL_ENV.retryLabel,
                    initScriptRelativePath
                );
                attempts.add(latest);
            }
            return finalizeAttempts(attempts, latest);
        }

        String isolatedGradleUserHome = normalizedProjectRoot.resolve(".bear-gradle-user-home").toString();
        ProjectTestAttempt latest = runProjectTestsOnce(
            normalizedProjectRoot,
            wrapper,
            isolatedGradleUserHome,
            GradleHomeMode.ISOLATED.initialLabel,
            initScriptRelativePath
        );
        attempts.add(latest);

        if (!isLockOrBootstrap(latest.status())) {
            return finalizeAttempts(attempts, latest);
        }

        safeSelfHealGradleHome(isolatedGradleUserHome);
        deterministicBackoff();

        if (isWindows()) {
            String userGradleUserHome = defaultUserGradleHome();
            latest = runProjectTestsOnce(
                normalizedProjectRoot,
                wrapper,
                userGradleUserHome,
                GradleHomeMode.USER_CACHE.initialLabel,
                initScriptRelativePath
            );
            attempts.add(latest);
            if (isLockOrBootstrap(latest.status())) {
                safeSelfHealGradleHome(userGradleUserHome);
                deterministicBackoff();
                latest = runProjectTestsOnce(
                    normalizedProjectRoot,
                    wrapper,
                    userGradleUserHome,
                    GradleHomeMode.USER_CACHE.retryLabel,
                    initScriptRelativePath
                );
                attempts.add(latest);
            }
            return finalizeAttempts(attempts, latest);
        }

        latest = runProjectTestsOnce(
            normalizedProjectRoot,
            wrapper,
            isolatedGradleUserHome,
            GradleHomeMode.ISOLATED.retryLabel,
            initScriptRelativePath
        );
        attempts.add(latest);
        if (isLockOrBootstrap(latest.status())) {
            String userGradleUserHome = defaultUserGradleHome();
            safeSelfHealGradleHome(userGradleUserHome);
            deterministicBackoff();
            latest = runProjectTestsOnce(
                normalizedProjectRoot,
                wrapper,
                userGradleUserHome,
                GradleHomeMode.USER_CACHE.initialLabel,
                initScriptRelativePath
            );
            attempts.add(latest);
        }
        return finalizeAttempts(attempts, latest);
    }

    private static void deterministicBackoff() throws InterruptedException {
        Thread.sleep(RETRY_BACKOFF_MILLIS);
    }

    private static ProjectTestAttempt runProjectTestsOnce(
        Path projectRoot,
        Path wrapper,
        String gradleUserHome,
        String attemptLabel,
        String initScriptRelativePath
    ) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd");
            command.add("/c");
            command.add(wrapper.toString());
        } else {
            command.add("sh");
            command.add(wrapper.toString());
        }
        command.add("--no-daemon");
        String normalizedInitScript = normalizeInitScriptRelativePath(initScriptRelativePath);
        if (normalizedInitScript != null) {
            command.add("-I");
            command.add(normalizedInitScript);
        }
        command.add("test");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);
        Map<String, String> environment = pb.environment();
        if (gradleUserHome != null && !gradleUserHome.isBlank()) {
            environment.put("GRADLE_USER_HOME", gradleUserHome);
        }
        Process process = pb.start();

        String output;
        try (InputStream in = process.getInputStream()) {
            boolean finished = process.waitFor(testTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return new ProjectTestAttempt(attemptLabel, gradleUserHome, ProjectTestStatus.TIMEOUT, output);
            }
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        ProjectTestStatus status = classifyProjectTestStatus(process.exitValue(), output, gradleUserHome);
        return new ProjectTestAttempt(attemptLabel, gradleUserHome, status, output);
    }

    private static String normalizeInitScriptRelativePath(String initScriptRelativePath) {
        if (initScriptRelativePath == null) {
            return null;
        }
        String normalized = initScriptRelativePath.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static ProjectTestStatus classifyProjectTestStatus(int exitValue, String output, String gradleUserHome) {
        if (exitValue == 0) {
            return ProjectTestStatus.PASSED;
        }
        if (isGradleWrapperLockOutput(output, gradleUserHome)) {
            return ProjectTestStatus.LOCKED;
        }
        if (isGradleWrapperBootstrapIoOutput(output, gradleUserHome)) {
            return ProjectTestStatus.BOOTSTRAP_IO;
        }
        if (firstSharedDepsViolationLine(output) != null) {
            return ProjectTestStatus.SHARED_DEPS_VIOLATION;
        }
        if (isInvariantViolationOutput(output)) {
            return ProjectTestStatus.INVARIANT_VIOLATION;
        }
        return ProjectTestStatus.FAILED;
    }

    private static ProjectTestResult finalizeAttempts(List<ProjectTestAttempt> attempts, ProjectTestAttempt latest) {
        String attemptTrail = attempts.stream().map(ProjectTestAttempt::label).reduce((a, b) -> a + "," + b).orElse("");
        return new ProjectTestResult(
            latest.status(),
            latest.output(),
            attemptTrail,
            firstLockLineAcrossAttempts(attempts),
            firstBootstrapLineAcrossAttempts(attempts),
            firstSharedDepsViolationLineAcrossAttempts(attempts),
            cacheModeForLabel(latest.label()),
            fallbackToUserCache(attempts)
        );
    }

    private static String cacheModeForLabel(String label) {
        if (label == null) {
            return "isolated";
        }
        if (label.startsWith("user-cache")) {
            return "user-cache";
        }
        if (label.startsWith("external-env")) {
            return "external-env";
        }
        return "isolated";
    }

    private static boolean fallbackToUserCache(List<ProjectTestAttempt> attempts) {
        boolean usedIsolated = attempts.stream().anyMatch(attempt -> attempt.label().startsWith("isolated"));
        boolean usedUserCache = attempts.stream().anyMatch(attempt -> attempt.label().startsWith("user-cache"));
        return usedIsolated && usedUserCache;
    }

    private static String firstLockLineAcrossAttempts(List<ProjectTestAttempt> attempts) {
        for (ProjectTestAttempt attempt : attempts) {
            String line = firstGradleLockLine(attempt.output(), attempt.gradleUserHome());
            if (line != null) {
                return line;
            }
        }
        return null;
    }

    private static String firstBootstrapLineAcrossAttempts(List<ProjectTestAttempt> attempts) {
        for (ProjectTestAttempt attempt : attempts) {
            String line = firstGradleBootstrapIoLine(attempt.output(), attempt.gradleUserHome());
            if (line != null) {
                return line;
            }
        }
        return null;
    }

    private static String firstSharedDepsViolationLineAcrossAttempts(List<ProjectTestAttempt> attempts) {
        for (ProjectTestAttempt attempt : attempts) {
            String line = firstSharedDepsViolationLine(attempt.output());
            if (line != null) {
                return line;
            }
        }
        return null;
    }

    private static boolean isLockOrBootstrap(ProjectTestStatus status) {
        return status == ProjectTestStatus.LOCKED || status == ProjectTestStatus.BOOTSTRAP_IO;
    }

    private static String configuredExternalGradleUserHome() {
        String override = System.getProperty("bear.cli.test.gradleUserHomeOverride");
        if (override != null) {
            String trimmed = override.trim();
            if ("NONE".equalsIgnoreCase(trimmed)) {
                return null;
            }
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        String env = System.getenv("GRADLE_USER_HOME");
        if (env == null || env.isBlank()) {
            return null;
        }
        return env.trim();
    }

    private static String defaultUserGradleHome() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return Path.of(".gradle").toString();
        }
        return Path.of(userHome, ".gradle").toString();
    }

    static void safeSelfHealGradleHome(String gradleUserHome) {
        if (gradleUserHome == null || gradleUserHome.isBlank()) {
            return;
        }
        try {
            Path gradleHomeRoot = Path.of(gradleUserHome).toAbsolutePath().normalize();
            if (!Files.isDirectory(gradleHomeRoot)) {
                return;
            }

            Instant now = Instant.now();
            List<Path> deleteTargets = new ArrayList<>();
            collectWrapperDistTargets(gradleHomeRoot, deleteTargets, now);
            collectTmpTargets(gradleHomeRoot, deleteTargets, now);
            collectGroovyDslTempTargets(gradleHomeRoot, deleteTargets, now);

            deleteTargets.sort((a, b) -> normalizePathForComparison(a).compareTo(normalizePathForComparison(b)));
            for (Path target : deleteTargets) {
                Path normalized = target.toAbsolutePath().normalize();
                if (!isPathInsideRoot(normalized, gradleHomeRoot)) {
                    continue;
                }
                Files.deleteIfExists(normalized);
            }
        } catch (Exception ignored) {
            // Safe self-heal is best effort and must not hide primary test result classification.
        }
    }

    private static void collectWrapperDistTargets(Path gradleHomeRoot, List<Path> deleteTargets, Instant now) throws IOException {
        Path distsRoot = gradleHomeRoot.resolve("wrapper").resolve("dists").normalize();
        if (!Files.isDirectory(distsRoot)) {
            return;
        }
        try (var stream = Files.walk(distsRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!(fileName.endsWith(".zip.lck") || fileName.endsWith(".zip.part"))) {
                    return;
                }
                if (isStaleArtifact(path, gradleHomeRoot, now)) {
                    deleteTargets.add(path);
                }
            });
        }
    }

    private static void collectTmpTargets(Path gradleHomeRoot, List<Path> deleteTargets, Instant now) throws IOException {
        Path tmpRoot = gradleHomeRoot.resolve(".tmp");
        if (!Files.isDirectory(tmpRoot)) {
            return;
        }
        try (var stream = Files.walk(tmpRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String rel = normalizePathForComparison(gradleHomeRoot.relativize(path));
                if (!isTmpTempRelative(rel)) {
                    return;
                }
                if (isStaleArtifact(path, gradleHomeRoot, now)) {
                    deleteTargets.add(path);
                }
            });
        }
    }

    private static void collectGroovyDslTempTargets(Path gradleHomeRoot, List<Path> deleteTargets, Instant now) throws IOException {
        Path cachesRoot = gradleHomeRoot.resolve("caches");
        if (!Files.isDirectory(cachesRoot)) {
            return;
        }
        try (var stream = Files.walk(cachesRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String rel = normalizePathForComparison(gradleHomeRoot.relativize(path));
                if (!isGroovyDslInstrumentedTempRelative(rel)) {
                    return;
                }
                if (isStaleArtifact(path, gradleHomeRoot, now)) {
                    deleteTargets.add(path);
                }
            });
        }
    }

    private static boolean isStaleArtifact(Path artifact, Path gradleHomeRoot, Instant now) {
        try {
            Path normalized = artifact.toAbsolutePath().normalize();
            if (!isPathInsideRoot(normalized, gradleHomeRoot)) {
                return false;
            }
            FileTime modified = Files.getLastModifiedTime(normalized);
            if (modified == null) {
                return false;
            }
            Instant modifiedAt = modified.toInstant();
            if (modifiedAt == null || modifiedAt.isAfter(now)) {
                return false;
            }
            return Duration.between(modifiedAt, now).compareTo(STALE_ARTIFACT_THRESHOLD) >= 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isGradleWrapperLockOutput(String output) {
        return isGradleWrapperLockOutput(output, null);
    }

    static boolean isGradleWrapperLockOutput(String output, String gradleUserHome) {
        String lower = CliText.normalizeLf(output).toLowerCase(Locale.ROOT);
        if (lower.contains(".zip.lck")) {
            return true;
        }
        if (lower.contains("gradlewrappermain") && lower.contains("access is denied")) {
            return true;
        }
        if (containsGradleScopedTempDeleteFailure(output, gradleUserHome)) {
            return true;
        }
        return lower.contains("project_test_gradle_lock_simulated");
    }

    static boolean isGradleWrapperBootstrapIoOutput(String output) {
        return isGradleWrapperBootstrapIoOutput(output, null);
    }

    static boolean isGradleWrapperBootstrapIoOutput(String output, String gradleUserHome) {
        String lower = CliText.normalizeLf(output).toLowerCase(Locale.ROOT);
        if (lower.contains("project_test_gradle_bootstrap_simulated")) {
            return true;
        }

        boolean mentionsGradleZip = lower.contains("gradle-") && lower.contains("-bin.zip");
        if (mentionsGradleZip
            && (lower.contains("nosuchfileexception")
            || lower.contains("filenotfoundexception")
            || lower.contains("zipexception")
            || lower.contains("error in opening zip file")
            || lower.contains("end header not found")
            || lower.contains("cannot unzip")
            || lower.contains("unable to unzip")
            || lower.contains("unable to install gradle"))) {
            return true;
        }

        return lower.contains("error in opening zip file")
            || lower.contains("end header not found")
            || lower.contains("project_test_gradle_bootstrap");
    }

    static String firstGradleLockLine(String output) {
        return firstGradleLockLine(output, null);
    }

    static String firstGradleLockLine(String output, String gradleUserHome) {
        for (String line : CliText.normalizeLf(output).lines().toList()) {
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

    static String firstGradleBootstrapIoLine(String output) {
        return firstGradleBootstrapIoLine(output, null);
    }

    static String firstGradleBootstrapIoLine(String output, String gradleUserHome) {
        for (String line : CliText.normalizeLf(output).lines().toList()) {
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

    private static boolean containsGradleScopedTempDeleteFailure(String output, String gradleUserHome) {
        for (String line : CliText.normalizeLf(output).lines().toList()) {
            if (isGradleScopedTempDeleteFailureLine(line, gradleUserHome)) {
                return true;
            }
        }
        return false;
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
        Path failedPath;
        try {
            failedPath = Path.of(parsedPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            return false;
        }
        Path gradleHomeRoot;
        try {
            gradleHomeRoot = Path.of(gradleUserHome).toAbsolutePath().normalize();
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

    private static boolean isPathInsideRoot(Path child, Path root) {
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

    private static String normalizePathForComparison(Path path) {
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
        if (isWindows()) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    static String firstRelevantProjectTestFailureLine(String output) {
        List<String> lines = CliText.normalizeLf(output).lines()
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

    static boolean isInvariantViolationOutput(String output) {
        return firstInvariantViolationLine(output) != null;
    }

    static String firstSharedDepsViolationLine(String output) {
        for (String line : CliText.normalizeLf(output).lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith(SHARED_DEPS_VIOLATION_MARKER_PREFIX)) {
                return trimmed;
            }
        }
        return null;
    }

    static String firstInvariantViolationLine(String output) {
        for (String line : CliText.normalizeLf(output).lines().toList()) {
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

    static String projectTestDetail(String base, String firstLine, String tail) {
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

    static Path resolveWrapper(Path projectRoot) throws IOException {
        if (isWindows()) {
            Path wrapper = projectRoot.resolve("gradlew.bat");
            if (!Files.isRegularFile(wrapper)) {
                throw new IOException("PROJECT_TEST_WRAPPER_MISSING: expected " + wrapper);
            }
            return wrapper;
        }

        Path wrapper = projectRoot.resolve("gradlew");
        if (!Files.isRegularFile(wrapper)) {
            throw new IOException("PROJECT_TEST_WRAPPER_MISSING: expected " + wrapper);
        }
        return wrapper;
    }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    static int testTimeoutSeconds() {
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
}
