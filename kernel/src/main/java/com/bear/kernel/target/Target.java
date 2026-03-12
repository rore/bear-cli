package com.bear.kernel.target;

import com.bear.kernel.ir.BearIr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface Target {
    TargetId targetId();

    default GovernanceProfile defaultProfile() {
        return GovernanceProfile.of(targetId(), "default");
    }

    void compile(BearIr ir, Path projectRoot, String blockKey) throws IOException;

    void generateWiringOnly(BearIr ir, Path projectRoot, Path outputRoot, String blockKey) throws IOException;

    WiringManifest parseWiringManifest(Path path) throws IOException, ManifestParseException;

    void prepareCheckWorkspace(Path projectRoot, Path tempRoot) throws IOException;

    Set<String> ownedGeneratedPrefixes(String blockName);

    boolean considerContainmentSurfaces(BearIr ir, Path projectRoot);

    boolean sharedContainmentInScope(Path projectRoot);

    boolean blockDeclaresAllowedDeps(Path irFile);

    String containmentSkipInfoLine(String projectRootLabel, Path projectRoot, boolean considerContainmentSurfaces);

    TargetCheckIssue preflightContainmentIfRequired(Path projectRoot, boolean considerContainmentSurfaces) throws IOException;

    TargetCheckIssue verifyContainmentMarkersIfRequired(Path projectRoot, boolean considerContainmentSurfaces) throws IOException;

    List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot) throws IOException, PolicyValidationException;

    List<UndeclaredReachFinding> scanForbiddenReflectionDispatch(Path projectRoot, List<WiringManifest> wiringManifests)
        throws IOException;

    List<BoundaryBypassFinding> scanBoundaryBypass(
        Path projectRoot,
        List<WiringManifest> wiringManifests,
        Set<String> reflectionAllowlist
    ) throws IOException, ManifestParseException, PolicyValidationException;

    List<BoundaryBypassFinding> scanPortImplContainmentBypass(Path projectRoot, List<WiringManifest> wiringManifests)
        throws IOException, ManifestParseException;

    List<BoundaryBypassFinding> scanBlockPortBindings(
        Path projectRoot,
        List<WiringManifest> wiringManifests,
        Set<String> inboundTargetWrapperFqcns
    ) throws IOException;

    List<MultiBlockPortImplAllowedSignal> scanMultiBlockPortImplAllowedSignals(
        Path projectRoot,
        List<WiringManifest> wiringManifests
    ) throws IOException, ManifestParseException;

    ProjectTestResult runProjectVerification(Path projectRoot, String initScriptRelativePath) throws IOException, InterruptedException;
}

