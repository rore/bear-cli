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
    private static final String PORT_IMPL_BYPASS_RULE = "PORT_IMPL_OUTSIDE_GOVERNED_ROOT";
    private static final String MULTI_BLOCK_PORT_IMPL_RULE = "MULTI_BLOCK_PORT_IMPL_FORBIDDEN";
    private static final String SHARED_PURITY_RULE = "SHARED_PURITY_VIOLATION";
    private static final String IMPL_PURITY_RULE = "IMPL_PURITY_VIOLATION";
    private static final String IMPL_STATE_DEPENDENCY_RULE = "IMPL_STATE_DEPENDENCY_BYPASS";
    private static final String SCOPED_IMPORT_POLICY_RULE = "SCOPED_IMPORT_POLICY_BYPASS";
    private static final String SHARED_LAYOUT_POLICY_RULE = "SHARED_LAYOUT_POLICY_VIOLATION";
    private static final String STATE_STORE_OP_MISUSE_RULE = "STATE_STORE_OP_MISUSE";
    private static final String STATE_STORE_NOOP_UPDATE_RULE = "STATE_STORE_NOOP_UPDATE";
    private static final String SHARED_ROOT_PREFIX = "src/main/java/blocks/_shared/";
    private static final String SHARED_PURE_ROOT_PREFIX = "src/main/java/blocks/_shared/pure/";
    private static final String SHARED_STATE_ROOT_PREFIX = "src/main/java/blocks/_shared/state/";
    private static final String BLOCKS_ROOT_PREFIX = "src/main/java/blocks/";
    private static final String ADAPTER_SEGMENT = "/adapter/";
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
    private static final Pattern STATIC_FIELD_STATEMENT_PATTERN = Pattern.compile(
        "(?m)^(?!\\s*import\\b)\\s*[^\\n{};]*\\bstatic\\b[^\\n{};]*;"
    );
    private static final Pattern MODIFIER_PATTERN = Pattern.compile("\\b(?:public|protected|private|static|final|transient|volatile)\\b");
    private static final Pattern LOCAL_ENUM_PATTERN = Pattern.compile("\\benum\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern SYNCHRONIZED_PATTERN = Pattern.compile("\\bsynchronized\\b");
    private static final Pattern NEW_TYPE_PATTERN_FOR_FIELDS = Pattern.compile("\\bnew\\s+([A-Za-z_][A-Za-z0-9_\\.]*)");
    private static final Pattern METHOD_SIGNATURE_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:public|protected|private)?\\s*(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?"
            + "[A-Za-z_][A-Za-z0-9_\\.<>,\\[\\]\\s]*\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^;{}]*\\)"
            + "\\s*(?:throws\\s+[A-Za-z0-9_\\.,\\s]+)?\\s*\\{"
    );
    private static final Pattern UPDATE_METHOD_NAME_PATTERN = Pattern.compile("^(?:update.*|set.*|put.*Balance)$");
    private static final Pattern CREATE_CALL_PATTERN = Pattern.compile("\\.\\s*create(?:Wallet|[A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern NOOP_MISSING_RETURN_PATTERN = Pattern.compile(
        "if\\s*\\([^\\)]*==\\s*null[^\\)]*\\)\\s*\\{\\s*return\\s*;\\s*\\}",
        Pattern.DOTALL
    );
    private static final Pattern NOOP_INLINE_MISSING_RETURN_PATTERN = Pattern.compile(
        "if\\s*\\([^\\)]*==\\s*null[^\\)]*\\)\\s*return\\s*;",
        Pattern.DOTALL
    );
    private static final Pattern ENUM_CONSTANT_INIT_PATTERN = Pattern.compile(
        "^([A-Za-z_][A-Za-z0-9_\\.]*)\\s*\\.\\s*([A-Za-z_][A-Za-z0-9_]*)$"
    );
    private static final Set<String> PURE_FORBIDDEN_PREFIX_TOKENS = Set.of(
        "java.io.",
        "java.net.",
        "java.nio.file."
    );
    private static final Set<String> IMPL_FORBIDDEN_PREFIX_TOKENS = Set.of(
        "java.io.",
        "java.net.",
        "java.nio.file.",
        "java.util.concurrent."
    );
    private static final Set<String> ALLOWED_PURE_CONSTANT_TYPES = Set.of(
        "byte",
        "short",
        "int",
        "long",
        "float",
        "double",
        "boolean",
        "char",
        "java.lang.Byte",
        "java.lang.Short",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Float",
        "java.lang.Double",
        "java.lang.Boolean",
        "java.lang.Character",
        "java.lang.String"
    );
    private static final Set<String> MUTABLE_TYPE_TOKENS = Set.of(
        "Map",
        "List",
        "Set",
        "Collection",
        "Queue",
        "Deque",
        "ConcurrentMap",
        "ConcurrentHashMap",
        "CopyOnWriteArrayList",
        "CopyOnWriteArraySet",
        "Atomic",
        "ThreadLocal"
    );
    private static final String SERVICE_DESCRIPTOR_PREFIX = "src/main/resources/META-INF/services/";
    private static final String MODULE_INFO_PATH = "src/main/java/module-info.java";

    private BoundaryBypassScanner() {
    }

    static List<BoundaryBypassFinding> scanBoundaryBypass(Path projectRoot, List<WiringManifest> manifests)
        throws IOException, ManifestParseException, PolicyValidationException {
        return scanBoundaryBypass(projectRoot, manifests, Set.of());
    }

    static List<BoundaryBypassFinding> scanBoundaryBypass(
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

    static List<BoundaryBypassFinding> scanPortImplContainmentBypass(
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
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        if (!relPath.endsWith(".java")) {
            return findings;
        }

        if (isSharedJavaOutsidePureOrState(relPath)) {
            findings.add(new BoundaryBypassFinding(
                SHARED_LAYOUT_POLICY_RULE,
                relPath,
                "shared java source must be under blocks/_shared/pure or blocks/_shared/state"
            ));
        }

        boolean implLane = isImplSourcePath(relPath);
        boolean pureLane = isSharedPureSourcePath(relPath);
        boolean stateLane = relPath.startsWith(SHARED_STATE_ROOT_PREFIX);
        boolean adapterLane = isAdapterSourcePath(relPath);
        if (!implLane && !pureLane && !stateLane && !adapterLane) {
            return findings;
        }

        if (implLane && source.contains("blocks._shared.state.")) {
            findings.add(new BoundaryBypassFinding(
                IMPL_STATE_DEPENDENCY_RULE,
                relPath,
                "impl lane references blocks._shared.state.*"
            ));
        }

        String forbiddenToken = firstForbiddenPrefixToken(
            source,
            implLane ? IMPL_FORBIDDEN_PREFIX_TOKENS : PURE_FORBIDDEN_PREFIX_TOKENS
        );
        if (forbiddenToken != null) {
            findings.add(new BoundaryBypassFinding(
                SCOPED_IMPORT_POLICY_RULE,
                relPath,
                "forbidden package token in " + (implLane ? "impl" : "_shared.pure") + " lane: " + forbiddenToken
            ));
        }

        String synchronizedToken = firstSynchronizedToken(sanitized);
        if (synchronizedToken != null) {
            findings.add(new BoundaryBypassFinding(
                implLane ? IMPL_PURITY_RULE : SHARED_PURITY_RULE,
                relPath,
                "synchronized usage is forbidden in " + (implLane ? "impl" : "_shared.pure") + " lane"
            ));
        }

        String packageName = parsePackageName(sanitized);
        Map<String, String> explicitImports = parseExplicitImports(sanitized);
        Set<String> localEnums = parseLocalEnumNames(sanitized);
        for (StaticFieldStatement statement : parseStaticFieldStatements(sanitized)) {
            if (!statement.isFinalField()) {
                findings.add(new BoundaryBypassFinding(
                    implLane ? IMPL_PURITY_RULE : SHARED_PURITY_RULE,
                    relPath,
                    "mutable static field is forbidden: " + normalizeToken(statement.raw())
                ));
                continue;
            }

            if (containsKnownMutableTypeToken(statement.typeRaw())) {
                findings.add(new BoundaryBypassFinding(
                    implLane ? IMPL_PURITY_RULE : SHARED_PURITY_RULE,
                    relPath,
                    "static final mutable container/atomic/threadlocal is forbidden: " + normalizeToken(statement.raw())
                ));
                continue;
            }

            if (!pureLane) {
                continue;
            }

            String declaredTypeFqcn = resolveTypeTokenFqcn(
                statement.typeRaw(),
                packageName,
                explicitImports
            );
            if (isAllowedPureConstantType(declaredTypeFqcn)
                || isAllowlistedImmutableType(declaredTypeFqcn, pureImmutableAllowlist)
                || isEnumConstantDeclaration(statement, declaredTypeFqcn, localEnums, packageName, explicitImports)) {
                continue;
            }

            String newTypeToken = extractNewTypeToken(statement.initializerRaw());
            if (newTypeToken != null) {
                String newTypeFqcn = resolveTypeTokenFqcn(newTypeToken, packageName, explicitImports);
                if (!isAllowlistedImmutableType(newTypeFqcn, pureImmutableAllowlist)) {
                    findings.add(new BoundaryBypassFinding(
                        SHARED_PURITY_RULE,
                        relPath,
                        "static final `new` initializer is forbidden in _shared.pure unless type is allowlisted immutable: "
                            + normalizeToken(statement.raw())
                    ));
                }
                continue;
            }

            findings.add(new BoundaryBypassFinding(
                SHARED_PURITY_RULE,
                relPath,
                "static final type is not an allowed pure constant type: " + normalizeToken(statement.raw())
            ));
        }

        if (stateLane) {
            String detail = firstStateStoreNoopUpdateDetail(sanitized);
            if (detail != null) {
                findings.add(new BoundaryBypassFinding(
                    STATE_STORE_NOOP_UPDATE_RULE,
                    relPath,
                    detail
                ));
            }
        }

        if (adapterLane) {
            String detail = firstStateStoreOpMisuseDetail(sanitized);
            if (detail != null) {
                findings.add(new BoundaryBypassFinding(
                    STATE_STORE_OP_MISUSE_RULE,
                    relPath,
                    detail
                ));
            }
        }

        return findings;
    }

    private static boolean isImplSourcePath(String relPath) {
        return relPath.startsWith(BLOCKS_ROOT_PREFIX) && relPath.contains("/impl/");
    }

    private static boolean isAdapterSourcePath(String relPath) {
        return relPath.startsWith(BLOCKS_ROOT_PREFIX) && relPath.contains(ADAPTER_SEGMENT);
    }

    private static boolean isSharedPureSourcePath(String relPath) {
        return relPath.startsWith(SHARED_PURE_ROOT_PREFIX);
    }

    private static boolean isSharedJavaOutsidePureOrState(String relPath) {
        return relPath.startsWith(SHARED_ROOT_PREFIX)
            && !relPath.startsWith(SHARED_PURE_ROOT_PREFIX)
            && !relPath.startsWith(SHARED_STATE_ROOT_PREFIX);
    }

    private static String firstStateStoreNoopUpdateDetail(String source) {
        for (MethodSlice method : extractMethodSlices(source)) {
            if (!UPDATE_METHOD_NAME_PATTERN.matcher(method.name()).matches()) {
                continue;
            }
            if (NOOP_MISSING_RETURN_PATTERN.matcher(method.body()).find()
                || NOOP_INLINE_MISSING_RETURN_PATTERN.matcher(method.body()).find()) {
                return "KIND=STATE_STORE_NOOP_UPDATE: method=" + method.name()
                    + ": silent missing-state return in _shared/state update path";
            }
        }
        return null;
    }

    private static String firstStateStoreOpMisuseDetail(String source) {
        for (MethodSlice method : extractMethodSlices(source)) {
            if (!CREATE_CALL_PATTERN.matcher(method.body()).find()) {
                continue;
            }
            if (!hasUpdatePathSignal(method)) {
                continue;
            }
            return "KIND=STATE_STORE_OP_MISUSE: method=" + method.name()
                + ": adapter update-path signals co-occur with state create-call signals";
        }
        return null;
    }

    private static boolean hasUpdatePathSignal(MethodSlice method) {
        if (UPDATE_METHOD_NAME_PATTERN.matcher(method.name()).matches()) {
            return true;
        }
        if (method.body().contains("updateBalance(")) {
            return true;
        }
        return hasBalanceMutationToken(method.body());
    }

    private static boolean hasBalanceMutationToken(String body) {
        return body.contains("balanceCents")
            || body.contains("setBalanceCents(")
            || body.contains("\"balanceCents\"");
    }

    private static List<MethodSlice> extractMethodSlices(String source) {
        ArrayList<MethodSlice> methods = new ArrayList<>();
        Matcher matcher = METHOD_SIGNATURE_PATTERN.matcher(source);
        while (matcher.find()) {
            String methodName = matcher.group(1);
            int openBrace = source.indexOf('{', matcher.end() - 1);
            if (openBrace < 0) {
                continue;
            }
            int closeBrace = findClosingBrace(source, openBrace);
            if (closeBrace < 0) {
                continue;
            }
            String body = source.substring(openBrace + 1, closeBrace);
            methods.add(new MethodSlice(methodName, body));
        }
        return methods;
    }

    private static String firstForbiddenPrefixToken(String source, Set<String> forbiddenPrefixes) {
        int bestIndex = Integer.MAX_VALUE;
        String bestToken = null;
        for (String token : forbiddenPrefixes) {
            int idx = source.indexOf(token);
            if (idx >= 0 && idx < bestIndex) {
                bestIndex = idx;
                bestToken = token;
            }
        }
        return bestToken;
    }

    private static String firstSynchronizedToken(String source) {
        Matcher matcher = SYNCHRONIZED_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private static Set<String> parseLocalEnumNames(String source) {
        TreeSet<String> enums = new TreeSet<>();
        Matcher matcher = LOCAL_ENUM_PATTERN.matcher(source);
        while (matcher.find()) {
            enums.add(matcher.group(1));
        }
        return enums;
    }

    private static List<StaticFieldStatement> parseStaticFieldStatements(String source) {
        ArrayList<StaticFieldStatement> statements = new ArrayList<>();
        Matcher matcher = STATIC_FIELD_STATEMENT_PATTERN.matcher(source);
        while (matcher.find()) {
            String raw = matcher.group().trim();
            if (raw.isEmpty()) {
                continue;
            }
            int eqIndex = raw.indexOf('=');
            int parenIndex = raw.indexOf('(');
            if (parenIndex >= 0 && (eqIndex < 0 || parenIndex < eqIndex)) {
                continue;
            }
            String noSemi = raw.endsWith(";") ? raw.substring(0, raw.length() - 1).trim() : raw;
            String initializer = "";
            String left = noSemi;
            if (eqIndex >= 0) {
                left = noSemi.substring(0, eqIndex).trim();
                initializer = noSemi.substring(eqIndex + 1).trim();
            }
            String leftNoModifiers = MODIFIER_PATTERN.matcher(left).replaceAll(" ");
            leftNoModifiers = leftNoModifiers.replaceAll("\\s+", " ").trim();
            Matcher fieldMatcher = Pattern.compile("^(.*\\S)\\s+([A-Za-z_][A-Za-z0-9_]*)$").matcher(leftNoModifiers);
            if (!fieldMatcher.find()) {
                continue;
            }
            String typeRaw = normalizeDeclaredType(fieldMatcher.group(1).trim());
            boolean isFinalField = Pattern.compile("\\bfinal\\b").matcher(raw).find();
            statements.add(new StaticFieldStatement(raw, typeRaw, initializer, isFinalField));
        }
        return statements;
    }

    private static String normalizeDeclaredType(String typeRaw) {
        String normalized = typeRaw.replaceAll("\\s+", "");
        normalized = stripGenerics(normalized);
        while (normalized.endsWith("[]")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.endsWith("...")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    private static boolean containsKnownMutableTypeToken(String typeRaw) {
        String typeLower = typeRaw.toLowerCase();
        for (String token : MUTABLE_TYPE_TOKENS) {
            String tokenLower = token.toLowerCase();
            if (typeLower.contains(tokenLower)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedPureConstantType(String fqcn) {
        if (fqcn == null || fqcn.isBlank()) {
            return false;
        }
        if (ALLOWED_PURE_CONSTANT_TYPES.contains(fqcn)) {
            return true;
        }
        String simple = simpleName(fqcn);
        return ALLOWED_PURE_CONSTANT_TYPES.contains("java.lang." + simple);
    }

    private static boolean isAllowlistedImmutableType(String fqcn, Set<String> allowlist) {
        if (fqcn == null || fqcn.isBlank()) {
            return false;
        }
        return allowlist.contains(fqcn);
    }

    private static boolean isEnumConstantDeclaration(
        StaticFieldStatement statement,
        String declaredTypeFqcn,
        Set<String> localEnums,
        String packageName,
        Map<String, String> explicitImports
    ) {
        if (declaredTypeFqcn == null || declaredTypeFqcn.isBlank()) {
            return false;
        }
        String declaredSimple = simpleName(declaredTypeFqcn);
        if (localEnums.contains(declaredSimple)) {
            return true;
        }
        String initializer = statement.initializerRaw().trim();
        Matcher matcher = ENUM_CONSTANT_INIT_PATTERN.matcher(initializer);
        if (!matcher.find()) {
            return false;
        }
        String initializerTypeToken = matcher.group(1);
        String initializerTypeFqcn = resolveTypeTokenFqcn(initializerTypeToken, packageName, explicitImports);
        if (initializerTypeFqcn == null || initializerTypeFqcn.isBlank()) {
            return false;
        }
        return declaredTypeFqcn.equals(initializerTypeFqcn) || declaredSimple.equals(simpleName(initializerTypeFqcn));
    }

    private static String extractNewTypeToken(String initializerRaw) {
        Matcher matcher = NEW_TYPE_PATTERN_FOR_FIELDS.matcher(initializerRaw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
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

    private static String parsePackageName(String source) {
        Matcher matcher = PACKAGE_DECL_PATTERN.matcher(source);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).trim();
    }

    private static Map<String, String> parseExplicitImports(String source) {
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

    private static String resolveTypeTokenFqcn(
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

    private static String stripGenerics(String token) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '<') {
                depth++;
                continue;
            }
            if (c == '>') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }
            if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString();
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

    private record StaticFieldStatement(
        String raw,
        String typeRaw,
        String initializerRaw,
        boolean isFinalField
    ) {
    }

    private record MethodSlice(
        String name,
        String body
    ) {
    }
}
