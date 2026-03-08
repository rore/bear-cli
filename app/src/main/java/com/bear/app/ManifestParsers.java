package com.bear.app;

import com.bear.kernel.target.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ManifestParsers {
    private static final String SHARED_GOVERNED_ROOT = "src/main/java/blocks/_shared";

    private ManifestParsers() {
    }

    static BoundaryManifest parseManifest(Path path) throws IOException, ManifestParseException {
        String json = Files.readString(path, StandardCharsets.UTF_8).trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new ManifestParseException("MALFORMED_JSON");
        }
        String schemaVersion = extractRequiredString(json, "schemaVersion");
        String target = extractRequiredString(json, "target");
        String block = extractRequiredString(json, "block");
        String irHash = extractRequiredString(json, "irHash");
        String generatorVersion = extractRequiredString(json, "generatorVersion");

        String capabilitiesPayload = extractRequiredArrayPayload(json, "capabilities");
        String allowedDepsPayload = extractOptionalArrayPayload(json, "allowedDeps");
        String invariantsPayload = extractRequiredArrayPayload(json, "invariants");
        Map<String, TreeSet<String>> capabilities = parseCapabilities(capabilitiesPayload);
        Map<String, String> allowedDeps = parseAllowedDeps(allowedDepsPayload);
        TreeSet<String> invariants = parseInvariants(invariantsPayload);
        return new BoundaryManifest(schemaVersion, target, block, irHash, generatorVersion, capabilities, allowedDeps, invariants);
    }

    static WiringManifest parseWiringManifest(Path path) throws IOException, ManifestParseException {
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
        if (roots.isEmpty()) {
            throw new ManifestParseException("INVALID_GOVERNED_SOURCE_ROOTS");
        }
        if (roots.size() < 2) {
            throw new ManifestParseException("INVALID_GOVERNED_SOURCE_ROOTS");
        }
        HashSet<String> seen = new HashSet<>();
        for (String root : roots) {
            validateRepoRelativeRootPath(root, "governedSourceRoots");
            if (!seen.add(root)) {
                throw new ManifestParseException("INVALID_GOVERNED_SOURCE_ROOTS");
            }
        }
        if (!blockRootSourceDir.equals(roots.get(0))) {
            throw new ManifestParseException("INVALID_GOVERNED_SOURCE_ROOTS");
        }
        if (!SHARED_GOVERNED_ROOT.equals(roots.get(1))) {
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
        if (value == null || value.isBlank()) {
            throw new ManifestParseException("INVALID_ROOT_PATH_" + field);
        }
        if (value.contains("\\")) {
            throw new ManifestParseException("INVALID_ROOT_PATH_" + field);
        }
        if (value.endsWith("/")) {
            throw new ManifestParseException("INVALID_ROOT_PATH_" + field);
        }
        if (value.startsWith("/") || value.startsWith("./")) {
            throw new ManifestParseException("INVALID_ROOT_PATH_" + field);
        }
        if (value.matches("^[A-Za-z]:.*")) {
            throw new ManifestParseException("INVALID_ROOT_PATH_" + field);
        }
        for (String segment : value.split("/")) {
            if ("..".equals(segment) || segment.isBlank()) {
                throw new ManifestParseException("INVALID_ROOT_PATH_" + field);
            }
        }
    }

    static String extractRequiredString(String json, String key) throws ManifestParseException {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\":\"((?:\\\\.|[^\\\\\"])*)\"").matcher(json);
        if (!m.find()) {
            throw new ManifestParseException("MISSING_KEY_" + key);
        }
        return jsonUnescape(m.group(1));
    }

    static String extractRequiredArrayPayload(String json, String key) throws ManifestParseException {
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

    static String extractOptionalString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\":\"((?:\\\\.|[^\\\\\"])*)\"").matcher(json);
        if (!m.find()) {
            return null;
        }
        return jsonUnescape(m.group(1));
    }

    static String extractOptionalArrayPayload(String json, String key) throws ManifestParseException {
        int keyIdx = json.indexOf("\"" + key + "\":[");
        if (keyIdx < 0) {
            return null;
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

    static Map<String, TreeSet<String>> parseCapabilities(String payload) throws ManifestParseException {
        Map<String, TreeSet<String>> capabilities = new TreeMap<>();
        if (payload.isBlank()) {
            return capabilities;
        }

        Matcher m = Pattern.compile("\\{\"name\":\"((?:\\\\.|[^\\\\\"])*)\",\"ops\":\\[([^\\]]*)\\]\\}").matcher(payload);
        int count = 0;
        while (m.find()) {
            count++;
            String name = jsonUnescape(m.group(1));
            TreeSet<String> ops = new TreeSet<>();
            String opsPayload = m.group(2);
            if (!opsPayload.isBlank()) {
                Matcher opMatcher = Pattern.compile("\"((?:\\\\.|[^\\\\\"])*)\"").matcher(opsPayload);
                while (opMatcher.find()) {
                    ops.add(jsonUnescape(opMatcher.group(1)));
                }
            }
            capabilities.put(name, ops);
        }

        if (count == 0) {
            throw new ManifestParseException("INVALID_CAPABILITIES");
        }
        return capabilities;
    }

    static TreeSet<String> parseInvariants(String payload) throws ManifestParseException {
        TreeSet<String> invariants = new TreeSet<>();
        if (payload.isBlank()) {
            return invariants;
        }

        Matcher m = Pattern.compile("\\{\"kind\":\"((?:\\\\.|[^\\\\\"])*)\",\"field\":\"((?:\\\\.|[^\\\\\"])*)\"\\}")
            .matcher(payload);
        int count = 0;
        while (m.find()) {
            count++;
            String kind = jsonUnescape(m.group(1));
            String field = jsonUnescape(m.group(2));
            invariants.add(kind + ":" + field);
        }

        if (count == 0) {
            throw new ManifestParseException("INVALID_INVARIANTS");
        }
        return invariants;
    }

    static Map<String, String> parseAllowedDeps(String payload) throws ManifestParseException {
        Map<String, String> allowedDeps = new TreeMap<>();
        if (payload == null || payload.isBlank()) {
            return allowedDeps;
        }

        Matcher m = Pattern.compile("\\{\\\"ga\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\",\\\"version\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\"\\}")
            .matcher(payload);
        int count = 0;
        while (m.find()) {
            count++;
            allowedDeps.put(jsonUnescape(m.group(1)), jsonUnescape(m.group(2)));
        }
        if (count == 0) {
            throw new ManifestParseException("INVALID_ALLOWED_DEPS");
        }
        return allowedDeps;
    }

    static List<String> parseStringArray(String payload) throws ManifestParseException {
        if (payload.isBlank()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\\\\\"])*)\"").matcher(payload);
        while (matcher.find()) {
            values.add(jsonUnescape(matcher.group(1)));
        }
        if (values.isEmpty()) {
            throw new ManifestParseException("INVALID_STRING_ARRAY");
        }
        return List.copyOf(values);
    }

    static String jsonUnescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 'n') {
                    out.append('\n');
                } else if (next == 'r') {
                    out.append('\r');
                } else if (next == 't') {
                    out.append('\t');
                } else {
                    out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
