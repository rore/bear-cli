package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.policy.SharedAllowedDepsPolicyException;
import com.bear.kernel.policy.SharedAllowedDepsPolicyParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CheckContainmentStage {
    private static final IrPipeline IR_PIPELINE = new DefaultIrPipeline();
    private static final String CONTAINMENT_REQUIRED_PATH = "build/generated/bear/config/containment-required.json";
    private static final String CONTAINMENT_ENTRYPOINT_PATH = "build/generated/bear/gradle/bear-containment.gradle";
    private static final String CONTAINMENT_SKIP_REASON = "no_selected_blocks_with_impl_allowedDeps";
    private static final String CONTAINMENT_MARKER_DIR = "build/bear/containment";
    private static final String SHARED_POLICY_PATH = "bear-policy/_shared.policy.yaml";
    private static final String SHARED_SOURCE_ROOT = "src/main/java/blocks/_shared";

    private CheckContainmentStage() {
    }

    static CheckResult preflightContainmentIfRequired(
        Path projectRoot,
        List<String> diagnostics,
        boolean considerContainmentSurfaces
    ) throws IOException {
        if (!considerContainmentSurfaces) {
            return null;
        }
        Path sharedPolicyPath = projectRoot.resolve(SHARED_POLICY_PATH);
        if (Files.isRegularFile(sharedPolicyPath)) {
            SharedAllowedDepsPolicyParser parser = new SharedAllowedDepsPolicyParser();
            try {
                parser.parse(sharedPolicyPath);
            } catch (SharedAllowedDepsPolicyException e) {
                String line = "check: POLICY_INVALID: " + e.reasonCode();
                diagnostics.add(line);
                return checkFailure(
                    CliCodes.EXIT_VALIDATION,
                    diagnostics,
                    "VALIDATION",
                    CliCodes.POLICY_INVALID,
                    SHARED_POLICY_PATH,
                    "Fix `bear-policy/_shared.policy.yaml` (version/scope/schema and pinned allowedDeps) and rerun `bear check`.",
                    line
                );
            }
        }
        Path gradlew = projectRoot.resolve("gradlew");
        Path gradlewBat = projectRoot.resolve("gradlew.bat");
        boolean hasWrapper = Files.isRegularFile(gradlew) || Files.isRegularFile(gradlewBat);
        if (!hasWrapper) {
            String line = "check: CONTAINMENT_REQUIRED: UNSUPPORTED_TARGET: missing Gradle wrapper";
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_IO,
                diagnostics,
                "CONTAINMENT",
                CliCodes.CONTAINMENT_UNSUPPORTED_TARGET,
                "project.root",
                "Containment enforcement in P2 requires Java+Gradle with wrapper at project root; remove `impl.allowedDeps`/`bear-policy/_shared.policy.yaml` scope usage or use supported target, then rerun `bear check`.",
                line
            );
        }

        Path entrypoint = projectRoot.resolve(CONTAINMENT_ENTRYPOINT_PATH);
        if (!Files.isRegularFile(entrypoint)) {
            String line = "drift: MISSING_BASELINE: " + CONTAINMENT_ENTRYPOINT_PATH;
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_DRIFT,
                diagnostics,
                "DRIFT",
                CliCodes.DRIFT_MISSING_BASELINE,
                CONTAINMENT_ENTRYPOINT_PATH,
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                line
            );
        }

        Path required = projectRoot.resolve(CONTAINMENT_REQUIRED_PATH);
        if (!Files.isRegularFile(required)) {
            String line = "drift: MISSING_BASELINE: " + CONTAINMENT_REQUIRED_PATH;
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_DRIFT,
                diagnostics,
                "DRIFT",
                CliCodes.DRIFT_MISSING_BASELINE,
                CONTAINMENT_REQUIRED_PATH,
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                line
            );
        }
        try {
            parseContainmentRequiredIndex(required);
        } catch (ManifestParseException e) {
            String line = "drift: CHANGED: " + CONTAINMENT_REQUIRED_PATH;
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_DRIFT,
                diagnostics,
                "DRIFT",
                CliCodes.DRIFT_DETECTED,
                CONTAINMENT_REQUIRED_PATH,
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                line
            );
        }

        return null;
    }

    static CheckResult verifyContainmentMarkersIfRequired(
        Path projectRoot,
        List<String> diagnostics,
        boolean considerContainmentSurfaces
    ) throws IOException {
        if (!considerContainmentSurfaces) {
            return null;
        }

        Path required = projectRoot.resolve(CONTAINMENT_REQUIRED_PATH);
        if (!Files.isRegularFile(required)) {
            String line = "drift: MISSING_BASELINE: " + CONTAINMENT_REQUIRED_PATH;
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_DRIFT,
                diagnostics,
                "DRIFT",
                CliCodes.DRIFT_MISSING_BASELINE,
                CONTAINMENT_REQUIRED_PATH,
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                line
            );
        }
        ContainmentRequiredIndex requiredIndex;
        try {
            requiredIndex = parseContainmentRequiredIndex(required);
        } catch (ManifestParseException e) {
            String line = "drift: CHANGED: " + CONTAINMENT_REQUIRED_PATH;
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_DRIFT,
                diagnostics,
                "DRIFT",
                CliCodes.DRIFT_DETECTED,
                CONTAINMENT_REQUIRED_PATH,
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                line
            );
        }

        Path marker = projectRoot.resolve(CONTAINMENT_MARKER_DIR + "/applied.marker");
        if (!Files.isRegularFile(marker)) {
            String line = "check: CONTAINMENT_REQUIRED: MARKER_MISSING: " + CONTAINMENT_MARKER_DIR + "/applied.marker";
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_IO,
                diagnostics,
                "CONTAINMENT",
                CliCodes.CONTAINMENT_NOT_VERIFIED,
                CONTAINMENT_MARKER_DIR + "/applied.marker",
                "Run Gradle build once so BEAR containment marker tasks write markers, then rerun `bear check`.",
                line
            );
        }

        String expectedHash = sha256Hex(Files.readAllBytes(required));
        String markerHash = readMarkerHash(marker);
        List<String> markerBlocks = readMarkerBlocks(marker);
        List<String> expectedBlocks = requiredIndex.blockKeys();
        if (markerHash == null || !markerHash.equals(expectedHash) || markerBlocks == null || !markerBlocks.equals(expectedBlocks)) {
            String line = "check: CONTAINMENT_REQUIRED: MARKER_STALE: " + CONTAINMENT_MARKER_DIR + "/applied.marker";
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_IO,
                diagnostics,
                "CONTAINMENT",
                CliCodes.CONTAINMENT_NOT_VERIFIED,
                CONTAINMENT_MARKER_DIR + "/applied.marker",
                "Run Gradle build once after BEAR compile so containment markers refresh, then rerun `bear check`.",
                line
            );
        }

        for (String blockKey : expectedBlocks) {
            String blockMarkerRel = CONTAINMENT_MARKER_DIR + "/" + blockKey + ".applied.marker";
            Path blockMarker = projectRoot.resolve(blockMarkerRel);
            if (!Files.isRegularFile(blockMarker)) {
                String line = "check: CONTAINMENT_REQUIRED: BLOCK_MARKER_MISSING: " + blockMarkerRel;
                diagnostics.add(line);
                return checkFailure(
                    CliCodes.EXIT_IO,
                    diagnostics,
                    "CONTAINMENT",
                    CliCodes.CONTAINMENT_NOT_VERIFIED,
                    blockMarkerRel,
                    "Run Gradle build once so BEAR containment marker tasks write markers, then rerun `bear check`.",
                    line
                );
            }
            String blockMarkerHash = readMarkerHash(blockMarker);
            String markerBlockKey = readMarkerBlockKey(blockMarker);
            if (blockMarkerHash == null || !expectedHash.equals(blockMarkerHash) || markerBlockKey == null || !blockKey.equals(markerBlockKey)) {
                String line = "check: CONTAINMENT_REQUIRED: BLOCK_MARKER_STALE: " + blockMarkerRel;
                diagnostics.add(line);
                return checkFailure(
                    CliCodes.EXIT_IO,
                    diagnostics,
                    "CONTAINMENT",
                    CliCodes.CONTAINMENT_NOT_VERIFIED,
                    blockMarkerRel,
                    "Run Gradle build once after BEAR compile so containment markers refresh, then rerun `bear check`.",
                    line
                );
            }
        }

        return null;
    }

    static boolean sharedContainmentInScope(Path projectRoot) {
        return Files.isRegularFile(projectRoot.resolve(SHARED_POLICY_PATH)) || hasSharedJavaSources(projectRoot);
    }

    static boolean blockDeclaresAllowedDeps(Path irFile) {
        try {
            BearIr normalized = IR_PIPELINE.parseValidateNormalize(irFile);
            return hasAllowedDeps(normalized);
        } catch (Exception ignored) {
            return false;
        }
    }

    static String containmentSkipInfoLine(String projectRootLabel, Path projectRoot, boolean considerContainmentSurfaces) {
        return maybeContainmentSkipInfo(projectRootLabel, projectRoot, considerContainmentSurfaces);
    }

    private static String maybeContainmentSkipInfo(String projectRootLabel, Path projectRoot, boolean considerContainmentSurfaces) {
        if (considerContainmentSurfaces) {
            return null;
        }
        Path required = projectRoot.resolve(CONTAINMENT_REQUIRED_PATH);
        if (!Files.isRegularFile(required)) {
            return null;
        }
        try {
            ContainmentRequiredIndex index = parseContainmentRequiredIndex(required);
            if (index.blockKeys().isEmpty()) {
                return null;
            }
            return "check: INFO: CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION: projectRoot="
                + projectRootLabel
                + ": reason="
                + CONTAINMENT_SKIP_REASON;
        } catch (Exception ignored) {
            return null;
        }
    }

    static boolean hasAllowedDeps(BearIr ir) {
        return ir.block().impl() != null
            && ir.block().impl().allowedDeps() != null
            && !ir.block().impl().allowedDeps().isEmpty();
    }

    private static boolean hasSharedJavaSources(Path projectRoot) {
        Path sharedRoot = projectRoot.resolve(SHARED_SOURCE_ROOT);
        if (!Files.isDirectory(sharedRoot)) {
            return false;
        }
        try (var stream = Files.walk(sharedRoot)) {
            return stream
                .filter(Files::isRegularFile)
                .anyMatch(path -> path.getFileName().toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }

    private static String readMarkerHash(Path markerFile) throws IOException {
        String content = CliText.normalizeLf(Files.readString(markerFile, StandardCharsets.UTF_8));
        for (String line : content.lines().toList()) {
            if (line.startsWith("hash=")) {
                String hash = line.substring("hash=".length()).trim();
                return hash.isEmpty() ? null : hash;
            }
        }
        return null;
    }

    private static List<String> readMarkerBlocks(Path markerFile) throws IOException {
        String content = CliText.normalizeLf(Files.readString(markerFile, StandardCharsets.UTF_8));
        for (String line : content.lines().toList()) {
            if (!line.startsWith("blocks=")) {
                continue;
            }
            String raw = line.substring("blocks=".length()).trim();
            if (raw.isEmpty()) {
                return List.of();
            }
            ArrayList<String> blocks = new ArrayList<>();
            Set<String> dedupe = new java.util.HashSet<>();
            for (String token : raw.split(",")) {
                String value = token.trim();
                if (value.isEmpty()) {
                    return null;
                }
                if (!dedupe.add(value)) {
                    return null;
                }
                blocks.add(value);
            }
            return List.copyOf(blocks);
        }
        return null;
    }

    private static String readMarkerBlockKey(Path markerFile) throws IOException {
        String content = CliText.normalizeLf(Files.readString(markerFile, StandardCharsets.UTF_8));
        for (String line : content.lines().toList()) {
            if (line.startsWith("block=")) {
                String value = line.substring("block=".length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private static ContainmentRequiredIndex parseContainmentRequiredIndex(Path requiredFile) throws IOException, ManifestParseException {
        String json = Files.readString(requiredFile, StandardCharsets.UTF_8).trim();
        if (json.isEmpty() || !json.startsWith("{") || !json.endsWith("}")) {
            throw new ManifestParseException("MALFORMED_JSON");
        }
        String blocksPayload = ManifestParsers.extractRequiredArrayPayload(json, "blocks");
        TreeSet<String> blockKeys = new TreeSet<>();
        if (!blocksPayload.isBlank()) {
            Matcher matcher = Pattern
                .compile("\\{\\\"blockKey\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\"")
                .matcher(blocksPayload);
            while (matcher.find()) {
                blockKeys.add(ManifestParsers.jsonUnescape(matcher.group(1)));
            }
            if (blockKeys.isEmpty()) {
                throw new ManifestParseException("INVALID_CONTAINMENT_REQUIRED_BLOCKS");
            }
        }
        return new ContainmentRequiredIndex(List.copyOf(blockKeys));
    }

    private static CheckResult checkFailure(
        int exitCode,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail
    ) {
        return new CheckResult(
            exitCode,
            List.of(),
            List.copyOf(stderrLines),
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail
        );
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record ContainmentRequiredIndex(List<String> blockKeys) {
    }
}
