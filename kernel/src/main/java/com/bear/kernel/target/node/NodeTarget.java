package com.bear.kernel.target.node;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.target.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class NodeTarget implements Target {

    @Override
    public TargetId targetId() {
        return TargetId.NODE;
    }

    @Override
    public GovernanceProfile defaultProfile() {
        return GovernanceProfile.of(TargetId.NODE, "backend-service");
    }

    @Override
    public void compile(BearIr ir, Path projectRoot, String blockKey) throws IOException {
        // Phase B: Generate TypeScript artifacts
        TypeScriptArtifactGenerator generator = new TypeScriptArtifactGenerator();
        TypeScriptManifestGenerator manifestGenerator = new TypeScriptManifestGenerator();

        String blockKeyKebab = toKebabCase(blockKey);
        Path typesDir = projectRoot.resolve("build/generated/bear/types/" + blockKeyKebab);
        Path wiringDir = projectRoot.resolve("build/generated/bear/wiring");

        // Generate Ports.ts
        generator.generatePorts(ir, typesDir, blockKey);

        // Generate Logic.ts
        generator.generateLogic(ir, typesDir, blockKey);

        // Generate Wrapper.ts
        generator.generateWrapper(ir, typesDir, blockKey);

        // Generate wiring.json
        manifestGenerator.generateWiringManifest(ir, wiringDir, blockKey);

        // Create user impl skeleton if absent
        Path implDir = projectRoot.resolve("src/blocks/" + blockKeyKebab + "/impl");
        Path implFile = implDir.resolve(blockKey + "Impl.ts");
        if (!java.nio.file.Files.exists(implFile)) {
            generator.generateUserImplSkeleton(ir, implDir, blockKey);
        }
    }

    @Override
    public void generateWiringOnly(BearIr ir, Path projectRoot, Path outputRoot, String blockKey) throws IOException {
        // Generate only wiring manifest (for pr-check)
        TypeScriptManifestGenerator manifestGenerator = new TypeScriptManifestGenerator();
        String blockKeyKebab = toKebabCase(blockKey);
        Path wiringDir = outputRoot.resolve("wiring");
        manifestGenerator.generateWiringManifest(ir, wiringDir, blockKey);
    }

    @Override
    public WiringManifest parseWiringManifest(Path path) throws IOException, ManifestParseException {
        // Phase B: Parse wiring.json
        // For now, throw UnsupportedOperationException
        throw new UnsupportedOperationException("parseWiringManifest not implemented in Phase B");
    }

    @Override
    public void prepareCheckWorkspace(Path projectRoot, Path tempRoot) throws IOException {
        throw new UnsupportedOperationException("prepareCheckWorkspace not implemented in Phase B");
    }

    @Override
    public Set<String> ownedGeneratedPrefixes(String blockName) {
        String blockKey = toKebabCase(blockName);
        return Set.of(
            "build/generated/bear/types/" + blockKey + "/",
            "build/generated/bear/wiring/" + blockKey + ".wiring.json"
        );
    }

    @Override
    public boolean considerContainmentSurfaces(BearIr ir, Path projectRoot) {
        return false; // impl.allowedDeps unsupported in Phase B
    }

    @Override
    public boolean sharedContainmentInScope(Path projectRoot) {
        return false; // No shared policy in Phase B
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
        throw new UnsupportedOperationException("containmentSkipInfoLine not implemented in Phase B");
    }

    @Override
    public TargetCheckIssue preflightContainmentIfRequired(Path projectRoot, boolean considerContainmentSurfaces) throws IOException {
        throw new UnsupportedOperationException("preflightContainmentIfRequired not implemented in Phase B");
    }

    @Override
    public TargetCheckIssue verifyContainmentMarkersIfRequired(Path projectRoot, boolean considerContainmentSurfaces) throws IOException {
        throw new UnsupportedOperationException("verifyContainmentMarkersIfRequired not implemented in Phase B");
    }

    @Override
    public List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot) throws IOException, PolicyValidationException {
        throw new UnsupportedOperationException("scanUndeclaredReach not implemented in Phase B");
    }

    @Override
    public List<UndeclaredReachFinding> scanForbiddenReflectionDispatch(Path projectRoot, List<WiringManifest> wiringManifests)
            throws IOException {
        throw new UnsupportedOperationException("scanForbiddenReflectionDispatch not implemented in Phase B");
    }

    @Override
    public List<BoundaryBypassFinding> scanBoundaryBypass(
            Path projectRoot,
            List<WiringManifest> wiringManifests,
            Set<String> reflectionAllowlist
    ) throws IOException, ManifestParseException, PolicyValidationException {
        return NodeImportContainmentScanner.scan(projectRoot, wiringManifests);
    }

    @Override
    public List<BoundaryBypassFinding> scanPortImplContainmentBypass(Path projectRoot, List<WiringManifest> wiringManifests)
            throws IOException, ManifestParseException {
        throw new UnsupportedOperationException("scanPortImplContainmentBypass not implemented in Phase B");
    }

    @Override
    public List<BoundaryBypassFinding> scanBlockPortBindings(
            Path projectRoot,
            List<WiringManifest> wiringManifests,
            Set<String> inboundTargetWrapperFqcns
    ) throws IOException {
        throw new UnsupportedOperationException("scanBlockPortBindings not implemented in Phase B");
    }

    @Override
    public List<MultiBlockPortImplAllowedSignal> scanMultiBlockPortImplAllowedSignals(
            Path projectRoot,
            List<WiringManifest> wiringManifests
    ) throws IOException, ManifestParseException {
        throw new UnsupportedOperationException("scanMultiBlockPortImplAllowedSignals not implemented in Phase B");
    }

    @Override
    public ProjectTestResult runProjectVerification(Path projectRoot, String initScriptRelativePath) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("runProjectVerification not implemented in Phase B");
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
