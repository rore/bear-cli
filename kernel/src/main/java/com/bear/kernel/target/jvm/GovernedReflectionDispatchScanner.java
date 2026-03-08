package com.bear.kernel.target.jvm;

import com.bear.kernel.target.*;
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

public final class GovernedReflectionDispatchScanner {
    private static final String USER_MAIN_ROOT = "src/main/java/";
    private static final String SHARED_ROOT = "src/main/java/blocks/_shared/";

    private static final Pattern TYPE_DECL_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:public|protected|private)?\\s*(abstract\\s+)?(?:final\\s+|sealed\\s+|non-sealed\\s+)?(?:static\\s+)?"
            + "(class|interface|record|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s*<[^\\{>]*>)?\\s*([^\\{;]*)\\{"
    );
    private static final Pattern EXTENDS_PATTERN = Pattern.compile("\\bextends\\s+([^\\{]+?)(?=\\bimplements\\b|$)");
    private static final Pattern IMPLEMENTS_PATTERN = Pattern.compile("\\bimplements\\s+([^\\{]+)$");

    private static final List<TokenSpec> TOKEN_SPECS = List.of(
        new TokenSpec("java.lang.reflect.", Pattern.compile("\\bjava\\s*\\.\\s*lang\\s*\\.\\s*reflect\\s*\\.")),
        new TokenSpec("java.lang.invoke.", Pattern.compile("\\bjava\\s*\\.\\s*lang\\s*\\.\\s*invoke\\s*\\.")),
        new TokenSpec("MethodHandles.", Pattern.compile("\\bMethodHandles\\s*\\.")),
        new TokenSpec("Lookup.", Pattern.compile("\\bLookup\\s*\\.")),
        new TokenSpec("LambdaMetafactory.", Pattern.compile("\\bLambdaMetafactory\\s*\\.")),
        new TokenSpec("Class.forName(", Pattern.compile("\\bClass\\s*\\.\\s*forName\\s*\\(")),
        new TokenSpec(".getDeclaredMethod(", Pattern.compile("\\.\\s*getDeclaredMethod\\s*\\(")),
        new TokenSpec(".getMethod(", Pattern.compile("\\.\\s*getMethod\\s*\\(")),
        new TokenSpec(".getDeclaredMethods(", Pattern.compile("\\.\\s*getDeclaredMethods\\s*\\(")),
        new TokenSpec(".getMethods(", Pattern.compile("\\.\\s*getMethods\\s*\\(")),
        new TokenSpec(".getDeclaredConstructor(", Pattern.compile("\\.\\s*getDeclaredConstructor\\s*\\(")),
        new TokenSpec(".getConstructor(", Pattern.compile("\\.\\s*getConstructor\\s*\\(")),
        new TokenSpec(".newInstance(", Pattern.compile("\\.\\s*newInstance\\s*\\(")),
        new TokenSpec(".invoke(", Pattern.compile("\\.\\s*invoke\\s*\\(")),
        new TokenSpec("Proxy.newProxyInstance(", Pattern.compile("\\bProxy\\s*\\.\\s*newProxyInstance\\s*\\(")),
        new TokenSpec("setAccessible(", Pattern.compile("\\bsetAccessible\\s*\\("))
    );

    private GovernedReflectionDispatchScanner() {
    }

    public static List<UndeclaredReachFinding> scanForbiddenReflectionDispatch(
        Path projectRoot,
        List<WiringManifest> manifests
    ) throws IOException {
        if (manifests == null || manifests.isEmpty()) {
            return List.of();
        }
        Path userMainRoot = projectRoot.resolve(USER_MAIN_ROOT);
        if (!Files.isDirectory(userMainRoot)) {
            return List.of();
        }

        OwnershipUniverse ownershipUniverse = OwnershipUniverse.fromManifests(manifests);
        TypeIndex userTypeIndex = TypeIndex.scan(projectRoot, userMainRoot);
        ArrayList<Path> javaFiles = collectJavaFiles(userMainRoot);

        ArrayList<ReflectionDispatchFinding> findings = new ArrayList<>();
        for (Path file : javaFiles) {
            String relPath = RepoPathNormalizer.normalizePathForIdentity(projectRoot.relativize(file));
            if (!ownershipUniverse.isEligible(relPath)) {
                continue;
            }
            if (relPath.startsWith(SHARED_ROOT)
                && !userTypeIndex.isConcreteSharedGeneratedPortImplementor(relPath)) {
                continue;
            }

            String source = Files.readString(file, StandardCharsets.UTF_8);
            String sanitized = BoundaryBypassScanner.stripJavaCommentsStringsAndChars(source);
            String token = firstMatchedToken(sanitized);
            if (token == null) {
                continue;
            }
            findings.add(new ReflectionDispatchFinding(relPath, token));
        }

        findings.sort(
            Comparator.comparing(ReflectionDispatchFinding::path)
                .thenComparingInt(finding -> tokenOrder(finding.token()))
        );
        ArrayList<UndeclaredReachFinding> out = new ArrayList<>();
        for (ReflectionDispatchFinding finding : findings) {
            out.add(new UndeclaredReachFinding(
                finding.path(),
                "REACH_HYGIENE: KIND=REFLECTION_DISPATCH token=" + finding.token()
            ));
        }
        return List.copyOf(out);
    }

    private static int tokenOrder(String token) {
        for (int i = 0; i < TOKEN_SPECS.size(); i++) {
            if (TOKEN_SPECS.get(i).canonicalToken().equals(token)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String firstMatchedToken(String sanitizedSource) {
        for (TokenSpec tokenSpec : TOKEN_SPECS) {
            if (tokenSpec.pattern().matcher(sanitizedSource).find()) {
                return tokenSpec.canonicalToken();
            }
        }
        return null;
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

    private record TokenSpec(String canonicalToken, Pattern pattern) {
    }

    private record ReflectionDispatchFinding(String path, String token) {
    }

    private record RootOwner(String blockKey, String rootPrefix) {
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
                        RepoPathNormalizer.normalizePathForPrefix(rootIdentity)
                    ));
                }
            }
            owners.sort(Comparator.comparing(RootOwner::blockKey).thenComparing(RootOwner::rootPrefix));
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
        String path
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
                String relPath = RepoPathNormalizer.normalizePathForIdentity(projectRoot.relativize(file));
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
                        relPath
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

        private TreeSet<String> reachableGeneratedPortInterfaces(TypeDecl decl) {
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
                TypeDecl superDecl = types.get(current);
                if (superDecl != null && superDecl.superClass() != null) {
                    queue.addLast(superDecl.superClass());
                }
            }
            if (decl.superClass() != null) {
                TypeDecl superDecl = types.get(decl.superClass());
                if (superDecl != null) {
                    out.addAll(reachableGeneratedPortInterfaces(superDecl));
                }
            }
            return out;
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


