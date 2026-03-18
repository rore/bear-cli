package com.bear.kernel.target;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TargetManifestParsers {
    private static final String SHARED_GOVERNED_ROOT = "src/main/java/blocks/_shared";

    private TargetManifestParsers() {
    }

    public static WiringManifest parseWiringManifest(Path path) throws IOException, ManifestParseException {
        String json = Files.readString(path, StandardCharsets.UTF_8).trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new ManifestParseException("MALFORMED_JSON");
        }
        String schemaVersion = extractRequiredString(json, "schemaVersion");
        if (!"v3".equals(schemaVersion)) {
            throw new ManifestParseException("UNSUPPORTED_WIRING_SCHEMA_VERSION");
        }
        String blockKey = extractRequiredString(json, "blockKey");
        String entrypointFqcn = extractRequiredString(json, "entrypointFqcn");
        String logicInterfaceFqcn = extractRequiredString(json, "logicInterfaceFqcn");
        String implFqcn = extractRequiredString(json, "implFqcn");
        String implSourcePath = extractRequiredString(json, "implSourcePath");
        String blockRootSourceDir = extractRequiredString(json, "blockRootSourceDir");
        String governedSourceRootsPayload = extractRequiredArrayPayload(json, "governedSourceRoots");
        String requiredEffectPortsPayload = extractRequiredArrayPayload(json, "requiredEffectPorts");
        String constructorPortParamsPayload = extractRequiredArrayPayload(json, "constructorPortParams");
        String logicRequiredPortsPayload = extractRequiredArrayPayload(json, "logicRequiredPorts");
        String wrapperOwnedSemanticPortsPayload = extractRequiredArrayPayload(json, "wrapperOwnedSemanticPorts");
        String wrapperOwnedSemanticChecksPayload = extractRequiredArrayPayload(json, "wrapperOwnedSemanticChecks");
        String blockPortBindingsPayload = extractRequiredArrayPayload(json, "blockPortBindings");

        validateRepoRelativeRootPath(blockRootSourceDir, "blockRootSourceDir");
        List<String> governedSourceRoots = parseStringArray(governedSourceRootsPayload);
        validateGovernedSourceRoots(governedSourceRoots, blockRootSourceDir);
        List<String> requiredEffectPorts = parseStringArray(requiredEffectPortsPayload);
        List<String> constructorPortParams = parseStringArray(constructorPortParamsPayload);
        List<String> logicRequiredPorts = parseStringArray(logicRequiredPortsPayload);
        List<String> wrapperOwnedSemanticPorts = parseStringArray(wrapperOwnedSemanticPortsPayload);
        List<String> wrapperOwnedSemanticChecks = parseStringArray(wrapperOwnedSemanticChecksPayload);
        List<BlockPortBinding> blockPortBindings = parseBlockPortBindings(blockPortBindingsPayload);

        return new WiringManifest(
            schemaVersion,
            blockKey,
            entrypointFqcn,
            logicInterfaceFqcn,
            implFqcn,
            implSourcePath,
            blockRootSourceDir,
            governedSourceRoots,
            requiredEffectPorts,
            constructorPortParams,
            logicRequiredPorts,
            wrapperOwnedSemanticPorts,
            wrapperOwnedSemanticChecks,
            blockPortBindings
        );
    }

    public static String extractRequiredString(String json, String key) throws ManifestParseException {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\":\"((?:\\\\.|[^\\\\\"])*)\"").matcher(json);
        if (!m.find()) {
            throw new ManifestParseException("MISSING_KEY_" + key);
        }
        return jsonUnescape(m.group(1));
    }

    public static String extractRequiredArrayPayload(String json, String key) throws ManifestParseException {
        int keyIdx = json.indexOf("\"" + key + "\":[");
        if (keyIdx < 0) {
            throw new ManifestParseException("MISSING_KEY_" + key);
        }
        int start = json.indexOf('[', keyIdx);
        if (start < 0) {
            throw new ManifestParseException("MALFORMED_ARRAY_" + key);
        }
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start + 1, i);
                }
            }
        }
        throw new ManifestParseException("MALFORMED_ARRAY_" + key);
    }

    public static String jsonUnescape(String raw) {
        return raw
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
    }

    private static List<BlockPortBinding> parseBlockPortBindings(String payload) throws ManifestParseException {
        if (payload.isBlank()) {
            return List.of();
        }
        ArrayList<BlockPortBinding> bindings = new ArrayList<>();
        for (String rawObject : splitObjectArray(payload)) {
            String objectJson = rawObject.trim();
            String port = extractRequiredString(objectJson, "port");
            String targetBlock = extractRequiredString(objectJson, "targetBlock");
            String targetOpsPayload = extractRequiredArrayPayload(objectJson, "targetOps");
            String portInterfaceFqcn = extractRequiredString(objectJson, "portInterfaceFqcn");
            String expectedClientImplFqcn = extractRequiredString(objectJson, "expectedClientImplFqcn");
            List<String> targetOps = parseStringArray(targetOpsPayload);
            bindings.add(new BlockPortBinding(port, targetBlock, targetOps, portInterfaceFqcn, expectedClientImplFqcn));
        }
        if (bindings.isEmpty()) {
            throw new ManifestParseException("INVALID_BLOCK_PORT_BINDINGS");
        }
        return List.copyOf(bindings);
    }

    private static List<String> splitObjectArray(String payload) throws ManifestParseException {
        ArrayList<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < payload.length(); i++) {
            char c = payload.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
                continue;
            }
            if (c == '}') {
                if (depth <= 0) {
                    throw new ManifestParseException("INVALID_BLOCK_PORT_BINDINGS");
                }
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(payload.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        if (depth != 0 || inString) {
            throw new ManifestParseException("INVALID_BLOCK_PORT_BINDINGS");
        }
        return objects;
    }

    private static void validateGovernedSourceRoots(List<String> roots, String blockRootSourceDir) throws ManifestParseException {
        if (roots.isEmpty() || roots.size() < 2) {
            throw new ManifestParseException("INVALID_GOVERNED_SOURCE_ROOTS");
        }
        HashSet<String> seen = new HashSet<>();
        for (String root : roots) {
            validateRepoRelativeRootPath(root, "governedSourceRoots");
            if (!seen.add(root)) {
                throw new ManifestParseException("INVALID_GOVERNED_SOURCE_ROOTS");
            }
        }
        if (!blockRootSourceDir.equals(roots.get(0)) || !SHARED_GOVERNED_ROOT.equals(roots.get(1))) {
            throw new ManifestParseException("INVALID_GOVERNED_SOURCE_ROOTS");
        }
        if (roots.size() > 2) {
            ArrayList<String> tail = new ArrayList<>(roots.subList(2, roots.size()));
            ArrayList<String> sortedTail = new ArrayList<>(tail);
            sortedTail.sort(String::compareTo);
            if (!tail.equals(sortedTail)) {
                throw new ManifestParseException("INVALID_GOVERNED_SOURCE_ROOTS");
            }
        }
    }

    private static void validateRepoRelativeRootPath(String value, String field) throws ManifestParseException {
        if (value == null || value.isBlank() || value.contains("\\") || value.endsWith("/") || value.startsWith("/") || value.startsWith("./") || value.matches("^[A-Za-z]:.*")) {
            throw new ManifestParseException("INVALID_ROOT_PATH_" + field);
        }
        for (String segment : value.split("/")) {
            if ("..".equals(segment) || segment.isBlank()) {
                throw new ManifestParseException("INVALID_ROOT_PATH_" + field);
            }
        }
    }

    private static List<String> parseStringArray(String payload) throws ManifestParseException {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\\\\\"])*)\"").matcher(payload);
        int cursor = 0;
        while (matcher.find()) {
            String between = payload.substring(cursor, matcher.start()).trim();
            if (!between.isEmpty() && !",".equals(between)) {
                throw new ManifestParseException("INVALID_STRING_ARRAY");
            }
            values.add(jsonUnescape(matcher.group(1)));
            cursor = matcher.end();
        }
        String trailing = payload.substring(cursor).trim();
        if (!trailing.isEmpty()) {
            throw new ManifestParseException("INVALID_STRING_ARRAY");
        }
        return List.copyOf(values);
    }
}

