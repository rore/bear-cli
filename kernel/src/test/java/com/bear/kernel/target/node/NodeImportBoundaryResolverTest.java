package com.bear.kernel.target.node;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NodeImportBoundaryResolverTest {

    private final NodeImportBoundaryResolver resolver = new NodeImportBoundaryResolver();

    @Test
    void sameBlockImportPasses(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        BoundaryDecision decision = resolver.resolve(importingFile, "./utils", Set.of(blockRoot), tempDir);

        assertTrue(decision.pass());
    }

    @Test
    void sharedImportPasses(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path sharedRoot = Files.createDirectories(tempDir.resolve("src/blocks/_shared"));
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        // from services/ -> up 2 levels to blocks/, then into _shared/
        BoundaryDecision decision = resolver.resolve(
            importingFile, "../../_shared/shared-utils", Set.of(blockRoot, sharedRoot), tempDir);

        assertTrue(decision.pass());
    }

    @Test
    void generatedImportPasses(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Files.createDirectories(tempDir.resolve("build/generated/bear/types/user-auth"));
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        // from services/ -> up 4 levels to tempDir/, then into build/generated/bear/...
        BoundaryDecision decision = resolver.resolve(
            importingFile, "../../../../build/generated/bear/types/user-auth/generated",
            Set.of(blockRoot), tempDir);

        assertTrue(decision.pass());
    }

    @Test
    void siblingBlockImportFails(@TempDir Path tempDir) throws IOException {
        Path block1Root = createBlockStructure(tempDir, "user-auth");
        Path block2Root = createBlockStructure(tempDir, "payment");
        Path importingFile = block1Root.resolve("services/user-service.ts");

        // from services/ -> up 2 levels to blocks/, then into payment/
        BoundaryDecision decision = resolver.resolve(
            importingFile, "../../payment/services/payment-service",
            Set.of(block1Root, block2Root), tempDir);

        assertFalse(decision.pass());
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void barePackageImportFails(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        BoundaryDecision decision = resolver.resolve(importingFile, "lodash", Set.of(blockRoot), tempDir);

        assertFalse(decision.pass());
        assertEquals("BARE_PACKAGE_IMPORT", decision.failureReason());
    }

    @Test
    void aliasImportFails(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        BoundaryDecision decision = resolver.resolve(importingFile, "#utils", Set.of(blockRoot), tempDir);

        assertFalse(decision.pass());
        assertEquals("ALIAS_IMPORT", decision.failureReason());
    }

    @Test
    void urlImportFails(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        BoundaryDecision decision = resolver.resolve(
            importingFile, "https://example.com/utils", Set.of(blockRoot), tempDir);

        assertFalse(decision.pass());
        assertEquals("URL_IMPORT", decision.failureReason());
    }

    @Test
    void sharedImportsBlockFails(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path sharedRoot = Files.createDirectories(tempDir.resolve("src/blocks/_shared"));
        Path sharedFile = Files.createFile(sharedRoot.resolve("shared-utils.ts"));

        BoundaryDecision decision = resolver.resolve(
            sharedFile, "../user-auth/services/user-service",
            Set.of(blockRoot, sharedRoot), tempDir);

        assertFalse(decision.pass());
        assertEquals("SHARED_IMPORTS_BLOCK", decision.failureReason());
    }

    private Path createBlockStructure(Path tempDir, String blockKey) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.createDirectories(blockRoot.resolve("services"));
        return blockRoot;
    }
}
