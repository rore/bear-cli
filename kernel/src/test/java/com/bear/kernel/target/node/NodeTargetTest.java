package com.bear.kernel.target.node;

import com.bear.kernel.ir.BearIr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NodeTargetTest {

    @Test
    void targetIdReturnsNode() {
        NodeTarget target = new NodeTarget();
        assertEquals(com.bear.kernel.target.TargetId.NODE, target.targetId());
    }

    @Test
    void defaultProfileReturnsNodeBackendService() {
        NodeTarget target = new NodeTarget();
        var profile = target.defaultProfile();
        assertEquals(com.bear.kernel.target.TargetId.NODE, profile.target());
        assertEquals("backend-service", profile.profileId());
    }

    @Test
    void compileGeneratesTypeScriptArtifacts(@TempDir Path tempDir) throws Exception {
        NodeTarget target = new NodeTarget();
        BearIr ir = createTestIr();

        target.compile(ir, tempDir, "user-auth");

        // Generator uses blockKey as filename prefix: "user-auth" + "Ports.ts" etc.
        assertTrue(Files.exists(tempDir.resolve("build/generated/bear/types/user-auth/user-authPorts.ts")));
        assertTrue(Files.exists(tempDir.resolve("build/generated/bear/types/user-auth/user-authLogic.ts")));
        assertTrue(Files.exists(tempDir.resolve("build/generated/bear/types/user-auth/user-authWrapper.ts")));
        assertTrue(Files.exists(tempDir.resolve("build/generated/bear/wiring/user-auth.wiring.json")));
        assertTrue(Files.exists(tempDir.resolve("src/blocks/user-auth/impl/UserAuthImpl.ts")));
    }

    @Test
    void compileCreatesUserImplOnce(@TempDir Path tempDir) throws Exception {
        NodeTarget target = new NodeTarget();
        BearIr ir = createTestIr();

        // First compile
        target.compile(ir, tempDir, "user-auth");

        Path implFile = tempDir.resolve("src/blocks/user-auth/impl/UserAuthImpl.ts");
        String firstContent = Files.readString(implFile);

        // Second compile - user impl should not be overwritten
        target.compile(ir, tempDir, "user-auth");

        String secondContent = Files.readString(implFile);
        assertEquals(firstContent, secondContent);
    }

    @Test
    void stubMethodsThrowUnsupportedOperationException() {
        NodeTarget target = new NodeTarget();

        assertThrows(UnsupportedOperationException.class, () -> target.prepareCheckWorkspace(Path.of("."), Path.of(".")));
        assertThrows(UnsupportedOperationException.class, () -> target.containmentSkipInfoLine("test", Path.of("."), false));
        assertThrows(UnsupportedOperationException.class, () -> target.preflightContainmentIfRequired(Path.of("."), false));
        assertThrows(UnsupportedOperationException.class, () -> target.verifyContainmentMarkersIfRequired(Path.of("."), false));
        assertThrows(UnsupportedOperationException.class, () -> target.scanUndeclaredReach(Path.of(".")));
        assertThrows(UnsupportedOperationException.class, () -> target.scanForbiddenReflectionDispatch(Path.of("."), java.util.List.of()));
        assertThrows(UnsupportedOperationException.class, () -> target.scanPortImplContainmentBypass(Path.of("."), java.util.List.of()));
        assertThrows(UnsupportedOperationException.class, () -> target.scanBlockPortBindings(Path.of("."), java.util.List.of(), java.util.Set.of()));
        assertThrows(UnsupportedOperationException.class, () -> target.scanMultiBlockPortImplAllowedSignals(Path.of("."), java.util.List.of()));
        assertThrows(UnsupportedOperationException.class, () -> target.runProjectVerification(Path.of("."), "init.js"));
    }

    @Test
    void parseWiringManifestThrowsUnsupportedOperationException() throws Exception {
        NodeTarget target = new NodeTarget();
        assertThrows(UnsupportedOperationException.class, () -> target.parseWiringManifest(Path.of("test.wiring.json")));
    }

    private BearIr createTestIr() {
        return new BearIr("1", new BearIr.Block(
            "UserAuth",
            BearIr.BlockKind.LOGIC,
            java.util.List.of(),
            new BearIr.Effects(java.util.List.of()),
            null,
            null,
            java.util.List.of()
        ));
    }
}
