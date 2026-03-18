package com.bear.kernel.target.python;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.target.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PythonTarget implements Target {

    @Override
    public TargetId targetId() {
        return TargetId.PYTHON;
    }

    @Override
    public GovernanceProfile defaultProfile() {
        return GovernanceProfile.of(TargetId.PYTHON, "service");
    }

    @Override
    public void compile(BearIr ir, Path projectRoot, String blockKey) throws IOException {
        // Phase P: Generate Python artifacts
        PythonArtifactGenerator generator = new PythonArtifactGenerator();
        PythonManifestGenerator manifestGenerator = new PythonManifestGenerator();

        Path generatedDir = projectRoot.resolve("build/generated/bear/" + blockKey);
        Path wiringDir = projectRoot.resolve("build/generated/bear/wiring");

        // Generate *_ports.py
        generator.generatePorts(ir, generatedDir, blockKey);

        // Generate *_logic.py
        generator.generateLogic(ir, generatedDir, blockKey);

        // Generate *_wrapper.py
        generator.generateWrapper(ir, generatedDir, blockKey);

        // Generate wiring.json
        manifestGenerator.generateWiringManifest(ir, wiringDir, blockKey);

        // Create user impl skeleton if absent
        Path implDir = projectRoot.resolve("src/blocks/" + blockKey + "/impl");
        generator.generateUserImplSkeleton(ir, implDir, blockKey);
    }

    @Override
    public void generateWiringOnly(BearIr ir, Path projectRoot, Path outputRoot, String blockKey) throws IOException {
        // Generate only wiring manifest (for pr-check)
        PythonManifestGenerator manifestGenerator = new PythonManifestGenerator();
        Path wiringDir = outputRoot.resolve("wiring");
        manifestGenerator.generateWiringManifest(ir, wiringDir, blockKey);
    }

    @Override
    public WiringManifest parseWiringManifest(Path path) throws IOException, ManifestParseException {
        return TargetManifestParsers.parseWiringManifest(path);
    }

    @Override
    public void prepareCheckWorkspace(Path projectRoot, Path tempRoot) throws IOException {
        Path sharedDir = projectRoot.resolve("src/blocks/_shared");
        if (Files.isDirectory(sharedDir)) {
            Files.createDirectories(tempRoot.resolve("src/blocks/_shared"));
        }
    }

    @Override
    public Set<String> ownedGeneratedPrefixes(String blockName) {
        String blockKey = toKebabCase(blockName);
        return Set.of(
            "build/generated/bear/" + blockKey + "/",
            "build/generated/bear/wiring/" + blockKey + ".wiring.json"
        );
    }

    @Override
    public boolean considerContainmentSurfaces(BearIr ir, Path projectRoot) {
        return false; // impl.allowedDeps unsupported in Phase P
    }

    @Override
    public boolean sharedContainmentInScope(Path projectRoot) {
        return false; // No shared policy in Phase P
    }

    @Override
    public boolean blockDeclaresAllowedDeps(Path irFile) {
        try {
            BearIr ir = parseIr(irFile);
            return ir.block().impl() != null && ir.block().impl().allowedDeps() != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String containmentSkipInfoLine(String projectRootLabel, Path projectRoot, boolean considerContainmentSurfaces) {
        return null; // JVM-style containment markers not applicable to Python
    }

    @Override
    public TargetCheckIssue preflightContainmentIfRequired(Path projectRoot, boolean considerContainmentSurfaces) throws IOException {
        return null; // JVM-style containment markers not applicable to Python
    }

    @Override
    public TargetCheckIssue verifyContainmentMarkersIfRequired(Path projectRoot, boolean considerContainmentSurfaces) throws IOException {
        return null; // JVM-style containment markers not applicable to Python
    }

    @Override
    public List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot) throws IOException, PolicyValidationException {
        // Discover wiring manifests from project root
        List<WiringManifest> wiringManifests = discoverWiringManifests(projectRoot);
        return PythonUndeclaredReachScanner.scan(projectRoot, wiringManifests);
    }

    @Override
    public List<UndeclaredReachFinding> scanForbiddenReflectionDispatch(Path projectRoot, List<WiringManifest> wiringManifests)
            throws IOException {
        // Combine dynamic execution scanner and dynamic import enforcer findings
        List<UndeclaredReachFinding> execFindings = PythonDynamicExecutionScanner.scan(projectRoot, wiringManifests);
        List<UndeclaredReachFinding> importFindings = PythonDynamicImportEnforcer.scan(projectRoot, wiringManifests);
        
        List<UndeclaredReachFinding> combined = new ArrayList<>();
        combined.addAll(execFindings);
        combined.addAll(importFindings);
        
        // Sort by path then surface
        combined.sort(java.util.Comparator.comparing(UndeclaredReachFinding::path)
            .thenComparing(UndeclaredReachFinding::surface));
        
        return combined;
    }

    /**
     * Discovers wiring manifests from the project root by scanning build/generated/bear/wiring/.
     */
    private List<WiringManifest> discoverWiringManifests(Path projectRoot) throws IOException {
        Path wiringDir = projectRoot.resolve("build/generated/bear/wiring");
        List<WiringManifest> manifests = new ArrayList<>();
        
        if (!Files.isDirectory(wiringDir)) {
            return manifests;
        }
        
        try (var stream = Files.list(wiringDir)) {
            stream.filter(p -> p.toString().endsWith(".wiring.json"))
                  .forEach(p -> {
                      try {
                          manifests.add(parseWiringManifest(p));
                      } catch (IOException | ManifestParseException e) {
                          // Skip invalid manifests - they'll be caught by drift gate
                      }
                  });
        }
        
        return manifests;
    }

    @Override
    public List<BoundaryBypassFinding> scanBoundaryBypass(
            Path projectRoot,
            List<WiringManifest> wiringManifests,
            Set<String> reflectionAllowlist
    ) throws IOException, ManifestParseException, PolicyValidationException {
        return PythonImportContainmentScanner.scan(projectRoot, wiringManifests);
    }

    @Override
    public List<BoundaryBypassFinding> scanPortImplContainmentBypass(Path projectRoot, List<WiringManifest> wiringManifests)
            throws IOException, ManifestParseException {
        return List.of(); // JVM-specific port binding checks not applicable to Python
    }

    @Override
    public List<BoundaryBypassFinding> scanBlockPortBindings(
            Path projectRoot,
            List<WiringManifest> wiringManifests,
            Set<String> inboundTargetWrapperFqcns
    ) throws IOException {
        return List.of(); // JVM-specific port binding checks not applicable to Python
    }

    @Override
    public List<MultiBlockPortImplAllowedSignal> scanMultiBlockPortImplAllowedSignals(
            Path projectRoot,
            List<WiringManifest> wiringManifests
    ) throws IOException, ManifestParseException {
        return List.of(); // JVM-specific port binding checks not applicable to Python
    }

    @Override
    public ProjectTestResult runProjectVerification(Path projectRoot, String initScriptRelativePath) throws IOException, InterruptedException {
        // initScriptRelativePath is JVM-specific (Gradle init script); Python ignores it
        return PythonProjectVerificationRunner.run(projectRoot);
    }

    // Helper methods

    private BearIr parseIr(Path irFile) throws IOException, BearIrValidationException {
        return new BearIrParser().parse(irFile);
    }

    private String toKebabCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
