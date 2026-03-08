package com.bear.kernel.target.jvm;

import com.bear.kernel.target.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UndeclaredReachScanner {
    static final String CONTRACT_FILE_PATH = "app/src/main/resources/reach-surfaces.v1.txt";
    private static final String CONTRACT_RESOURCE = "/reach-surfaces.v1.txt";
    private static final String CONTRACT_HEADER = "REACH_SURFACES|v1";
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "(?m)^\\s*import\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+)\\s*;\\s*$"
    );
    private static final Pattern TYPE_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+$");
    private static final Pattern METHOD_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private UndeclaredReachScanner() {
    }

    static List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot) throws IOException, PolicyValidationException {
        List<ReachSurfaceSpec> surfaces = readContract();
        List<UndeclaredReachFinding> findings = new ArrayList<>();
        if (!Files.isDirectory(projectRoot)) {
            return findings;
        }
        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                String rel = projectRoot.relativize(file).toString().replace('\\', '/');
                if (!rel.endsWith(".java") || isUndeclaredReachExcluded(rel)) {
                    return FileVisitResult.CONTINUE;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                String sanitized = BoundaryBypassScanner.stripJavaCommentsStringsAndChars(source);
                String codeWithoutImports = IMPORT_PATTERN.matcher(sanitized).replaceAll("");
                Map<String, String> importedTypes = importedTypes(source);
                for (ReachSurfaceSpec surface : surfaces) {
                    if (matches(surface, codeWithoutImports, importedTypes)) {
                        findings.add(new UndeclaredReachFinding(rel, surface.label()));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        findings.sort(
            Comparator.comparing(UndeclaredReachFinding::path)
                .thenComparing(UndeclaredReachFinding::surface)
        );
        return findings;
    }

    private static boolean matches(ReachSurfaceSpec surface, String codeWithoutImports, Map<String, String> importedTypes) {
        boolean typeUsed = hasFqcnUsage(codeWithoutImports, surface.ownerFqcn())
            || hasImportedSimpleUsage(codeWithoutImports, importedTypes, surface.ownerFqcn());
        if (!typeUsed) {
            return false;
        }
        if (surface.kind() == ReachKind.TYPE) {
            return true;
        }
        return Pattern.compile("\\b" + Pattern.quote(surface.methodName()) + "\\s*\\(")
            .matcher(codeWithoutImports)
            .find();
    }

    private static boolean hasFqcnUsage(String source, String fqcn) {
        return Pattern.compile("\\b" + Pattern.quote(fqcn) + "\\b").matcher(source).find();
    }

    private static boolean hasImportedSimpleUsage(String source, Map<String, String> imports, String fqcn) {
        String simple = simpleName(fqcn);
        String imported = imports.get(simple);
        if (!fqcn.equals(imported)) {
            return false;
        }
        return Pattern.compile("\\b" + Pattern.quote(simple) + "\\b").matcher(source).find();
    }

    private static String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        if (idx < 0) {
            return fqcn;
        }
        return fqcn.substring(idx + 1);
    }

    private static Map<String, String> importedTypes(String source) {
        String code = BoundaryBypassScanner.stripJavaCommentsStringsAndChars(source);
        Matcher matcher = IMPORT_PATTERN.matcher(code);
        HashMap<String, String> map = new HashMap<>();
        HashMap<String, String> ambiguous = new HashMap<>();
        while (matcher.find()) {
            String fqcn = matcher.group(1);
            if (fqcn.endsWith(".*")) {
                continue;
            }
            String simple = simpleName(fqcn);
            String existing = map.get(simple);
            if (existing == null) {
                map.put(simple, fqcn);
                continue;
            }
            if (!existing.equals(fqcn)) {
                ambiguous.put(simple, "");
            }
        }
        for (String simple : ambiguous.keySet()) {
            map.remove(simple);
        }
        return map;
    }

    private static List<ReachSurfaceSpec> readContract() throws IOException, PolicyValidationException {
        InputStream stream = UndeclaredReachScanner.class.getResourceAsStream(CONTRACT_RESOURCE);
        if (stream == null) {
            throw new PolicyValidationException(CONTRACT_FILE_PATH, "missing reach surface contract resource");
        }
        ArrayList<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            throw new PolicyValidationException(CONTRACT_FILE_PATH, "missing header");
        }
        String header = stripBom(lines.get(0)).trim();
        if (!CONTRACT_HEADER.equals(header)) {
            throw new PolicyValidationException(CONTRACT_FILE_PATH, "invalid header: expected " + CONTRACT_HEADER);
        }

        ArrayList<ReachSurfaceSpec> specs = new ArrayList<>();
        LinkedHashSet<String> seenKeys = new LinkedHashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            String raw = lines.get(i).trim();
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;
            }
            String[] parts = raw.split("\\|", -1);
            if (parts.length < 3) {
                throw new PolicyValidationException(CONTRACT_FILE_PATH, "line " + (i + 1) + ": invalid contract row");
            }
            ReachSurfaceSpec spec = parseSurface(parts, i + 1);
            String key = spec.kind() + "|" + spec.ownerFqcn() + "|" + spec.methodName() + "|" + spec.label();
            if (!seenKeys.add(key)) {
                throw new PolicyValidationException(CONTRACT_FILE_PATH, "line " + (i + 1) + ": duplicate surface row");
            }
            specs.add(spec);
        }
        if (specs.isEmpty()) {
            throw new PolicyValidationException(CONTRACT_FILE_PATH, "no surfaces declared");
        }
        return specs;
    }

    private static ReachSurfaceSpec parseSurface(String[] parts, int lineNumber) throws PolicyValidationException {
        String kindToken = parts[0].trim();
        if ("TYPE".equals(kindToken)) {
            if (parts.length != 3) {
                throw new PolicyValidationException(CONTRACT_FILE_PATH, "line " + lineNumber + ": TYPE rows require 3 fields");
            }
            String fqcn = parts[1].trim();
            String label = parts[2].trim();
            if (!TYPE_NAME_PATTERN.matcher(fqcn).matches()) {
                throw new PolicyValidationException(CONTRACT_FILE_PATH, "line " + lineNumber + ": invalid FQCN");
            }
            if (label.isEmpty()) {
                throw new PolicyValidationException(CONTRACT_FILE_PATH, "line " + lineNumber + ": label is required");
            }
            return new ReachSurfaceSpec(ReachKind.TYPE, fqcn, "", label);
        }
        if ("METHOD".equals(kindToken)) {
            if (parts.length != 4) {
                throw new PolicyValidationException(CONTRACT_FILE_PATH, "line " + lineNumber + ": METHOD rows require 4 fields");
            }
            String owner = parts[1].trim();
            String method = parts[2].trim();
            String label = parts[3].trim();
            if (!TYPE_NAME_PATTERN.matcher(owner).matches()) {
                throw new PolicyValidationException(CONTRACT_FILE_PATH, "line " + lineNumber + ": invalid owner FQCN");
            }
            if (!METHOD_NAME_PATTERN.matcher(method).matches()) {
                throw new PolicyValidationException(CONTRACT_FILE_PATH, "line " + lineNumber + ": invalid method name");
            }
            if (label.isEmpty()) {
                throw new PolicyValidationException(CONTRACT_FILE_PATH, "line " + lineNumber + ": label is required");
            }
            return new ReachSurfaceSpec(ReachKind.METHOD, owner, method, label);
        }
        throw new PolicyValidationException(CONTRACT_FILE_PATH, "line " + lineNumber + ": unknown surface kind: " + kindToken);
    }

    private static String stripBom(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    static boolean isUndeclaredReachExcluded(String relPath) {
        return relPath.startsWith("build/")
            || relPath.startsWith(".gradle/")
            || relPath.startsWith("src/test/")
            || relPath.startsWith("build/generated/bear/");
    }

    private enum ReachKind {
        TYPE,
        METHOD
    }

    private record ReachSurfaceSpec(ReachKind kind, String ownerFqcn, String methodName, String label) {
    }
}


