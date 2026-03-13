package com.bear.kernel.target.python;

import com.bear.kernel.target.node.BoundaryDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PythonImportBoundaryResolverTest {

    @TempDir
    Path tempDir;

    private final PythonImportBoundaryResolver resolver = new PythonImportBoundaryResolver();

    @Test
    void sameBlockRelativeImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        BoundaryDecision decision = resolver.resolve(importingFile, ".utils", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void sameBlockDeepRelativeImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("handlers/login.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        BoundaryDecision decision = resolver.resolve(importingFile, ".validation", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void sharedImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot, sharedRoot);

        // Relative import going up and into _shared
        BoundaryDecision decision = resolver.resolve(importingFile, ".._shared.utils", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void generatedArtifactImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("impl/user_auth_impl.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Relative import to generated artifact
        // From src/blocks/user-auth/impl/ we need to go up 4 levels to reach project root,
        // then down into build/generated/bear/user-auth/
        // .... goes up 3 levels: impl -> user-auth -> blocks -> src
        // Then we need one more level to reach project root
        // So we use ..... (5 dots) to go up 4 levels from impl/
        BoundaryDecision decision = resolver.resolve(importingFile, ".....build.generated.bear.user_auth.user_auth_ports", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void siblingBlockImport_fail() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path siblingRoot = projectRoot.resolve("src/blocks/payment");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot, siblingRoot);

        // Relative import trying to reach sibling block
        BoundaryDecision decision = resolver.resolve(importingFile, "..payment.processor", true, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void escapingBlockRoot_fail() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Relative import escaping to nongoverned source
        BoundaryDecision decision = resolver.resolve(importingFile, "...utils", true, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void sharedImportsBlock_fail() {
        Path projectRoot = tempDir;
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = sharedRoot.resolve("utils.py");
        Set<Path> governedRoots = Set.of(sharedRoot, blockRoot);

        // _shared trying to import from a block
        BoundaryDecision decision = resolver.resolve(importingFile, "..user_auth.service", true, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("SHARED_IMPORTS_BLOCK", decision.failureReason());
    }

    @Test
    void stdlibImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of stdlib module
        BoundaryDecision decision = resolver.resolve(importingFile, "os", false, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void stdlibSubmoduleImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of stdlib submodule
        BoundaryDecision decision = resolver.resolve(importingFile, "os.path", false, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void thirdPartyImport_fail() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of third-party package
        BoundaryDecision decision = resolver.resolve(importingFile, "requests", false, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("THIRD_PARTY_IMPORT", decision.failureReason());
    }

    @Test
    void thirdPartySubmoduleImport_fail() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of third-party submodule
        BoundaryDecision decision = resolver.resolve(importingFile, "flask.app", false, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("THIRD_PARTY_IMPORT", decision.failureReason());
    }

    @Test
    void bearGeneratedAbsoluteImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("impl/user_auth_impl.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of BEAR-generated module
        BoundaryDecision decision = resolver.resolve(importingFile, "build.generated.bear.user_auth.user_auth_ports", false, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void parentRelativeImport_withinBlock_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("handlers/validators/email.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Go up two levels but stay within block
        BoundaryDecision decision = resolver.resolve(importingFile, "..utils", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void currentPackageImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Current package import (single dot)
        BoundaryDecision decision = resolver.resolve(importingFile, ".", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }
}
