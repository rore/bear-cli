package com.bear.kernel.target.jvm;

import com.bear.kernel.target.*;
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

public final class BoundaryBypassScanner {
    private static final String PORT_IMPL_BYPASS_RULE = "PORT_IMPL_OUTSIDE_GOVERNED_ROOT";
    private static final String MULTI_BLOCK_PORT_IMPL_RULE = "MULTI_BLOCK_PORT_IMPL_FORBIDDEN";
    private static final String PLACEHOLDER_MARKER_TODO = "TODO: replace this entire method body with business logic.";
    private static final String PLACEHOLDER_MARKER_RETURN = "Do not append logic below this placeholder return.";
    private static final Pattern PLACEHOLDER_RESULT_RETURN_PATTERN = Pattern.compile(
        "\\breturn\\s+new\\s+[A-Za-z_][A-Za-z0-9_]*Result\\s*\\("
    );
    private static final Pattern IDENTIFIER_TOKEN_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");
    private static final Pattern MODULE_PROVIDES_PATTERN = Pattern.compile(
        "\\bprovides\\s+([A-Za-z_][A-Za-z0-9_$.]*)\\s+with\\s+([^;]+);",
        Pattern.DOTALL
    );
    private static final Pattern STATIC_CALL_PATTERN = Pattern.compile(
        "\\b([A-Za-z_][A-Za-z0-9_\\.]*)\\s*\\.\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\("
    );
    private static final Pattern NEW_TYPE_PATTERN = Pattern.compile(
        "\\bnew\\s+([A-Za-z_][A-Za-z0-9_\\.]*)\\s*\\("
    );
    private static final Pattern PACKAGE_DECL_PATTERN = Pattern.compile(
        "(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_\\.]*)\\s*;"
    );
    private static final Pattern IMPORT_DECL_PATTERN = Pattern.compile(
        "(?m)^\\s*import\\s+(static\\s+)?([A-Za-z_][A-Za-z0-9_\\.]*?)\\s*;"
    );
    private static final String SERVICE_DESCRIPTOR_PREFIX = "src/main/resources/META-INF/services/";
    private static final String MODULE_INFO_PATH = "src/main/java/module-info.java";

    private BoundaryBypassScanner() {
    }

    public static List<BoundaryBypassFinding> scanBoundaryBypass(Path projectRoot, List<WiringManifest> manifests)
        throws IOException, ManifestParseException, PolicyValidationException {
        return scanBoundaryBypass(projectRoot, manifests, Set.of());
    }

    public static List<BoundaryBypassFinding> scanBoundaryBypass(
        Path projectRoot,
        List<WiringManifest> manifests,
        Set<String> reflectionAllowlist
    ) throws IOException, ManifestParseException, PolicyValidationException {
        if (manifests.isEmpty()) {
            return List.of();
        }
        Set<String> pureImmutableAllowlist = PolicyAllowlistParser.parseFqcnAllowlist(
            projectRoot,
            PolicyAllowlistParser.PURE_SHARED_IMMUTABLE_TYPES_ALLOWLIST_PATH
        );

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
                    findings.addAll(scanLanePolicyViolations(rel, source, sanitized, pureImmutableAllowlist));

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
            String executeBodies = extractExecuteMethodBodies(sanitized);
            findings.addAll(scanImplContainment(projectRoot, manifest, rel, sanitized, executeBodies));
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

            List<String> requiredPorts = new ArrayList<>(manifest.logicRequiredPorts());
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

        findings.addAll(scanPortImplContainmentBypass(projectRoot, manifests));

        findings.sort(
            Comparator.comparing(BoundaryBypassFinding::path)
                .thenComparing(BoundaryBypassFinding::rule)
                .thenComparing(BoundaryBypassFinding::detail)
        );
        return findings;
    }

    public static List<BoundaryBypassFinding> scanPortImplContainmentBypass(
        Path projectRoot,
        List<WiringManifest> manifests
    ) throws IOException, ManifestParseException {
        List<PortImplContainmentFinding> outsideRootFindings = PortImplContainmentScanner.scanPortImplOutsideGovernedRoots(
            projectRoot,
            manifests
        );
        ArrayList<BoundaryBypassFinding> bypassFindings = new ArrayList<>();
        TreeSet<String> outsideRootViolationPaths = new TreeSet<>();
        for (PortImplContainmentFinding finding : outsideRootFindings) {
            outsideRootViolationPaths.add(finding.path());
            bypassFindings.add(new BoundaryBypassFinding(
                PORT_IMPL_BYPASS_RULE,
                finding.path(),
                "KIND=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: " + finding.interfaceFqcn() + " -> " + finding.implClassFqcn()
            ));
        }

        List<MultiBlockPortImplFinding> multiBlockFindings = PortImplContainmentScanner.scanMultiBlockPortImplFindings(
            projectRoot,
            manifests
        );
        for (MultiBlockPortImplFinding finding : multiBlockFindings) {
            // Dedupe by contract: outside-governed-root takes precedence for the same file.
            if (outsideRootViolationPaths.contains(finding.path())) {
                continue;
            }
            if (PortImplContainmentScanner.MARKER_MISUSED_OUTSIDE_SHARED_KIND.equals(finding.kind())) {
                bypassFindings.add(new BoundaryBypassFinding(
                    MULTI_BLOCK_PORT_IMPL_RULE,
                    finding.path(),
                    "KIND=MARKER_MISUSED_OUTSIDE_SHARED: " + finding.implClassFqcn()
                ));
                continue;
            }
            bypassFindings.add(new BoundaryBypassFinding(
                MULTI_BLOCK_PORT_IMPL_RULE,
                finding.path(),
                "KIND=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: " + finding.implClassFqcn()
                    + " -> " + finding.generatedPackageCsv()
            ));
        }

        if (bypassFindings.isEmpty()) {
            return List.of();
        }
        bypassFindings.sort(
            Comparator.comparing(BoundaryBypassFinding::path)
                .thenComparing(BoundaryBypassFinding::rule)
                .thenComparing(BoundaryBypassFinding::detail)
        );
        return bypassFindings;
    }

    static boolean isBoundaryScanExcluded(String relPath) {
        return !relPath.startsWith("src/main/")
            || relPath.startsWith("src/test/")
            || relPath.startsWith("build/")
            || relPath.startsWith(".gradle/")
            || relPath.startsWith("build/generated/bear/");
    }

    public static String firstDirectImplUsageToken(String source) {
        String token = BoundaryImplUsageDetector.firstDirectImplUsageToken(source);
        return token == null ? null : normalizeToken(token);
    }

    public static String firstReflectionClassloadingToken(String source) {
        return BoundaryImplUsageDetector.firstReflectionClassloadingToken(source);
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

    private static List<BoundaryBypassFinding> scanImplContainment(
        Path projectRoot,
        WiringManifest manifest,
        String relPath,
        String sanitizedSource,
        String executeBodiesSource
    ) {
        if (executeBodiesSource == null || executeBodiesSource.isBlank()) {
            return List.of();
        }
        String packageName = parsePackageName(sanitizedSource);
        Map<String, String> explicitImports = parseExplicitImports(sanitizedSource);
        TreeSet<String> targets = new TreeSet<>();
        targets.addAll(findStaticCallTypeTargets(executeBodiesSource));
        targets.addAll(findConstructorCallTypeTargets(executeBodiesSource));

        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        for (String typeToken : targets) {
            String resolvedTarget = resolveTypeTokenFqcn(typeToken, packageName, explicitImports);
            if (resolvedTarget == null) {
                continue;
            }
            if (resolvedTarget.startsWith("java.") || resolvedTarget.startsWith("javax.")) {
                continue;
            }
            String resolvedSourcePath = resolveSourcePath(projectRoot, resolvedTarget);
            if (resolvedSourcePath == null) {
                continue;
            }
            if (isUnderAnyGovernedRoot(resolvedSourcePath, manifest.governedSourceRoots())) {
                continue;
            }
            findings.add(new BoundaryBypassFinding(
                "IMPL_CONTAINMENT_BYPASS",
                relPath,
                "KIND=IMPL_EXTERNAL_CALL: " + resolvedTarget
            ));
        }
        return findings;
    }

    private static List<BoundaryBypassFinding> scanLanePolicyViolations(
        String relPath,
        String source,
        String sanitized,
        Set<String> pureImmutableAllowlist
    ) {
        return BoundaryLanePolicyScanner.scanLanePolicyViolations(relPath, source, sanitized, pureImmutableAllowlist);
    }

    static boolean ruleAppliesToPath(String ruleId, String relPath) {
        return BoundaryLanePolicyScanner.ruleAppliesToPath(ruleId, relPath);
    }

    private static String extractExecuteMethodBodies(String source) {
        StringBuilder out = new StringBuilder();
        Matcher matcher = Pattern.compile("\\bexecute\\s*\\(").matcher(source);
        while (matcher.find()) {
            int openParen = source.indexOf('(', matcher.start());
            if (openParen < 0) {
                continue;
            }
            int closeParen = findClosingParen(source, openParen);
            if (closeParen < 0) {
                continue;
            }
            int idx = closeParen + 1;
            while (idx < source.length() && Character.isWhitespace(source.charAt(idx))) {
                idx++;
            }
            if (idx >= source.length() || source.charAt(idx) != '{') {
                continue;
            }
            int closeBrace = findClosingBrace(source, idx);
            if (closeBrace < 0) {
                continue;
            }
            out.append(source, idx + 1, closeBrace).append('\n');
        }
        return out.toString();
    }

    static String parsePackageName(String source) {
        Matcher matcher = PACKAGE_DECL_PATTERN.matcher(source);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).trim();
    }

    static Map<String, String> parseExplicitImports(String source) {
        TreeMap<String, String> importsBySimple = new TreeMap<>();
        Matcher matcher = IMPORT_DECL_PATTERN.matcher(source);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                continue;
            }
            String value = matcher.group(2).trim();
            if (value.endsWith(".*")) {
                continue;
            }
            int idx = value.lastIndexOf('.');
            if (idx <= 0 || idx == value.length() - 1) {
                continue;
            }
            String simple = value.substring(idx + 1);
            importsBySimple.putIfAbsent(simple, value);
        }
        return importsBySimple;
    }

    private static List<String> findStaticCallTypeTargets(String source) {
        ArrayList<String> targets = new ArrayList<>();
        Matcher matcher = STATIC_CALL_PATTERN.matcher(source);
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            if ("this".equals(token) || "super".equals(token)) {
                continue;
            }
            targets.add(token);
        }
        return targets;
    }

    private static List<String> findConstructorCallTypeTargets(String source) {
        ArrayList<String> targets = new ArrayList<>();
        Matcher matcher = NEW_TYPE_PATTERN.matcher(source);
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            int openParen = matcher.end() - 1;
            int closeParen = findClosingParen(source, openParen);
            if (closeParen < 0) {
                continue;
            }
            int idx = closeParen + 1;
            while (idx < source.length() && Character.isWhitespace(source.charAt(idx))) {
                idx++;
            }
            if (idx >= source.length() || source.charAt(idx) != '.') {
                continue;
            }
            idx++;
            while (idx < source.length() && Character.isWhitespace(source.charAt(idx))) {
                idx++;
            }
            if (idx >= source.length() || !Character.isJavaIdentifierStart(source.charAt(idx))) {
                continue;
            }
            while (idx < source.length() && Character.isJavaIdentifierPart(source.charAt(idx))) {
                idx++;
            }
            while (idx < source.length() && Character.isWhitespace(source.charAt(idx))) {
                idx++;
            }
            if (idx < source.length() && source.charAt(idx) == '(') {
                targets.add(token);
            }
        }
        return targets;
    }

    private static int findClosingParen(String source, int openParenIndex) {
        if (openParenIndex < 0 || openParenIndex >= source.length() || source.charAt(openParenIndex) != '(') {
            return -1;
        }
        int depth = 1;
        for (int i = openParenIndex + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findClosingBrace(String source, int openBraceIndex) {
        if (openBraceIndex < 0 || openBraceIndex >= source.length() || source.charAt(openBraceIndex) != '{') {
            return -1;
        }
        int depth = 1;
        for (int i = openBraceIndex + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static String resolveTypeTokenFqcn(
        String typeToken,
        String packageName,
        Map<String, String> explicitImports
    ) {
        if (isPrimitiveTypeToken(typeToken)) {
            return typeToken;
        }
        if (typeToken.contains(".")) {
            return typeToken;
        }
        String imported = explicitImports.get(typeToken);
        if (imported != null) {
            return imported;
        }
        if (packageName.isBlank()) {
            return null;
        }
        return packageName + "." + typeToken;
    }

    private static boolean isPrimitiveTypeToken(String typeToken) {
        return "byte".equals(typeToken)
            || "short".equals(typeToken)
            || "int".equals(typeToken)
            || "long".equals(typeToken)
            || "float".equals(typeToken)
            || "double".equals(typeToken)
            || "boolean".equals(typeToken)
            || "char".equals(typeToken);
    }

    private static String resolveSourcePath(Path projectRoot, String targetFqcn) {
        String candidateRelPath = "src/main/java/" + targetFqcn.replace('.', '/') + ".java";
        Path candidate = projectRoot.resolve(candidateRelPath).normalize();
        if (!candidate.startsWith(projectRoot)) {
            return null;
        }
        if (!Files.isRegularFile(candidate)) {
            return null;
        }
        return candidateRelPath;
    }

    private static boolean isUnderAnyGovernedRoot(String resolvedSourcePath, List<String> governedSourceRoots) {
        if (governedSourceRoots == null || governedSourceRoots.isEmpty()) {
            return false;
        }
        for (String root : governedSourceRoots) {
            String normalizedRoot = normalizeRepoPath(root);
            if (normalizedRoot == null || normalizedRoot.isBlank()) {
                continue;
            }
            if (resolvedSourcePath.equals(normalizedRoot) || resolvedSourcePath.startsWith(normalizedRoot + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeRepoPath(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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

    public static String firstReflectiveImplUsageToken(String source) {
        String token = BoundaryImplUsageDetector.firstReflectiveImplUsageToken(source);
        return token == null ? null : normalizeToken(token);
    }

    public static String firstTopLevelNullPortWiringToken(
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
        Matcher matcher = PolicyPatterns.PORT_USED_SUPPRESSION_PATTERN.matcher(source);
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

    public static String stripJavaCommentsStringsAndChars(String source) {
        return BoundaryJavaSourceSanitizer.stripJavaCommentsStringsAndChars(source);
    }

    static String stripJavaCommentsPreserveStringsAndChars(String source) {
        return BoundaryJavaSourceSanitizer.stripJavaCommentsPreserveStringsAndChars(source);
    }

}


