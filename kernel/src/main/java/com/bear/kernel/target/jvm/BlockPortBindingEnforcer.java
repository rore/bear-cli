package com.bear.kernel.target.jvm;

import com.bear.kernel.target.*;
import com.bear.kernel.identity.BlockIdentityCanonicalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
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

public final class BlockPortBindingEnforcer {
    public static final String RULE_BLOCK_PORT_IMPL_INVALID = "BLOCK_PORT_IMPL_INVALID";
    public static final String RULE_BLOCK_PORT_REFERENCE_FORBIDDEN = "BLOCK_PORT_REFERENCE_FORBIDDEN";
    public static final String RULE_BLOCK_PORT_INBOUND_EXECUTE_FORBIDDEN = "BLOCK_PORT_INBOUND_EXECUTE_FORBIDDEN";
    public static final String USER_MAIN_IMPL_DETAIL =
        "BLOCK_PORT_IMPL_INVALID: block-port interface must not be implemented in src/main/java; only generated client allowed";

    private static final String USER_MAIN_ROOT = "src/main/java/";
    private static final String APP_WIRING_ROOT = "src/main/java/com/";
    private static final String SHARED_ROOT = "src/main/java/blocks/_shared/";
    private static final String GENERATED_MAIN_ROOT = "build/generated/bear/src/main/java/";

    private static final Pattern TYPE_DECL_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:public|protected|private)?\\s*(abstract\\s+)?(?:final\\s+|sealed\\s+|non-sealed\\s+)?(?:static\\s+)?"
            + "(class|interface|record|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s*<[^\\{>]*>)?\\s*([^\\{;]*)\\{"
    );
    private static final Pattern EXTENDS_PATTERN = Pattern.compile("\\bextends\\s+([^\\{]+?)(?=\\bimplements\\b|$)");
    private static final Pattern IMPLEMENTS_PATTERN = Pattern.compile("\\bimplements\\s+([^\\{]+)$");
    private static final Pattern IMPORT_LINE_PATTERN = Pattern.compile("(?m)^\\s*import\\s+([A-Za-z_][A-Za-z0-9_\\.]+)\\s*;");
    private static final Pattern PACKAGE_LINE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_\\.]+)\\s*;");
    private static final Pattern FQCN_TOKEN_PATTERN = Pattern.compile("\\b[a-z_][a-z0-9_]*(?:\\.[a-z_][a-z0-9_]*)+\\b");
    private static final Pattern EXECUTE_CALL_PATTERN = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_\\.]*)\\s*\\.\\s*execute\\s*\\(");
    private static final Pattern VARIABLE_DECL_PATTERN = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_\\.]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=|;)");
    private static final Pattern SHARED_OWNER_ANNOTATION_PATTERN = Pattern.compile("@BearSharedOwner\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");

    private static final Map<String, OwnershipUniverse> OWNERSHIP_CACHE = new HashMap<>();

    private BlockPortBindingEnforcer() {
    }

    public static List<BoundaryBypassFinding> scan(
        Path projectRoot,
        List<WiringManifest> manifests,
        Set<String> inboundTargetWrapperFqcns
    ) throws IOException {
        if (manifests == null || manifests.isEmpty()) {
            return List.of();
        }

        List<BlockPortBindingContext> contexts = collectBindingContexts(manifests);
        if (contexts.isEmpty() && (inboundTargetWrapperFqcns == null || inboundTargetWrapperFqcns.isEmpty())) {
            return List.of();
        }

        TypeIndex userIndex = TypeIndex.scan(projectRoot, projectRoot.resolve(USER_MAIN_ROOT));
        TypeIndex generatedIndex = TypeIndex.scan(projectRoot, projectRoot.resolve(GENERATED_MAIN_ROOT));
        OwnershipUniverse ownershipUniverse = ownershipUniverseForInvocation(projectRoot, manifests);

        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        findings.addAll(enforcePortInterfaceImplementations(contexts, userIndex, generatedIndex));
        findings.addAll(enforceSharedOwnership(userIndex, manifests));
        findings.addAll(enforceReferenceGuards(projectRoot, contexts, inboundTargetWrapperFqcns, userIndex, ownershipUniverse));

        findings.sort(
            Comparator.comparing(BoundaryBypassFinding::path)
                .thenComparing(BoundaryBypassFinding::rule)
                .thenComparing(BoundaryBypassFinding::detail)
        );
        return findings;
    }

    private static OwnershipUniverse ownershipUniverseForInvocation(Path projectRoot, List<WiringManifest> manifests) {
        String projectRootIdentity = RepoPathNormalizer.normalizePathForIdentity(projectRoot.toAbsolutePath().normalize());
        ArrayList<String> manifestFingerprintParts = new ArrayList<>();
        for (WiringManifest manifest : manifests) {
            ArrayList<String> roots = new ArrayList<>();
            for (String root : manifest.governedSourceRoots()) {
                roots.add(RepoPathNormalizer.normalizePathForIdentity(root));
            }
            roots.sort(String::compareTo);
            manifestFingerprintParts.add(manifest.blockKey() + "=>" + String.join(",", roots));
        }
        manifestFingerprintParts.sort(String::compareTo);
        String cacheKey = projectRootIdentity + "|" + String.join("|", manifestFingerprintParts);
        synchronized (OWNERSHIP_CACHE) {
            OwnershipUniverse cached = OWNERSHIP_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            OwnershipUniverse created = OwnershipUniverse.fromManifests(manifests);
            OWNERSHIP_CACHE.put(cacheKey, created);
            return created;
        }
    }

    private static List<BlockPortBindingContext> collectBindingContexts(List<WiringManifest> manifests) {
        ArrayList<BlockPortBindingContext> contexts = new ArrayList<>();
        for (WiringManifest manifest : manifests) {
            for (BlockPortBinding binding : manifest.blockPortBindings()) {
                TreeSet<String> targetWrapperFqcns = new TreeSet<>();
                for (String op : binding.targetOps()) {
                    targetWrapperFqcns.add(wrapperFqcn(binding.targetBlock(), op));
                }
                contexts.add(new BlockPortBindingContext(
                    manifest.blockKey(),
                    binding.port(),
                    binding.targetBlock(),
                    binding.targetOps(),
                    binding.portInterfaceFqcn(),
                    binding.expectedClientImplFqcn(),
                    targetWrapperFqcns
                ));
            }
        }
        return List.copyOf(contexts);
    }

    private static List<BoundaryBypassFinding> enforcePortInterfaceImplementations(
        List<BlockPortBindingContext> contexts,
        TypeIndex userIndex,
        TypeIndex generatedIndex
    ) {
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        TreeSet<String> seenUserPaths = new TreeSet<>();

        for (BlockPortBindingContext context : contexts) {
            List<TypeDecl> userImplementors = userIndex.findConcreteImplementors(context.portInterfaceFqcn());
            for (TypeDecl decl : userImplementors) {
                if (!seenUserPaths.add(decl.path())) {
                    continue;
                }
                findings.add(new BoundaryBypassFinding(
                    RULE_BLOCK_PORT_IMPL_INVALID,
                    decl.path(),
                    USER_MAIN_IMPL_DETAIL
                ));
            }

            List<TypeDecl> generatedImplementors = generatedIndex.findConcreteImplementors(context.portInterfaceFqcn());
            if (generatedImplementors.size() != 1
                || !generatedImplementors.get(0).fqcn().equals(context.expectedClientImplFqcn())) {
                String path = generatedImplementors.isEmpty()
                    ? GENERATED_MAIN_ROOT.substring(0, GENERATED_MAIN_ROOT.length() - 1)
                    : generatedImplementors.get(0).path();
                String detail = "BLOCK_PORT_IMPL_INVALID: generated binding mismatch: interface="
                    + context.portInterfaceFqcn()
                    + ", expected="
                    + context.expectedClientImplFqcn()
                    + ", found="
                    + renderImplementorFqcns(generatedImplementors);
                findings.add(new BoundaryBypassFinding(RULE_BLOCK_PORT_IMPL_INVALID, path, detail));
            }
        }

        return findings;
    }

    private static String wrapperFqcn(String blockKey, String operationName) {
        String packageSegment = sanitizePackageSegment(blockKey);
        String blockName = toPascalCase(blockKey);
        String opName = toPascalCase(operationName);
        return "com.bear.generated." + packageSegment + "." + blockName + "_" + opName;
    }

    private static String sanitizePackageSegment(String raw) {
        List<String> tokens = BlockIdentityCanonicalizer.canonicalTokens(raw);
        if (tokens.isEmpty()) {
            return "block";
        }
        return String.join(".", tokens);
    }

    private static String toPascalCase(String raw) {
        List<String> tokens = BlockIdentityCanonicalizer.canonicalTokens(raw);
        if (tokens.isEmpty()) {
            return "Block";
        }
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            out.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        return out.toString();
    }

    private static String renderImplementorFqcns(List<TypeDecl> decls) {
        if (decls == null || decls.isEmpty()) {
            return "none";
        }
        TreeSet<String> fqcns = new TreeSet<>();
        for (TypeDecl decl : decls) {
            fqcns.add(decl.fqcn());
        }
        return String.join(",", fqcns);
    }

    private static List<BoundaryBypassFinding> enforceSharedOwnership(TypeIndex userIndex, List<WiringManifest> manifests) {
        TreeMap<String, String> ownerByGeneratedPackage = new TreeMap<>();
        for (WiringManifest manifest : manifests) {
            String pkg = packageOf(manifest.entrypointFqcn());
            if (pkg != null && !pkg.isBlank()) {
                ownerByGeneratedPackage.put(pkg, manifest.blockKey());
            }
        }

        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        for (TypeDecl decl : userIndex.types().values()) {
            if (!decl.path().startsWith(SHARED_ROOT)) {
                continue;
            }
            TreeSet<String> generatedPortInterfaces = userIndex.reachableGeneratedPortInterfaces(decl);
            if (generatedPortInterfaces.isEmpty()) {
                continue;
            }

            TreeSet<String> owners = new TreeSet<>();
            for (String portInterface : generatedPortInterfaces) {
                String owner = ownerByGeneratedPackage.get(packageOf(portInterface));
                if (owner != null && !owner.isBlank()) {
                    owners.add(owner);
                }
            }
            if (owners.size() > 1) {
                findings.add(new BoundaryBypassFinding(
                    RULE_BLOCK_PORT_IMPL_INVALID,
                    decl.path(),
                    "BLOCK_PORT_IMPL_INVALID: _shared generated-port implementation has multiple owners: " + String.join(",", owners)
                ));
                continue;
            }
            if (owners.size() == 1) {
                String expectedOwner = owners.first();
                String declaredOwner = extractSharedOwnerAnnotation(decl.sourceRaw());
                if (!expectedOwner.equals(declaredOwner)) {
                    findings.add(new BoundaryBypassFinding(
                        RULE_BLOCK_PORT_IMPL_INVALID,
                        decl.path(),
                        "BLOCK_PORT_IMPL_INVALID: _shared generated-port implementation requires @BearSharedOwner(\""
                            + expectedOwner
                            + "\")"
                    ));
                }
            }
        }
        return findings;
    }

    private static List<BoundaryBypassFinding> enforceReferenceGuards(
        Path projectRoot,
        List<BlockPortBindingContext> contexts,
        Set<String> inboundTargetWrapperFqcns,
        TypeIndex userIndex,
        OwnershipUniverse ownershipUniverse
    ) throws IOException {
        if (!Files.isDirectory(projectRoot.resolve(USER_MAIN_ROOT))) {
            return List.of();
        }

        TreeSet<String> inboundSet = new TreeSet<>();
        if (inboundTargetWrapperFqcns != null) {
            inboundSet.addAll(inboundTargetWrapperFqcns);
        }

        TreeMap<String, List<BlockPortBindingContext>> contextsBySource = new TreeMap<>();
        TreeSet<String> targetBlockKeys = new TreeSet<>();
        TreeMap<String, TreeSet<String>> wrappersByTargetBlock = new TreeMap<>();
        for (BlockPortBindingContext context : contexts) {
            contextsBySource.computeIfAbsent(context.sourceBlock(), ignored -> new ArrayList<>()).add(context);
            targetBlockKeys.add(context.targetBlock());
            wrappersByTargetBlock.computeIfAbsent(context.targetBlock(), ignored -> new TreeSet<>())
                .addAll(context.targetWrapperFqcns());
        }

        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        ArrayList<Path> javaFiles = collectJavaFiles(projectRoot.resolve(USER_MAIN_ROOT));
        for (Path file : javaFiles) {
            String relPath = normalizePath(projectRoot.relativize(file).toString());
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String sanitized = BoundaryBypassScanner.stripJavaCommentsStringsAndChars(source);
            String packageName = BoundaryBypassScanner.parsePackageName(sanitized);
            Map<String, String> importsBySimple = BoundaryBypassScanner.parseExplicitImports(sanitized);
            Map<String, String> variableTypes = parseVariableTypeMap(sanitized, packageName, importsBySimple);

            if (relPath.startsWith(APP_WIRING_ROOT)) {
                findings.addAll(findInboundExecuteViolations(relPath, sanitized, packageName, importsBySimple, variableTypes, inboundSet));
                continue;
            }

            if (relPath.startsWith(SHARED_ROOT)) {
                findings.addAll(findSharedLaneNarrowGuardViolations(
                    relPath,
                    source,
                    sanitized,
                    userIndex,
                    targetBlockKeys,
                    wrappersByTargetBlock
                ));
                continue;
            }

            if (!ownershipUniverse.isEligible(relPath)) {
                continue;
            }
            String ownerBlock = ownershipUniverse.resolveOwnerBlock(relPath);
            if (ownerBlock == null || ownerBlock.isBlank()) {
                continue;
            }

            List<BlockPortBindingContext> sourceContexts = contextsBySource.getOrDefault(ownerBlock, List.of());
            for (BlockPortBindingContext context : sourceContexts) {
                if (belongsToTargetPackage(packageName, context.targetBlock())) {
                    continue;
                }
                findings.addAll(findForbiddenInternalReferences(relPath, source, sanitized, packageName, importsBySimple, variableTypes, context));
            }
        }
        return findings;
    }

    private static List<BoundaryBypassFinding> findSharedLaneNarrowGuardViolations(
        String relPath,
        String source,
        String sanitized,
        TypeIndex userIndex,
        Set<String> targetBlockKeys,
        Map<String, TreeSet<String>> wrappersByTargetBlock
    ) {
        if (!userIndex.isConcreteSharedGeneratedPortImplementor(relPath)) {
            return List.of();
        }

        TreeSet<String> details = new TreeSet<>();
        for (String targetBlockKey : targetBlockKeys) {
            String targetBlocksPrefix = "blocks." + sanitizePackageSegment(targetBlockKey);

            Matcher importMatcher = IMPORT_LINE_PATTERN.matcher(source);
            while (importMatcher.find()) {
                String imported = importMatcher.group(1);
                if (imported.startsWith(targetBlocksPrefix + ".") || imported.equals(targetBlocksPrefix)) {
                    details.add("BLOCK_PORT_REFERENCE_FORBIDDEN: _shared generated-port implementor imports target internals: " + imported);
                }
            }

            Matcher fqcnMatcher = FQCN_TOKEN_PATTERN.matcher(sanitized);
            while (fqcnMatcher.find()) {
                String token = fqcnMatcher.group();
                if (token.startsWith(targetBlocksPrefix + ".") || token.equals(targetBlocksPrefix)) {
                    details.add("BLOCK_PORT_REFERENCE_FORBIDDEN: _shared generated-port implementor references target internals: " + token);
                }
            }

            if (sanitized.contains(targetBlocksPrefix + ".")) {
                details.add("BLOCK_PORT_REFERENCE_FORBIDDEN: _shared generated-port implementor references target internals token: "
                    + targetBlocksPrefix + ".*");
            }

            for (String wrapperFqcn : wrappersByTargetBlock.getOrDefault(targetBlockKey, new TreeSet<>())) {
                Matcher wrapperMatcher = FQCN_TOKEN_PATTERN.matcher(sanitized);
                while (wrapperMatcher.find()) {
                    String token = wrapperMatcher.group();
                    if (wrapperFqcn.equals(token)) {
                        details.add("BLOCK_PORT_REFERENCE_FORBIDDEN: _shared generated-port implementor references target wrapper FQCN: " + wrapperFqcn);
                    }
                }
            }
        }

        if (details.isEmpty()) {
            return List.of();
        }
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        for (String detail : details) {
            findings.add(new BoundaryBypassFinding(
                RULE_BLOCK_PORT_REFERENCE_FORBIDDEN,
                relPath,
                detail
            ));
        }
        return findings;
    }

    private static List<BoundaryBypassFinding> findInboundExecuteViolations(
        String relPath,
        String sanitized,
        String packageName,
        Map<String, String> importsBySimple,
        Map<String, String> variableTypes,
        Set<String> inboundWrapperFqcns
    ) {
        if (inboundWrapperFqcns.isEmpty()) {
            return List.of();
        }
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        Matcher executeMatcher = EXECUTE_CALL_PATTERN.matcher(sanitized);
        while (executeMatcher.find()) {
            String receiver = executeMatcher.group(1);
            String receiverFqcn = resolveReceiverFqcn(receiver, packageName, importsBySimple, variableTypes);
            if (receiverFqcn != null && inboundWrapperFqcns.contains(receiverFqcn)) {
                findings.add(new BoundaryBypassFinding(
                    RULE_BLOCK_PORT_INBOUND_EXECUTE_FORBIDDEN,
                    relPath,
                    "BLOCK_PORT_REFERENCE_FORBIDDEN: app wiring may not directly execute inbound target wrapper: "
                        + receiverFqcn
                        + ".execute(...)"
                ));
            }
        }
        return findings;
    }

    private static List<BoundaryBypassFinding> findForbiddenInternalReferences(
        String relPath,
        String source,
        String sanitized,
        String packageName,
        Map<String, String> importsBySimple,
        Map<String, String> variableTypes,
        BlockPortBindingContext context
    ) {
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        String targetBlocksPrefix = "blocks." + sanitizePackageSegment(context.targetBlock());

        Matcher importMatcher = IMPORT_LINE_PATTERN.matcher(source);
        while (importMatcher.find()) {
            String imported = importMatcher.group(1);
            if (imported.startsWith(targetBlocksPrefix + ".") || imported.equals(targetBlocksPrefix)) {
                findings.add(new BoundaryBypassFinding(
                    RULE_BLOCK_PORT_REFERENCE_FORBIDDEN,
                    relPath,
                    "BLOCK_PORT_REFERENCE_FORBIDDEN: source block directly imports target internals: " + imported
                ));
            }
        }

        Matcher packageMatcher = PACKAGE_LINE_PATTERN.matcher(source);
        if (packageMatcher.find()) {
            String pkg = packageMatcher.group(1);
            if (pkg.startsWith(targetBlocksPrefix + ".") || pkg.equals(targetBlocksPrefix)) {
                findings.add(new BoundaryBypassFinding(
                    RULE_BLOCK_PORT_REFERENCE_FORBIDDEN,
                    relPath,
                    "BLOCK_PORT_REFERENCE_FORBIDDEN: source block package overlaps target internals: " + pkg
                ));
            }
        }

        TreeSet<String> targetWrappers = context.targetWrapperFqcns();
        if (!targetWrappers.isEmpty()) {
            Matcher fqcnMatcher = FQCN_TOKEN_PATTERN.matcher(sanitized);
            while (fqcnMatcher.find()) {
                String token = fqcnMatcher.group();
                if (targetWrappers.contains(token)) {
                    findings.add(new BoundaryBypassFinding(
                        RULE_BLOCK_PORT_REFERENCE_FORBIDDEN,
                        relPath,
                        "BLOCK_PORT_REFERENCE_FORBIDDEN: source block references target wrapper FQCN: " + token
                    ));
                }
            }

            Matcher executeMatcher = EXECUTE_CALL_PATTERN.matcher(sanitized);
            while (executeMatcher.find()) {
                String receiver = executeMatcher.group(1);
                String receiverFqcn = resolveReceiverFqcn(receiver, packageName, importsBySimple, variableTypes);
                if (receiverFqcn != null && targetWrappers.contains(receiverFqcn)) {
                    findings.add(new BoundaryBypassFinding(
                        RULE_BLOCK_PORT_REFERENCE_FORBIDDEN,
                        relPath,
                        "BLOCK_PORT_REFERENCE_FORBIDDEN: source block directly executes target wrapper: "
                            + receiverFqcn
                            + ".execute(...)"
                    ));
                }
            }
        }

        return findings;
    }

    private static boolean belongsToTargetPackage(String packageName, String targetBlock) {
        if (packageName == null || packageName.isBlank()) {
            return false;
        }
        String targetBlocksPrefix = "blocks." + sanitizePackageSegment(targetBlock);
        return packageName.equals(targetBlocksPrefix) || packageName.startsWith(targetBlocksPrefix + ".");
    }

    private static String resolveReceiverFqcn(
        String receiverToken,
        String packageName,
        Map<String, String> importsBySimple,
        Map<String, String> variableTypes
    ) {
        if (receiverToken == null || receiverToken.isBlank()) {
            return null;
        }
        if (receiverToken.contains(".")) {
            if (Character.isLowerCase(receiverToken.charAt(0))) {
                return receiverToken;
            }
            return null;
        }

        String declaredType = variableTypes.get(receiverToken);
        if (declaredType != null) {
            return declaredType;
        }

        String imported = importsBySimple.get(receiverToken);
        if (imported != null) {
            return imported;
        }
        if (packageName == null || packageName.isBlank()) {
            return null;
        }
        return packageName + "." + receiverToken;
    }

    private static Map<String, String> parseVariableTypeMap(
        String sanitized,
        String packageName,
        Map<String, String> importsBySimple
    ) {
        TreeMap<String, String> byVariable = new TreeMap<>();
        Matcher matcher = VARIABLE_DECL_PATTERN.matcher(sanitized);
        while (matcher.find()) {
            String typeToken = matcher.group(1);
            String varName = matcher.group(2);
            if ("return".equals(typeToken) || "new".equals(typeToken)) {
                continue;
            }
            String fqcn = BoundaryBypassScanner.resolveTypeTokenFqcn(typeToken, packageName, importsBySimple);
            if (fqcn != null) {
                byVariable.put(varName, fqcn);
            }
        }
        return byVariable;
    }

    private static String extractSharedOwnerAnnotation(String sourceRaw) {
        Matcher matcher = SHARED_OWNER_ANNOTATION_PATTERN.matcher(sourceRaw);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private static ArrayList<Path> collectJavaFiles(Path root) throws IOException {
        ArrayList<Path> files = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return files;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(path -> path.toString().replace('\\', '/')));
        return files;
    }

    private static String normalizePath(String raw) {
        return raw.replace('\\', '/');
    }




    private static String packageOf(String fqcn) {
        if (fqcn == null) {
            return null;
        }
        int idx = fqcn.lastIndexOf('.');
        if (idx <= 0) {
            return null;
        }
        return fqcn.substring(0, idx);
    }

    private record BlockPortBindingContext(
        String sourceBlock,
        String port,
        String targetBlock,
        List<String> targetOps,
        String portInterfaceFqcn,
        String expectedClientImplFqcn,
        TreeSet<String> targetWrapperFqcns
    ) {
    }
    private record RootOwner(String blockKey, String rootIdentity, String rootPrefix) {
    }

    private record OwnershipUniverse(List<RootOwner> rootOwners) {
        static OwnershipUniverse fromManifests(List<WiringManifest> manifests) {
            ArrayList<RootOwner> owners = new ArrayList<>();
            for (WiringManifest manifest : manifests) {
                for (String root : manifest.governedSourceRoots()) {
                    String rootIdentity = RepoPathNormalizer.normalizePathForIdentity(root);
                    if (rootIdentity.isBlank()) {
                        continue;
                    }
                    owners.add(new RootOwner(
                        manifest.blockKey(),
                        rootIdentity,
                        RepoPathNormalizer.normalizePathForPrefix(rootIdentity)
                    ));
                }
            }
            owners.sort(
                Comparator.comparing(RootOwner::blockKey)
                    .thenComparing(RootOwner::rootIdentity)
            );
            return new OwnershipUniverse(List.copyOf(owners));
        }

        boolean isEligible(String relPath) {
            String normalizedPath = RepoPathNormalizer.normalizePathForIdentity(relPath);
            for (RootOwner owner : rootOwners) {
                if (RepoPathNormalizer.hasSegmentPrefix(normalizedPath, owner.rootPrefix())) {
                    return true;
                }
            }
            return false;
        }

        String resolveOwnerBlock(String relPath) {
            String normalizedPath = RepoPathNormalizer.normalizePathForIdentity(relPath);
            RootOwner best = null;
            for (RootOwner owner : rootOwners) {
                if (!RepoPathNormalizer.hasSegmentPrefix(normalizedPath, owner.rootPrefix())) {
                    continue;
                }
                if (best == null) {
                    best = owner;
                    continue;
                }
                int lengthCompare = Integer.compare(owner.rootIdentity().length(), best.rootIdentity().length());
                if (lengthCompare > 0) {
                    best = owner;
                    continue;
                }
                if (lengthCompare < 0) {
                    continue;
                }
                int blockCompare = owner.blockKey().compareTo(best.blockKey());
                if (blockCompare < 0) {
                    best = owner;
                    continue;
                }
                if (blockCompare > 0) {
                    continue;
                }
                if (owner.rootIdentity().compareTo(best.rootIdentity()) < 0) {
                    best = owner;
                }
            }
            return best == null ? null : best.blockKey();
        }
    }

    private enum TypeKind {
        CLASS,
        INTERFACE,
        RECORD,
        ENUM
    }

    private record TypeDecl(
        String fqcn,
        TypeKind kind,
        boolean abstractType,
        String superClass,
        List<String> interfaces,
        String path,
        String sourceRaw
    ) {
        boolean isConcrete() {
            if (kind == TypeKind.INTERFACE) {
                return false;
            }
            return !abstractType;
        }
    }

    private static final class TypeIndex {
        private final Map<String, TypeDecl> types;
        private final Map<String, List<TypeDecl>> typesByPath;

        private TypeIndex(Map<String, TypeDecl> types, Map<String, List<TypeDecl>> typesByPath) {
            this.types = types;
            this.typesByPath = typesByPath;
        }

        static TypeIndex scan(Path projectRoot, Path sourceRoot) throws IOException {
            TreeMap<String, TypeDecl> types = new TreeMap<>();
            TreeMap<String, ArrayList<TypeDecl>> byPath = new TreeMap<>();
            if (!Files.isDirectory(sourceRoot)) {
                return new TypeIndex(types, Map.of());
            }
            ArrayList<Path> javaFiles = collectJavaFiles(sourceRoot);
            for (Path file : javaFiles) {
                String relPath = normalizePath(projectRoot.relativize(file).toString());
                String sourceRaw = Files.readString(file, StandardCharsets.UTF_8);
                String sanitized = BoundaryBypassScanner.stripJavaCommentsStringsAndChars(sourceRaw);
                String packageName = BoundaryBypassScanner.parsePackageName(sanitized);
                Map<String, String> importsBySimple = BoundaryBypassScanner.parseExplicitImports(sanitized);

                Matcher matcher = TYPE_DECL_PATTERN.matcher(sanitized);
                while (matcher.find()) {
                    boolean abstractType = matcher.group(1) != null;
                    String kindToken = matcher.group(2);
                    String typeName = matcher.group(3);
                    String signatureTail = matcher.group(4) == null ? "" : matcher.group(4);
                    TypeKind kind = parseKind(kindToken);
                    String fqcn = packageName == null || packageName.isBlank()
                        ? typeName
                        : packageName + "." + typeName;

                    String superClass = null;
                    ArrayList<String> interfaces = new ArrayList<>();
                    if (kind == TypeKind.INTERFACE) {
                        Matcher extendsMatcher = EXTENDS_PATTERN.matcher(signatureTail);
                        if (extendsMatcher.find()) {
                            interfaces.addAll(resolveTypeList(extendsMatcher.group(1), importsBySimple, packageName));
                        }
                    } else {
                        Matcher extendsMatcher = EXTENDS_PATTERN.matcher(signatureTail);
                        if (extendsMatcher.find()) {
                            List<String> supers = resolveTypeList(extendsMatcher.group(1), importsBySimple, packageName);
                            if (!supers.isEmpty()) {
                                superClass = supers.get(0);
                            }
                        }
                        Matcher implementsMatcher = IMPLEMENTS_PATTERN.matcher(signatureTail);
                        if (implementsMatcher.find()) {
                            interfaces.addAll(resolveTypeList(implementsMatcher.group(1), importsBySimple, packageName));
                        }
                    }
                    interfaces.sort(String::compareTo);
                    TypeDecl decl = new TypeDecl(
                        fqcn,
                        kind,
                        abstractType,
                        superClass,
                        List.copyOf(interfaces),
                        relPath,
                        sourceRaw
                    );
                    types.put(fqcn, decl);
                    byPath.computeIfAbsent(relPath, ignored -> new ArrayList<>()).add(decl);
                }
            }
            TreeMap<String, List<TypeDecl>> frozenByPath = new TreeMap<>();
            for (Map.Entry<String, ArrayList<TypeDecl>> entry : byPath.entrySet()) {
                entry.getValue().sort(Comparator.comparing(TypeDecl::fqcn));
                frozenByPath.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return new TypeIndex(types, Map.copyOf(frozenByPath));
        }

        Map<String, TypeDecl> types() {
            return types;
        }

        boolean isConcreteSharedGeneratedPortImplementor(String relPath) {
            List<TypeDecl> candidates = typesByPath.getOrDefault(relPath, List.of());
            for (TypeDecl decl : candidates) {
                if (!decl.isConcrete()) {
                    continue;
                }
                if (!decl.path().startsWith(SHARED_ROOT)) {
                    continue;
                }
                if (!reachableGeneratedPortInterfaces(decl).isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        List<TypeDecl> findConcreteImplementors(String interfaceFqcn) {
            ArrayList<TypeDecl> matches = new ArrayList<>();
            for (TypeDecl decl : types.values()) {
                if (!decl.isConcrete()) {
                    continue;
                }
                if (implementsInterface(decl, interfaceFqcn, new HashSet<>())) {
                    matches.add(decl);
                }
            }
            matches.sort(Comparator.comparing(TypeDecl::path).thenComparing(TypeDecl::fqcn));
            return List.copyOf(matches);
        }

        TreeSet<String> reachableGeneratedPortInterfaces(TypeDecl decl) {
            TreeSet<String> out = new TreeSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>(decl.interfaces());
            HashSet<String> seen = new HashSet<>();
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                if (!seen.add(current)) {
                    continue;
                }
                if (current.startsWith("com.bear.generated.") && current.endsWith("Port")) {
                    out.add(current);
                }
                TypeDecl iface = types.get(current);
                if (iface != null) {
                    queue.addAll(iface.interfaces());
                }
            }
            return out;
        }

        private boolean implementsInterface(TypeDecl decl, String targetInterface, Set<String> visiting) {
            if (!visiting.add(decl.fqcn())) {
                return false;
            }
            for (String iface : decl.interfaces()) {
                if (iface.equals(targetInterface)) {
                    return true;
                }
                if (interfaceExtends(iface, targetInterface, new HashSet<>())) {
                    return true;
                }
            }
            if (decl.superClass() != null) {
                TypeDecl superDecl = types.get(decl.superClass());
                if (superDecl != null && implementsInterface(superDecl, targetInterface, visiting)) {
                    return true;
                }
            }
            return false;
        }

        private boolean interfaceExtends(String ifaceFqcn, String targetInterface, Set<String> visiting) {
            if (ifaceFqcn.equals(targetInterface)) {
                return true;
            }
            if (!visiting.add(ifaceFqcn)) {
                return false;
            }
            TypeDecl ifaceDecl = types.get(ifaceFqcn);
            if (ifaceDecl == null) {
                return false;
            }
            for (String parent : ifaceDecl.interfaces()) {
                if (interfaceExtends(parent, targetInterface, visiting)) {
                    return true;
                }
            }
            if (ifaceDecl.superClass() != null) {
                TypeDecl superDecl = types.get(ifaceDecl.superClass());
                if (superDecl != null && interfaceExtends(superDecl.fqcn(), targetInterface, visiting)) {
                    return true;
                }
            }
            return false;
        }

        private static TypeKind parseKind(String token) {
            return switch (token) {
                case "class" -> TypeKind.CLASS;
                case "interface" -> TypeKind.INTERFACE;
                case "record" -> TypeKind.RECORD;
                case "enum" -> TypeKind.ENUM;
                default -> throw new IllegalStateException("Unknown type kind: " + token);
            };
        }

        private static List<String> resolveTypeList(String raw, Map<String, String> importsBySimple, String packageName) {
            ArrayList<String> resolved = new ArrayList<>();
            for (String token : splitTypeList(raw)) {
                String normalized = stripGenerics(token).trim();
                if (normalized.isBlank()) {
                    continue;
                }
                String fqcn = BoundaryBypassScanner.resolveTypeTokenFqcn(normalized, packageName, importsBySimple);
                if (fqcn != null) {
                    resolved.add(fqcn);
                }
            }
            return resolved;
        }

        private static List<String> splitTypeList(String payload) {
            ArrayList<String> out = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int genericDepth = 0;
            for (int i = 0; i < payload.length(); i++) {
                char c = payload.charAt(i);
                if (c == '<') {
                    genericDepth++;
                    current.append(c);
                    continue;
                }
                if (c == '>') {
                    if (genericDepth > 0) {
                        genericDepth--;
                    }
                    current.append(c);
                    continue;
                }
                if (c == ',' && genericDepth == 0) {
                    out.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
                current.append(c);
            }
            String tail = current.toString().trim();
            if (!tail.isBlank()) {
                out.add(tail);
            }
            return out;
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
    }
}














