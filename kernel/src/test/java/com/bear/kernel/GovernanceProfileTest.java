package com.bear.kernel;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.*;
import com.bear.kernel.target.jvm.JvmTarget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceProfileTest {

    @Test
    void constructionAndEquality() {
        GovernanceProfile a = GovernanceProfile.of(TargetId.JVM, "backend-service");
        GovernanceProfile b = GovernanceProfile.of(TargetId.JVM, "backend-service");
        assertEquals(a, b);
    }

    @Test
    void toStringFormat() {
        GovernanceProfile profile = GovernanceProfile.of(TargetId.JVM, "backend-service");
        assertEquals("jvm/backend-service", profile.toString());
    }

    @Test
    void jvmTargetDefaultProfile() {
        GovernanceProfile profile = new JvmTarget().defaultProfile();
        assertEquals(GovernanceProfile.of(TargetId.JVM, "backend-service"), profile);
    }

    @Test
    void differentProfilesNotEqual() {
        GovernanceProfile a = GovernanceProfile.of(TargetId.JVM, "backend-service");
        GovernanceProfile b = GovernanceProfile.of(TargetId.JVM, "default");
        assertNotEquals(a, b);
    }

    @Test
    void defaultMethodOnTarget() {
        Target stub = new TargetStub(TargetId.JVM);
        assertEquals(GovernanceProfile.of(TargetId.JVM, "default"), stub.defaultProfile());
    }

    /**
     * Minimal stub implementing Target interface for testing the default method.
     */
    private static class TargetStub implements Target {
        private final TargetId id;

        TargetStub(TargetId id) {
            this.id = id;
        }

        @Override public TargetId targetId() { return id; }
        @Override public void compile(BearIr ir, Path projectRoot, String blockKey) { throw new UnsupportedOperationException(); }
        @Override public void generateWiringOnly(BearIr ir, Path projectRoot, Path outputRoot, String blockKey) { throw new UnsupportedOperationException(); }
        @Override public WiringManifest parseWiringManifest(Path path) { throw new UnsupportedOperationException(); }
        @Override public void prepareCheckWorkspace(Path projectRoot, Path tempRoot) { throw new UnsupportedOperationException(); }
        @Override public Set<String> ownedGeneratedPrefixes(String blockName) { throw new UnsupportedOperationException(); }
        @Override public boolean considerContainmentSurfaces(BearIr ir, Path projectRoot) { throw new UnsupportedOperationException(); }
        @Override public boolean sharedContainmentInScope(Path projectRoot) { throw new UnsupportedOperationException(); }
        @Override public boolean blockDeclaresAllowedDeps(Path irFile) { throw new UnsupportedOperationException(); }
        @Override public String containmentSkipInfoLine(String projectRootLabel, Path projectRoot, boolean considerContainmentSurfaces) { throw new UnsupportedOperationException(); }
        @Override public TargetCheckIssue preflightContainmentIfRequired(Path projectRoot, boolean considerContainmentSurfaces) { throw new UnsupportedOperationException(); }
        @Override public TargetCheckIssue verifyContainmentMarkersIfRequired(Path projectRoot, boolean considerContainmentSurfaces) { throw new UnsupportedOperationException(); }
        @Override public List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot) { throw new UnsupportedOperationException(); }
        @Override public List<UndeclaredReachFinding> scanForbiddenReflectionDispatch(Path projectRoot, List<WiringManifest> wiringManifests) { throw new UnsupportedOperationException(); }
        @Override public List<BoundaryBypassFinding> scanBoundaryBypass(Path projectRoot, List<WiringManifest> wiringManifests, Set<String> reflectionAllowlist) { throw new UnsupportedOperationException(); }
        @Override public List<BoundaryBypassFinding> scanPortImplContainmentBypass(Path projectRoot, List<WiringManifest> wiringManifests) { throw new UnsupportedOperationException(); }
        @Override public List<BoundaryBypassFinding> scanBlockPortBindings(Path projectRoot, List<WiringManifest> wiringManifests, Set<String> inboundTargetWrapperFqcns) { throw new UnsupportedOperationException(); }
        @Override public List<MultiBlockPortImplAllowedSignal> scanMultiBlockPortImplAllowedSignals(Path projectRoot, List<WiringManifest> wiringManifests) { throw new UnsupportedOperationException(); }
        @Override public ProjectTestResult runProjectVerification(Path projectRoot, String initScriptRelativePath) { throw new UnsupportedOperationException(); }
    }
}
