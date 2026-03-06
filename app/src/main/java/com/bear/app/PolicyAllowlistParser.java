package com.bear.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class PolicyAllowlistParser {
    static final String REFLECTION_ALLOWLIST_PATH = "bear-policy/reflection-allowlist.txt";
    static final String HYGIENE_ALLOWLIST_PATH = "bear-policy/hygiene-allowlist.txt";
    static final String PURE_SHARED_IMMUTABLE_TYPES_ALLOWLIST_PATH = "bear-policy/pure-shared-immutable-types.txt";
    private static final Pattern FQCN_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+$");

    private PolicyAllowlistParser() {
    }

    static Set<String> parseExactPathAllowlist(Path projectRoot, String policyRelativePath)
        throws IOException, PolicyValidationException {
        Path policyFile = projectRoot.resolve(policyRelativePath).normalize();
        if (!Files.exists(policyFile)) {
            return Set.of();
        }
        if (!Files.isRegularFile(policyFile)) {
            throw new PolicyValidationException(policyRelativePath, "policy file is not a regular file");
        }

        List<String> lines = Files.readAllLines(policyFile, StandardCharsets.UTF_8);
        ArrayList<String> entries = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String normalized = normalizeAllowlistPath(trimmed, policyRelativePath, i + 1);
            if (!seen.add(normalized)) {
                throw new PolicyValidationException(
                    policyRelativePath,
                    "duplicate entry at line " + (i + 1) + ": " + normalized
                );
            }
            entries.add(normalized);
        }

        ArrayList<String> sorted = new ArrayList<>(entries);
        sorted.sort(String::compareTo);
        if (!entries.equals(sorted)) {
            throw new PolicyValidationException(policyRelativePath, "entries must be sorted lexicographically");
        }
        return Set.copyOf(entries);
    }

    static Set<String> parseFqcnAllowlist(Path projectRoot, String policyRelativePath)
        throws IOException, PolicyValidationException {
        Path policyFile = projectRoot.resolve(policyRelativePath).normalize();
        if (!Files.exists(policyFile)) {
            return Set.of();
        }
        if (!Files.isRegularFile(policyFile)) {
            throw new PolicyValidationException(policyRelativePath, "policy file is not a regular file");
        }

        List<String> lines = Files.readAllLines(policyFile, StandardCharsets.UTF_8);
        ArrayList<String> entries = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String normalized = normalizeAllowlistFqcn(trimmed, policyRelativePath, i + 1);
            if (!seen.add(normalized)) {
                throw new PolicyValidationException(
                    policyRelativePath,
                    "duplicate entry at line " + (i + 1) + ": " + normalized
                );
            }
            entries.add(normalized);
        }

        ArrayList<String> sorted = new ArrayList<>(entries);
        sorted.sort(String::compareTo);
        if (!entries.equals(sorted)) {
            throw new PolicyValidationException(policyRelativePath, "entries must be sorted lexicographically");
        }
        return Set.copyOf(entries);
    }

    private static String normalizeAllowlistPath(String raw, String policyPath, int lineNumber)
        throws PolicyValidationException {
        String candidate = raw;
        if (candidate.contains("\\")) {
            throw new PolicyValidationException(policyPath, "line " + lineNumber + ": path must use forward slashes");
        }
        if (candidate.endsWith("/")) {
            throw new PolicyValidationException(policyPath, "line " + lineNumber + ": path must not end with '/'");
        }
        if (candidate.startsWith("/")) {
            throw new PolicyValidationException(policyPath, "line " + lineNumber + ": path must be repo-relative");
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.matches("^[a-z]:/.*")) {
            throw new PolicyValidationException(policyPath, "line " + lineNumber + ": path must be repo-relative");
        }
        if (candidate.indexOf('*') >= 0 || candidate.indexOf('?') >= 0
            || candidate.indexOf('[') >= 0 || candidate.indexOf(']') >= 0
            || candidate.indexOf('{') >= 0 || candidate.indexOf('}') >= 0) {
            throw new PolicyValidationException(policyPath, "line " + lineNumber + ": wildcard paths are not supported");
        }

        String[] segments = candidate.split("/");
        if (segments.length == 0) {
            throw new PolicyValidationException(policyPath, "line " + lineNumber + ": invalid path");
        }
        ArrayList<String> normalized = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                throw new PolicyValidationException(policyPath, "line " + lineNumber + ": invalid path");
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw new PolicyValidationException(policyPath, "line " + lineNumber + ": path traversal is not allowed");
            }
            normalized.add(segment);
        }
        return String.join("/", normalized);
    }

    private static String normalizeAllowlistFqcn(String raw, String policyPath, int lineNumber)
        throws PolicyValidationException {
        String candidate = raw.trim();
        if (!FQCN_PATTERN.matcher(candidate).matches()) {
            throw new PolicyValidationException(
                policyPath,
                "line " + lineNumber + ": entry must be fully-qualified class name"
            );
        }
        return candidate;
    }
}
