package com.bear.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BoundaryBypassScanner {
    private static final Pattern DIRECT_IMPL_IMPORT_PATTERN = Pattern.compile(
        "\\bimport\\s+blocks(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.impl\\.[A-Za-z_][A-Za-z0-9_]*Impl\\s*;"
    );
    private static final Pattern DIRECT_IMPL_NEW_PATTERN = Pattern.compile(
        "\\bnew\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\s*\\("
    );
    private static final Pattern DIRECT_IMPL_TYPE_CAST_PATTERN = Pattern.compile(
        "\\(\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\s*\\)"
    );
    private static final Pattern DIRECT_IMPL_VAR_DECL_PATTERN = Pattern.compile(
        "(?m)\\b(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b\\s+[A-Za-z_][A-Za-z0-9_]*\\s*(?:[=;,)])"
    );
    private static final Pattern DIRECT_IMPL_EXTENDS_IMPL_PATTERN = Pattern.compile(
        "\\bextends\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b"
    );
    private static final Pattern DIRECT_IMPL_IMPLEMENTS_IMPL_PATTERN = Pattern.compile(
        "\\bimplements\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b"
    );
    private static final Pattern SUPPRESSION_PATTERN = Pattern.compile("(?m)^\\s*//\\s*BEAR:PORT_USED\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");

    private BoundaryBypassScanner() {
    }

    static List<BoundaryBypassFinding> scanBoundaryBypass(Path projectRoot, List<WiringManifest> manifests) throws IOException {
        if (manifests.isEmpty()) {
            return List.of();
        }

        TreeMap<String, WiringManifest> manifestsByImplPath = new TreeMap<>();
        HashSet<String> governedEntrypointFqcns = new HashSet<>();
        HashMap<String, Integer> governedSimpleNameCounts = new HashMap<>();
        for (WiringManifest manifest : manifests) {
            manifestsByImplPath.put(manifest.implSourcePath(), manifest);
            governedEntrypointFqcns.add(manifest.entrypointFqcn());
            String simple = simpleName(manifest.entrypointFqcn());
            governedSimpleNameCounts.put(simple, governedSimpleNameCounts.getOrDefault(simple, 0) + 1);
        }

        List<BoundaryBypassFinding> findings = new ArrayList<>();
        if (Files.isDirectory(projectRoot)) {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String rel = projectRoot.relativize(file).toString().replace('\\', '/');
                    if (!rel.endsWith(".java") || isBoundaryScanExcluded(rel)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String source = Files.readString(file, StandardCharsets.UTF_8);
                    String sanitized = stripJavaCommentsStringsAndChars(source);

                    String directImplToken = firstDirectImplUsageToken(sanitized);
                    if (directImplToken != null) {
                        findings.add(new BoundaryBypassFinding("DIRECT_IMPL_USAGE", rel, directImplToken));
                    }

                    String nullWiringToken = firstTopLevelNullPortWiringToken(sanitized, governedEntrypointFqcns, governedSimpleNameCounts);
                    if (nullWiringToken != null) {
                        findings.add(new BoundaryBypassFinding("NULL_PORT_WIRING", rel, nullWiringToken));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        for (Map.Entry<String, WiringManifest> entry : manifestsByImplPath.entrySet()) {
            WiringManifest manifest = entry.getValue();
            Path implPath = projectRoot.resolve(entry.getKey()).normalize();
            String rel = projectRoot.relativize(implPath).toString().replace('\\', '/');
            if (!Files.isRegularFile(implPath)) {
                findings.add(new BoundaryBypassFinding(
                    "EFFECTS_BYPASS",
                    rel,
                    "missing governed impl source"
                ));
                continue;
            }
            String source = Files.readString(implPath, StandardCharsets.UTF_8);
            String sanitized = stripJavaCommentsStringsAndChars(source);
            Set<String> suppressions = parsePortSuppressions(source);

            List<String> requiredPorts = manifest.logicRequiredPorts().isEmpty()
                ? new ArrayList<>(manifest.requiredEffectPorts())
                : new ArrayList<>(manifest.logicRequiredPorts());
            requiredPorts.sort(String::compareTo);
            for (String portParam : requiredPorts) {
                if (suppressions.contains(portParam)) {
                    continue;
                }
                if (referencesPortAsReceiver(sanitized, portParam)) {
                    continue;
                }
                if (passesPortAsInvocationArgument(sanitized, portParam)) {
                    continue;
                }
                findings.add(new BoundaryBypassFinding(
                    "EFFECTS_BYPASS",
                    rel,
                    "missing required effect port usage: " + portParam
                ));
            }
        }

        findings.sort(
            Comparator.comparing(BoundaryBypassFinding::path)
                .thenComparing(BoundaryBypassFinding::rule)
                .thenComparing(BoundaryBypassFinding::detail)
        );
        return findings;
    }

    static boolean isBoundaryScanExcluded(String relPath) {
        return !relPath.startsWith("src/main/")
            || relPath.startsWith("src/test/")
            || relPath.startsWith("build/")
            || relPath.startsWith(".gradle/")
            || relPath.startsWith("build/generated/bear/");
    }

    static String firstDirectImplUsageToken(String source) {
        Matcher importMatcher = DIRECT_IMPL_IMPORT_PATTERN.matcher(source);
        if (importMatcher.find()) {
            return normalizeToken(importMatcher.group());
        }
        Matcher newMatcher = DIRECT_IMPL_NEW_PATTERN.matcher(source);
        if (newMatcher.find()) {
            return normalizeToken(newMatcher.group());
        }
        Matcher castMatcher = DIRECT_IMPL_TYPE_CAST_PATTERN.matcher(source);
        if (castMatcher.find()) {
            return normalizeToken(castMatcher.group());
        }
        Matcher varMatcher = DIRECT_IMPL_VAR_DECL_PATTERN.matcher(source);
        if (varMatcher.find()) {
            return normalizeToken(varMatcher.group());
        }
        Matcher extendsMatcher = DIRECT_IMPL_EXTENDS_IMPL_PATTERN.matcher(source);
        if (extendsMatcher.find()) {
            return normalizeToken(extendsMatcher.group());
        }
        Matcher implementsMatcher = DIRECT_IMPL_IMPLEMENTS_IMPL_PATTERN.matcher(source);
        if (implementsMatcher.find()) {
            return normalizeToken(implementsMatcher.group());
        }
        return null;
    }

    static String firstTopLevelNullPortWiringToken(
        String source,
        Set<String> governedEntrypointFqcns,
        Map<String, Integer> governedSimpleNameCounts
    ) {
        Matcher constructorMatcher = Pattern.compile("\\bnew\\s+([A-Za-z_][A-Za-z0-9_\\.]*)\\s*\\(").matcher(source);
        while (constructorMatcher.find()) {
            String typeName = constructorMatcher.group(1);
            if (!isGovernedEntrypointType(typeName, governedEntrypointFqcns, governedSimpleNameCounts)) {
                continue;
            }
            List<String> args = parseTopLevelArguments(source, constructorMatcher.end() - 1);
            if (args == null) {
                continue;
            }
            for (String arg : args) {
                if ("null".equals(arg.trim())) {
                    return "new " + typeName + "(..., null, ...)";
                }
            }
        }
        return null;
    }

    static boolean isGovernedEntrypointType(
        String typeName,
        Set<String> governedEntrypointFqcns,
        Map<String, Integer> governedSimpleNameCounts
    ) {
        if (governedEntrypointFqcns.contains(typeName)) {
            return true;
        }
        String simple = simpleName(typeName);
        return governedSimpleNameCounts.getOrDefault(simple, 0) == 1;
    }

    static String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        if (idx < 0) {
            return fqcn;
        }
        return fqcn.substring(idx + 1);
    }

    static List<String> parseTopLevelArguments(String source, int openParenIndex) {
        if (openParenIndex < 0 || openParenIndex >= source.length() || source.charAt(openParenIndex) != '(') {
            return null;
        }
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 1;
        int braceDepth = 0;
        int bracketDepth = 0;
        for (int i = openParenIndex + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') {
                parenDepth++;
                current.append(c);
                continue;
            }
            if (c == ')') {
                if (parenDepth == 1 && braceDepth == 0 && bracketDepth == 0) {
                    args.add(current.toString());
                    return args;
                }
                parenDepth--;
                current.append(c);
                continue;
            }
            if (c == '{') {
                braceDepth++;
                current.append(c);
                continue;
            }
            if (c == '}') {
                if (braceDepth > 0) {
                    braceDepth--;
                }
                current.append(c);
                continue;
            }
            if (c == '[') {
                bracketDepth++;
                current.append(c);
                continue;
            }
            if (c == ']') {
                if (bracketDepth > 0) {
                    bracketDepth--;
                }
                current.append(c);
                continue;
            }
            if (c == ',' && parenDepth == 1 && braceDepth == 0 && bracketDepth == 0) {
                args.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        return null;
    }

    static Set<String> parsePortSuppressions(String source) {
        TreeSet<String> suppressions = new TreeSet<>();
        Matcher matcher = SUPPRESSION_PATTERN.matcher(source);
        while (matcher.find()) {
            suppressions.add(matcher.group(1));
        }
        return suppressions;
    }

    static boolean referencesPortAsReceiver(String source, String portParam) {
        return Pattern.compile("\\b" + Pattern.quote(portParam) + "\\s*\\.").matcher(source).find();
    }

    static boolean passesPortAsInvocationArgument(String source, String portParam) {
        return Pattern.compile("(?:\\(|,)\\s*" + Pattern.quote(portParam) + "\\s*(?:,|\\))").matcher(source).find();
    }

    static String normalizeToken(String token) {
        return token.replaceAll("\\s+", " ").trim();
    }

    static String stripJavaCommentsStringsAndChars(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                    out.append(c);
                } else {
                    out.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    out.append(' ');
                    out.append(' ');
                    i++;
                } else if (c == '\n' || c == '\r') {
                    out.append(c);
                } else {
                    out.append(' ');
                }
                continue;
            }
            if (inString) {
                if (!escaped && c == '\"') {
                    inString = false;
                }
                if (!escaped && c == '\\') {
                    escaped = true;
                } else {
                    escaped = false;
                }
                out.append(c == '\n' || c == '\r' ? c : ' ');
                continue;
            }
            if (inChar) {
                if (!escaped && c == '\'') {
                    inChar = false;
                }
                if (!escaped && c == '\\') {
                    escaped = true;
                } else {
                    escaped = false;
                }
                out.append(c == '\n' || c == '\r' ? c : ' ');
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                out.append(' ');
                out.append(' ');
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                out.append(' ');
                out.append(' ');
                i++;
                continue;
            }
            if (c == '\"') {
                inString = true;
                escaped = false;
                out.append(' ');
                continue;
            }
            if (c == '\'') {
                inChar = true;
                escaped = false;
                out.append(' ');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
