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
    private static final Pattern REFLECTION_CLASS_FORNAME_PATTERN = Pattern.compile("\\bClass\\s*\\.\\s*forName\\s*\\(");
    private static final Pattern REFLECTION_LOAD_CLASS_PATTERN = Pattern.compile("\\bloadClass\\s*\\(");
    private static final Pattern REFLECT_FORNAME_PATTERN = Pattern.compile(
        "\\bClass\\s*\\.\\s*forName\\s*\\(\\s*\"blocks\\.[A-Za-z0-9_$.]+\\.impl\\.[A-Za-z0-9_]+Impl\"\\s*\\)"
    );
    private static final Pattern REFLECT_LOADCLASS_PATTERN = Pattern.compile(
        "\\bloadClass\\s*\\(\\s*\"blocks\\.[A-Za-z0-9_$.]+\\.impl\\.[A-Za-z0-9_]+Impl\"\\s*\\)"
    );
    private static final String PLACEHOLDER_MARKER_TODO = "TODO: replace this entire method body with business logic.";
    private static final String PLACEHOLDER_MARKER_RETURN = "Do not append logic below this placeholder return.";
    private static final Pattern PLACEHOLDER_RESULT_RETURN_PATTERN = Pattern.compile(
        "\\breturn\\s+new\\s+[A-Za-z_][A-Za-z0-9_]*Result\\s*\\("
    );
    private static final Pattern SUPPRESSION_PATTERN = Pattern.compile("(?m)^\\s*//\\s*BEAR:PORT_USED\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
    private static final Pattern IDENTIFIER_TOKEN_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");
    private static final Pattern MODULE_PROVIDES_PATTERN = Pattern.compile(
        "\\bprovides\\s+([A-Za-z_][A-Za-z0-9_$.]*)\\s+with\\s+([^;]+);",
        Pattern.DOTALL
    );
    private static final String SERVICE_DESCRIPTOR_PREFIX = "src/main/resources/META-INF/services/";
    private static final String MODULE_INFO_PATH = "src/main/java/module-info.java";

    private BoundaryBypassScanner() {
    }

    static List<BoundaryBypassFinding> scanBoundaryBypass(Path projectRoot, List<WiringManifest> manifests) throws IOException {
        return scanBoundaryBypass(projectRoot, manifests, Set.of());
    }

    static List<BoundaryBypassFinding> scanBoundaryBypass(
        Path projectRoot,
        List<WiringManifest> manifests,
        Set<String> reflectionAllowlist
    ) throws IOException {
        if (manifests.isEmpty()) {
            return List.of();
        }

        TreeMap<String, WiringManifest> manifestsByImplPath = new TreeMap<>();
        HashSet<String> governedEntrypointFqcns = new HashSet<>();
        HashMap<String, Integer> governedSimpleNameCounts = new HashMap<>();
        TreeMap<String, String> governedImplByLogicFqcn = new TreeMap<>();
        for (WiringManifest manifest : manifests) {
            manifestsByImplPath.put(manifest.implSourcePath(), manifest);
            governedEntrypointFqcns.add(manifest.entrypointFqcn());
            String simple = simpleName(manifest.entrypointFqcn());
            governedSimpleNameCounts.put(simple, governedSimpleNameCounts.getOrDefault(simple, 0) + 1);
            String logicFqcn = safeTrim(manifest.logicInterfaceFqcn());
            String implFqcn = safeTrim(manifest.implFqcn());
            if (!logicFqcn.isEmpty() && !implFqcn.isEmpty()) {
                governedImplByLogicFqcn.putIfAbsent(logicFqcn, implFqcn);
            }
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
                    if (isBoundaryScanExcluded(rel)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (rel.startsWith(SERVICE_DESCRIPTOR_PREFIX)) {
                        scanServiceDescriptor(file, rel, governedImplByLogicFqcn, findings);
                        return FileVisitResult.CONTINUE;
                    }
                    if (MODULE_INFO_PATH.equals(rel)) {
                        scanModuleInfo(file, rel, governedImplByLogicFqcn, findings);
                        return FileVisitResult.CONTINUE;
                    }
                    if (!rel.endsWith(".java")) {
                        return FileVisitResult.CONTINUE;
                    }
                    String source = Files.readString(file, StandardCharsets.UTF_8);
                    String sanitized = stripJavaCommentsStringsAndChars(source);
                    String noCommentSource = stripJavaCommentsPreserveStringsAndChars(source);

                    String directImplToken = firstDirectImplUsageToken(sanitized);
                    if (directImplToken != null) {
                        findings.add(new BoundaryBypassFinding("DIRECT_IMPL_USAGE", rel, directImplToken));
                    }
                    String reflectiveToken = firstReflectiveImplUsageToken(noCommentSource);
                    if (reflectiveToken != null) {
                        findings.add(new BoundaryBypassFinding("DIRECT_IMPL_USAGE", rel, reflectiveToken));
                    }
                    if (!reflectionAllowlist.contains(rel)) {
                        String classloadingToken = firstReflectionClassloadingToken(sanitized);
                        if (classloadingToken != null) {
                            findings.add(new BoundaryBypassFinding(
                                "DIRECT_IMPL_USAGE",
                                rel,
                                "KIND=REFLECTION_CLASSLOADING: " + classloadingToken
                            ));
                        }
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
            if (hasPlaceholderStub(source, sanitized)) {
                findings.add(new BoundaryBypassFinding(
                    "IMPL_PLACEHOLDER",
                    rel,
                    "generated placeholder implementation stub remains"
                ));
                continue;
            }
            Set<String> suppressions = parsePortSuppressions(source);
            List<String> semanticPorts = new ArrayList<>(manifest.wrapperOwnedSemanticPorts());
            semanticPorts.sort(String::compareTo);
            for (String semanticPort : semanticPorts) {
                if (suppressions.contains(semanticPort)) {
                    findings.add(new BoundaryBypassFinding(
                        "EFFECTS_BYPASS",
                        rel,
                        "semantic port suppression forbidden: " + semanticPort
                    ));
                }
                if (containsIdentifierToken(sanitized, semanticPort)) {
                    findings.add(new BoundaryBypassFinding(
                        "EFFECTS_BYPASS",
                        rel,
                        "semantic port usage forbidden: " + semanticPort
                    ));
                }
            }

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

    static String firstReflectionClassloadingToken(String source) {
        Matcher forNameMatcher = REFLECTION_CLASS_FORNAME_PATTERN.matcher(source);
        if (forNameMatcher.find()) {
            return "Class.forName(...)";
        }
        Matcher loadClassMatcher = REFLECTION_LOAD_CLASS_PATTERN.matcher(source);
        if (loadClassMatcher.find()) {
            return "loadClass(...)";
        }
        return null;
    }

    static void scanServiceDescriptor(
        Path file,
        String relPath,
        Map<String, String> governedImplByLogicFqcn,
        List<BoundaryBypassFinding> findings
    ) throws IOException {
        String servicePath = relPath.substring(SERVICE_DESCRIPTOR_PREFIX.length());
        String serviceFqcn = servicePath.replace('/', '.').trim();
        String governedImpl = governedImplByLogicFqcn.get(serviceFqcn);
        if (governedImpl == null) {
            return;
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String provider = firstToken(trimmed);
            if (provider.equals(governedImpl)) {
                findings.add(new BoundaryBypassFinding(
                    "DIRECT_IMPL_USAGE",
                    relPath,
                    "KIND=IMPL_SERVICE_BINDING: " + serviceFqcn + " -> " + provider
                ));
            }
        }
    }

    static void scanModuleInfo(
        Path file,
        String relPath,
        Map<String, String> governedImplByLogicFqcn,
        List<BoundaryBypassFinding> findings
    ) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        String sanitized = stripJavaCommentsStringsAndChars(source);
        Matcher providesMatcher = MODULE_PROVIDES_PATTERN.matcher(sanitized);
        while (providesMatcher.find()) {
            String serviceFqcn = providesMatcher.group(1).trim();
            String governedImpl = governedImplByLogicFqcn.get(serviceFqcn);
            if (governedImpl == null) {
                continue;
            }
            String providersPayload = providesMatcher.group(2);
            for (String providerRaw : providersPayload.split(",")) {
                String provider = providerRaw.trim();
                if (provider.isEmpty()) {
                    continue;
                }
                if (provider.equals(governedImpl)) {
                    findings.add(new BoundaryBypassFinding(
                        "DIRECT_IMPL_USAGE",
                        relPath,
                        "KIND=IMPL_MODULE_BINDING: " + serviceFqcn + " -> " + provider
                    ));
                }
            }
        }
    }

    private static String firstToken(String value) {
        int split = value.indexOf(' ');
        if (split < 0) {
            split = value.indexOf('\t');
        }
        if (split < 0) {
            return value;
        }
        return value.substring(0, split).trim();
    }

    static boolean hasPlaceholderStub(String source, String sanitized) {
        if (source.contains(PLACEHOLDER_MARKER_TODO) && source.contains(PLACEHOLDER_MARKER_RETURN)) {
            return true;
        }
        if (source.contains("BEAR:PORT_USED") && PLACEHOLDER_RESULT_RETURN_PATTERN.matcher(sanitized).find()) {
            return true;
        }
        return false;
    }

    static String firstReflectiveImplUsageToken(String source) {
        Matcher forNameMatcher = REFLECT_FORNAME_PATTERN.matcher(source);
        if (forNameMatcher.find()) {
            return normalizeToken(forNameMatcher.group());
        }
        Matcher loadClassMatcher = REFLECT_LOADCLASS_PATTERN.matcher(source);
        if (loadClassMatcher.find()) {
            return normalizeToken(loadClassMatcher.group());
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

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
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

    static boolean containsIdentifierToken(String source, String identifier) {
        Matcher matcher = IDENTIFIER_TOKEN_PATTERN.matcher(source);
        while (matcher.find()) {
            if (identifier.equals(matcher.group())) {
                return true;
            }
        }
        return false;
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

    static String stripJavaCommentsPreserveStringsAndChars(String source) {
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
                out.append(c);
                if (!escaped && c == '\"') {
                    inString = false;
                }
                if (!escaped && c == '\\') {
                    escaped = true;
                } else {
                    escaped = false;
                }
                continue;
            }
            if (inChar) {
                out.append(c);
                if (!escaped && c == '\'') {
                    inChar = false;
                }
                if (!escaped && c == '\\') {
                    escaped = true;
                } else {
                    escaped = false;
                }
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
                out.append(c);
                continue;
            }
            if (c == '\'') {
                inChar = true;
                escaped = false;
                out.append(c);
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
