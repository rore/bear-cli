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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PortImplContainmentScanner {
    public static final String AMBIGUOUS_PORT_OWNER_REASON_CODE = "AMBIGUOUS_PORT_OWNER";
    public static final String ALLOW_MULTI_BLOCK_PORT_IMPL_MARKER = "// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL";
    public static final String MULTI_BLOCK_PORT_IMPL_FORBIDDEN_KIND = "MULTI_BLOCK_PORT_IMPL_FORBIDDEN";
    public static final String MARKER_MISUSED_OUTSIDE_SHARED_KIND = "MARKER_MISUSED_OUTSIDE_SHARED";

    private static final Pattern PACKAGE_DECL_PATTERN = Pattern.compile(
        "(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_\\.]*)\\s*;"
    );
    private static final Pattern IMPORT_DECL_PATTERN = Pattern.compile(
        "(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_][A-Za-z0-9_\\.]*)\\s*;"
    );
    private static final Pattern IMPLEMENTS_DECL_PATTERN = Pattern.compile(
        "\\b(?:class|record|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b[^\\{;]*\\bimplements\\s+([^\\{]+)\\{",
        Pattern.DOTALL
    );
    private static final int MARKER_WINDOW_NON_EMPTY_LINES = 5;
    private static final String SHARED_GOVERNED_ROOT_PREFIX = "src/main/java/blocks/_shared/";

    private PortImplContainmentScanner() {
    }

    public static List<PortImplContainmentFinding> scanPortImplOutsideGovernedRoots(
        Path projectRoot,
        List<WiringManifest> manifests
    ) throws IOException, ManifestParseException {
        if (manifests == null || manifests.isEmpty()) {
            return List.of();
        }

        Map<String, WiringManifest> ownerByGeneratedPackage = buildOwnerByGeneratedPackage(manifests);
        if (ownerByGeneratedPackage.isEmpty()) {
            return List.of();
        }

        Path srcMainJava = projectRoot.resolve("src/main/java").normalize();
        if (!Files.isDirectory(srcMainJava)) {
            return List.of();
        }

        ArrayList<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(srcMainJava, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        javaFiles.sort(Comparator.comparing(path -> projectRoot.relativize(path).toString().replace('\\', '/')));

        ArrayList<PortImplContainmentFinding> findings = new ArrayList<>();
        for (Path file : javaFiles) {
            String relPath = projectRoot.relativize(file).toString().replace('\\', '/');
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String sanitized = BoundaryBypassScanner.stripJavaCommentsStringsAndChars(source);
            String packageName = parsePackageName(sanitized);
            Map<String, String> explicitImports = parseExplicitImports(sanitized);

            Matcher matcher = IMPLEMENTS_DECL_PATTERN.matcher(sanitized);
            while (matcher.find()) {
                String className = matcher.group(1).trim();
                String interfacesRaw = matcher.group(2).trim();
                if (interfacesRaw.isEmpty()) {
                    continue;
                }
                String implClassFqcn = packageName.isBlank() ? className : packageName + "." + className;
                for (String token : splitInterfaces(interfacesRaw)) {
                    String resolvedInterface = resolveInterfaceFqcn(token, explicitImports, packageName);
                    if (resolvedInterface == null || !isGeneratedPortInterface(resolvedInterface)) {
                        continue;
                    }
                    WiringManifest owner = ownerByGeneratedPackage.get(packageOfFqcn(resolvedInterface));
                    if (owner == null) {
                        // Missing owner in this manifest scope is non-fatal by contract.
                        continue;
                    }
                    if (!isUnderAnyGovernedRoot(relPath, owner.governedSourceRoots())) {
                        findings.add(new PortImplContainmentFinding(resolvedInterface, implClassFqcn, relPath));
                    }
                }
            }
        }

        findings.sort(
            Comparator.comparing(PortImplContainmentFinding::path)
                .thenComparing(PortImplContainmentFinding::interfaceFqcn)
                .thenComparing(PortImplContainmentFinding::implClassFqcn)
        );
        return findings;
    }

    public static List<MultiBlockPortImplFinding> scanMultiBlockPortImplFindings(
        Path projectRoot,
        List<WiringManifest> manifests
    ) throws IOException, ManifestParseException {
        if (manifests == null || manifests.isEmpty()) {
            return List.of();
        }

        Map<String, WiringManifest> ownerByGeneratedPackage = buildOwnerByGeneratedPackage(manifests);
        if (ownerByGeneratedPackage.isEmpty()) {
            return List.of();
        }

        Path srcMainJava = projectRoot.resolve("src/main/java").normalize();
        if (!Files.isDirectory(srcMainJava)) {
            return List.of();
        }

        ArrayList<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(srcMainJava, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        javaFiles.sort(Comparator.comparing(path -> projectRoot.relativize(path).toString().replace('\\', '/')));

        ArrayList<MultiBlockPortImplFinding> findings = new ArrayList<>();
        for (Path file : javaFiles) {
            String relPath = projectRoot.relativize(file).toString().replace('\\', '/');
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String sanitized = BoundaryBypassScanner.stripJavaCommentsStringsAndChars(source);
            String packageName = parsePackageName(sanitized);
            Map<String, String> explicitImports = parseExplicitImports(sanitized);
            boolean markerPresentInFile = containsAllowMultiBlockMarker(source);
            boolean underSharedRoot = isUnderSharedGovernedRoot(relPath);

            Matcher matcher = IMPLEMENTS_DECL_PATTERN.matcher(sanitized);
            while (matcher.find()) {
                String className = matcher.group(1).trim();
                String interfacesRaw = matcher.group(2).trim();
                if (interfacesRaw.isEmpty()) {
                    continue;
                }
                String implClassFqcn = packageName.isBlank() ? className : packageName + "." + className;
                TreeMap<String, String> generatedPackages = new TreeMap<>();
                for (String token : splitInterfaces(interfacesRaw)) {
                    String resolvedInterface = resolveInterfaceFqcn(token, explicitImports, packageName);
                    if (resolvedInterface == null || !isGeneratedPortInterface(resolvedInterface)) {
                        continue;
                    }
                    String generatedPackage = packageOfFqcn(resolvedInterface);
                    if (generatedPackage == null) {
                        continue;
                    }
                    WiringManifest owner = ownerByGeneratedPackage.get(generatedPackage);
                    if (owner == null) {
                        // Missing owner in this manifest scope is non-fatal by contract.
                        continue;
                    }
                    generatedPackages.putIfAbsent(generatedPackage, generatedPackage);
                }
                if (generatedPackages.isEmpty()) {
                    continue;
                }

                if (!underSharedRoot && markerPresentInFile) {
                    findings.add(new MultiBlockPortImplFinding(
                        MARKER_MISUSED_OUTSIDE_SHARED_KIND,
                        implClassFqcn,
                        "",
                        relPath
                    ));
                    continue;
                }

                if (generatedPackages.size() <= 1) {
                    continue;
                }

                if (underSharedRoot && hasAllowMarkerWithinWindow(source, matcher.start())) {
                    continue;
                }

                findings.add(new MultiBlockPortImplFinding(
                    MULTI_BLOCK_PORT_IMPL_FORBIDDEN_KIND,
                    implClassFqcn,
                    String.join(",", generatedPackages.keySet()),
                    relPath
                ));
            }
        }

        findings.sort(
            Comparator.comparing(MultiBlockPortImplFinding::path)
                .thenComparing(MultiBlockPortImplFinding::kind)
                .thenComparing(MultiBlockPortImplFinding::implClassFqcn)
                .thenComparing(MultiBlockPortImplFinding::generatedPackageCsv)
        );
        return findings;
    }

    public static List<MultiBlockPortImplAllowedSignal> scanMultiBlockPortImplAllowedSignals(
        Path projectRoot,
        List<WiringManifest> manifests
    ) throws IOException, ManifestParseException {
        if (manifests == null || manifests.isEmpty()) {
            return List.of();
        }

        Map<String, WiringManifest> ownerByGeneratedPackage = buildOwnerByGeneratedPackage(manifests);
        if (ownerByGeneratedPackage.isEmpty()) {
            return List.of();
        }

        Path srcMainJava = projectRoot.resolve("src/main/java").normalize();
        if (!Files.isDirectory(srcMainJava)) {
            return List.of();
        }

        ArrayList<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(srcMainJava, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        javaFiles.sort(Comparator.comparing(path -> projectRoot.relativize(path).toString().replace('\\', '/')));

        ArrayList<MultiBlockPortImplAllowedSignal> signals = new ArrayList<>();
        for (Path file : javaFiles) {
            String relPath = projectRoot.relativize(file).toString().replace('\\', '/');
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String sanitized = BoundaryBypassScanner.stripJavaCommentsStringsAndChars(source);
            String packageName = parsePackageName(sanitized);
            Map<String, String> explicitImports = parseExplicitImports(sanitized);
            boolean underSharedRoot = isUnderSharedGovernedRoot(relPath);

            Matcher matcher = IMPLEMENTS_DECL_PATTERN.matcher(sanitized);
            while (matcher.find()) {
                String className = matcher.group(1).trim();
                String interfacesRaw = matcher.group(2).trim();
                if (interfacesRaw.isEmpty()) {
                    continue;
                }
                String implClassFqcn = packageName.isBlank() ? className : packageName + "." + className;
                TreeMap<String, String> generatedPackages = new TreeMap<>();
                for (String token : splitInterfaces(interfacesRaw)) {
                    String resolvedInterface = resolveInterfaceFqcn(token, explicitImports, packageName);
                    if (resolvedInterface == null || !isGeneratedPortInterface(resolvedInterface)) {
                        continue;
                    }
                    String generatedPackage = packageOfFqcn(resolvedInterface);
                    if (generatedPackage == null) {
                        continue;
                    }
                    generatedPackages.putIfAbsent(generatedPackage, generatedPackage);
                }
                if (generatedPackages.size() <= 1) {
                    continue;
                }
                if (!underSharedRoot) {
                    continue;
                }
                if (!hasAllowMarkerWithinWindow(source, matcher.start())) {
                    continue;
                }

                signals.add(new MultiBlockPortImplAllowedSignal(
                    implClassFqcn,
                    String.join(",", generatedPackages.keySet()),
                    relPath
                ));
            }
        }

        signals.sort(
            Comparator.comparing(MultiBlockPortImplAllowedSignal::path)
                .thenComparing(MultiBlockPortImplAllowedSignal::implClassFqcn)
                .thenComparing(MultiBlockPortImplAllowedSignal::generatedPackageCsv)
        );
        return signals;
    }

    private static Map<String, WiringManifest> buildOwnerByGeneratedPackage(List<WiringManifest> manifests)
        throws ManifestParseException {
        TreeMap<String, WiringManifest> ownerByGeneratedPackage = new TreeMap<>();
        for (WiringManifest manifest : manifests) {
            String entrypointPackage = packageOfFqcn(manifest.entrypointFqcn());
            if (entrypointPackage == null || entrypointPackage.isBlank()) {
                continue;
            }
            WiringManifest existing = ownerByGeneratedPackage.get(entrypointPackage);
            if (existing == null) {
                ownerByGeneratedPackage.put(entrypointPackage, manifest);
                continue;
            }
            if (!sameOwnerIdentity(existing, manifest)) {
                throw new ManifestParseException(AMBIGUOUS_PORT_OWNER_REASON_CODE);
            }
        }
        return ownerByGeneratedPackage;
    }

    private static boolean sameOwnerIdentity(WiringManifest left, WiringManifest right) {
        return safe(left.blockKey()).equals(safe(right.blockKey()))
            && safe(left.entrypointFqcn()).equals(safe(right.entrypointFqcn()));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String parsePackageName(String source) {
        Matcher matcher = PACKAGE_DECL_PATTERN.matcher(source);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).trim();
    }

    private static Map<String, String> parseExplicitImports(String source) {
        HashMap<String, String> importsBySimple = new HashMap<>();
        Matcher matcher = IMPORT_DECL_PATTERN.matcher(source);
        while (matcher.find()) {
            String importFqcn = matcher.group(1).trim();
            if (importFqcn.endsWith(".*")) {
                continue;
            }
            int idx = importFqcn.lastIndexOf('.');
            if (idx <= 0 || idx == importFqcn.length() - 1) {
                continue;
            }
            importsBySimple.putIfAbsent(importFqcn.substring(idx + 1), importFqcn);
        }
        return importsBySimple;
    }

    private static List<String> splitInterfaces(String payload) {
        ArrayList<String> interfaces = new ArrayList<>();
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
                String token = normalizeInterfaceToken(current.toString());
                if (!token.isBlank()) {
                    interfaces.add(token);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String token = normalizeInterfaceToken(current.toString());
        if (!token.isBlank()) {
            interfaces.add(token);
        }
        return interfaces;
    }

    private static String normalizeInterfaceToken(String raw) {
        String token = raw.trim();
        while (token.startsWith("@")) {
            int split = token.indexOf(' ');
            if (split < 0) {
                return "";
            }
            token = token.substring(split + 1).trim();
        }
        token = stripGenerics(token);
        int whitespace = token.indexOf(' ');
        if (whitespace >= 0) {
            token = token.substring(0, whitespace);
        }
        return token.trim();
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

    private static String resolveInterfaceFqcn(String token, Map<String, String> explicitImports, String packageName) {
        if (token.isBlank()) {
            return null;
        }
        if (token.contains(".")) {
            return token;
        }
        String imported = explicitImports.get(token);
        if (imported != null) {
            return imported;
        }
        if (packageName.isBlank()) {
            return null;
        }
        return packageName + "." + token;
    }

    private static String packageOfFqcn(String fqcn) {
        if (fqcn == null) {
            return null;
        }
        int idx = fqcn.lastIndexOf('.');
        if (idx <= 0) {
            return null;
        }
        return fqcn.substring(0, idx);
    }

    private static boolean isGeneratedPortInterface(String fqcn) {
        if (!fqcn.startsWith("com.bear.generated.")) {
            return false;
        }
        int idx = fqcn.lastIndexOf('.');
        if (idx < 0 || idx == fqcn.length() - 1) {
            return false;
        }
        String simple = fqcn.substring(idx + 1);
        return simple.endsWith("Port");
    }

    private static boolean containsAllowMultiBlockMarker(String source) {
        String normalized = normalizeNewlines(source);
        String[] lines = normalized.split("\n", -1);
        for (String line : lines) {
            if (ALLOW_MULTI_BLOCK_PORT_IMPL_MARKER.equals(line.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAllowMarkerWithinWindow(String source, int classDeclOffset) {
        String normalized = normalizeNewlines(source);
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0) {
            return false;
        }
        int classLineNumber = lineNumberAtOffset(source, classDeclOffset);
        int classLineIndex = Math.max(0, Math.min(classLineNumber - 1, lines.length - 1));

        int seenNonEmpty = 0;
        for (int lineIndex = classLineIndex - 1; lineIndex >= 0 && seenNonEmpty < MARKER_WINDOW_NON_EMPTY_LINES; lineIndex--) {
            String trimmed = lines[lineIndex].trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (ALLOW_MULTI_BLOCK_PORT_IMPL_MARKER.equals(trimmed)) {
                return true;
            }
            seenNonEmpty++;
        }
        return false;
    }

    private static int lineNumberAtOffset(String source, int offset) {
        int boundedOffset = Math.max(0, Math.min(offset, source.length()));
        int line = 1;
        for (int i = 0; i < boundedOffset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static boolean isUnderSharedGovernedRoot(String relPath) {
        return relPath.startsWith(SHARED_GOVERNED_ROOT_PREFIX);
    }

    private static boolean isUnderAnyGovernedRoot(String relPath, List<String> governedRoots) {
        if (governedRoots == null || governedRoots.isEmpty()) {
            return false;
        }
        for (String root : governedRoots) {
            String normalizedRoot = normalizeRepoPath(root);
            if (normalizedRoot == null || normalizedRoot.isBlank()) {
                continue;
            }
            if (relPath.equals(normalizedRoot) || relPath.startsWith(normalizedRoot + "/")) {
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
}


