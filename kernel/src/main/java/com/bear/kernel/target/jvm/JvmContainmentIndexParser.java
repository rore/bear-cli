package com.bear.kernel.target.jvm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JvmContainmentIndexParser {
    Map<String, JvmTarget.ContainmentBlockSpec> readContainmentIndex(Path indexFile) throws IOException {
        Map<String, JvmTarget.ContainmentBlockSpec> index = new TreeMap<>();
        if (!Files.isRegularFile(indexFile)) {
            return index;
        }
        String json = Files.readString(indexFile, StandardCharsets.UTF_8).trim();
        if (json.isEmpty() || !json.startsWith("{") || !json.endsWith("}")) {
            return index;
        }
        String blocksPayload;
        try {
            blocksPayload = extractRequiredArrayPayload(json, "blocks");
        } catch (IllegalStateException e) {
            return index;
        }

        Matcher blockMatcher = Pattern
            .compile(
                "\\{\\\"blockKey\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\",\\\"implDir\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\",(?:\\\"legacyImplDir\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\",)?\\\"allowedDeps\\\":\\[(.*?)\\]\\}",
                Pattern.DOTALL
            )
            .matcher(blocksPayload);
        boolean matchedAny = false;
        while (blockMatcher.find()) {
            matchedAny = true;
            String key = jsonUnescape(blockMatcher.group(1));
            String implDir = jsonUnescape(blockMatcher.group(2));
            String legacyImplDir = blockMatcher.group(3) == null ? null : jsonUnescape(blockMatcher.group(3));
            String depsPayload = blockMatcher.group(4);
            index.put(key, parseContainmentBlock(key, implDir, legacyImplDir, depsPayload));
        }

        if (!matchedAny) {
            Matcher legacyMatcher = Pattern
                .compile(
                    "\\{\\\"blockKey\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\",\\\"legacyImplDir\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\",\\\"allowedDeps\\\":\\[(.*?)\\]\\}",
                    Pattern.DOTALL
                )
                .matcher(blocksPayload);
            while (legacyMatcher.find()) {
                String key = jsonUnescape(legacyMatcher.group(1));
                String legacyImplDir = jsonUnescape(legacyMatcher.group(2));
                String depsPayload = legacyMatcher.group(3);
                String implDir = "src/main/java/blocks/" + key.replace('.', '/') + "/impl";
                index.put(key, parseContainmentBlock(key, implDir, legacyImplDir, depsPayload));
            }
        }
        return index;
    }

    private JvmTarget.ContainmentBlockSpec parseContainmentBlock(
        String blockKey,
        String implDir,
        String legacyImplDir,
        String depsPayload
    ) {
        ArrayList<JvmTarget.ContainmentDep> deps = new ArrayList<>();
        Matcher depMatcher = Pattern
            .compile("\\{\\\"ga\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\",\\\"version\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\"\\}")
            .matcher(depsPayload);
        while (depMatcher.find()) {
            deps.add(new JvmTarget.ContainmentDep(jsonUnescape(depMatcher.group(1)), jsonUnescape(depMatcher.group(2))));
        }
        deps.sort(Comparator.comparing(JvmTarget.ContainmentDep::ga));
        return new JvmTarget.ContainmentBlockSpec(blockKey, implDir, legacyImplDir, deps);
    }

    private String extractRequiredArrayPayload(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\":[");
        if (keyIdx < 0) {
            throw new IllegalStateException("missing key: " + key);
        }
        int start = json.indexOf('[', keyIdx);
        if (start < 0) {
            throw new IllegalStateException("malformed array: " + key);
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
        throw new IllegalStateException("malformed array: " + key);
    }

    private String jsonUnescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i + 1 >= value.length()) {
                break;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case '\\' -> out.append('\\');
                case '"' -> out.append('"');
                default -> out.append(next);
            }
        }
        return out.toString();
    }
}


