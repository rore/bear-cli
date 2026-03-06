package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockIdentityResolverTest {
    @Test
    void canonicalizeIsDeterministic() {
        assertEquals("create-wallet", BlockIdentityResolver.canonicalize("CreateWallet"));
        assertEquals("create-wallet", BlockIdentityResolver.canonicalize("create_wallet"));
        assertEquals("transfer-funds", BlockIdentityResolver.canonicalize("transfer--funds"));
        assertEquals("a-b-c", BlockIdentityResolver.canonicalize("A B@@C"));
        assertEquals("block", BlockIdentityResolver.canonicalize(""));
    }

    @Test
    void singleCommandFallsBackWhenNoIndex(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("service");
        Path irFile = tempDir.resolve("bear-ir/create-wallet.bear.yaml");
        Files.createDirectories(projectRoot);

        BlockIdentityResolution resolved = BlockIdentityResolver.resolveSingleCommandIdentity(
            irFile,
            projectRoot,
            "CreateWallet"
        );

        assertFalse(resolved.indexResolved());
        assertEquals("create-wallet", resolved.blockKey());
    }

    @Test
    void singleCommandResolvesIndexMatch(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Path projectRoot = repoRoot.resolve("services/api");
        Path irFile = repoRoot.resolve("bear-ir/create-wallet.bear.yaml");
        Files.createDirectories(projectRoot);
        Files.createDirectories(irFile.getParent());
        Files.writeString(irFile, "version: v1\n", StandardCharsets.UTF_8);
        writeIndex(repoRoot, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: create-wallet\n"
            + "    ir: bear-ir/create-wallet.bear.yaml\n"
            + "    projectRoot: services/api\n");

        BlockIdentityResolution resolved = BlockIdentityResolver.resolveSingleCommandIdentity(
            irFile,
            projectRoot,
            "CreateWallet"
        );

        assertTrue(resolved.indexResolved());
        assertEquals("create-wallet", resolved.blockKey());
        assertEquals("create-wallet", resolved.indexName());
        assertNotNull(resolved.indexLocator());
    }

    @Test
    void singleCommandFallsBackWhenIndexHasNoTupleMatch(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Path projectRoot = repoRoot.resolve("services/api");
        Path irFile = repoRoot.resolve("bear-ir/create-wallet.bear.yaml");
        Files.createDirectories(projectRoot);
        Files.createDirectories(irFile.getParent());
        Files.writeString(irFile, "version: v1\n", StandardCharsets.UTF_8);
        writeIndex(repoRoot, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: create-wallet\n"
            + "    ir: bear-ir/create-wallet.bear.yaml\n"
            + "    projectRoot: services/other\n");

        BlockIdentityResolution resolved = BlockIdentityResolver.resolveSingleCommandIdentity(
            irFile,
            projectRoot,
            "CreateWallet"
        );

        assertFalse(resolved.indexResolved());
        assertEquals("create-wallet", resolved.blockKey());
    }

    @Test
    void singleCommandFailsOnAmbiguousTupleMatches(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Path projectRoot = repoRoot.resolve("services/api");
        Path irFile = repoRoot.resolve("bear-ir/create-wallet.bear.yaml");
        Files.createDirectories(projectRoot);
        Files.createDirectories(irFile.getParent());
        Files.writeString(irFile, "version: v1\n", StandardCharsets.UTF_8);
        writeIndex(repoRoot, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: create-wallet\n"
            + "    ir: bear-ir/create-wallet.bear.yaml\n"
            + "    projectRoot: services/api\n"
            + "  - name: wallet-create\n"
            + "    ir: bear-ir/create-wallet.bear.yaml\n"
            + "    projectRoot: services/api\n");

        BlockIdentityResolutionException error = assertThrows(
            BlockIdentityResolutionException.class,
            () -> BlockIdentityResolver.resolveSingleCommandIdentity(irFile, projectRoot, "CreateWallet")
        );
        assertTrue(error.line().contains("AMBIGUOUS_INDEX_ENTRIES"));
        assertEquals("bear.blocks.yaml", error.path());
    }

    @Test
    void singleCommandFailsOnCanonicalMismatch(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Path projectRoot = repoRoot.resolve("services/api");
        Path irFile = repoRoot.resolve("bear-ir/create-wallet.bear.yaml");
        Files.createDirectories(projectRoot);
        Files.createDirectories(irFile.getParent());
        Files.writeString(irFile, "version: v1\n", StandardCharsets.UTF_8);
        writeIndex(repoRoot, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: wallet-create\n"
            + "    ir: bear-ir/create-wallet.bear.yaml\n"
            + "    projectRoot: services/api\n");

        BlockIdentityResolutionException error = assertThrows(
            BlockIdentityResolutionException.class,
            () -> BlockIdentityResolver.resolveSingleCommandIdentity(irFile, projectRoot, "CreateWallet")
        );
        assertTrue(error.line().contains("canonical block identity mismatch"));
        assertEquals("block.name", error.path());
    }

    @Test
    void indexIdentityKeepsStableBlockKeyAcrossDisplayNameVariants() throws Exception {
        BlockIdentityResolution one = BlockIdentityResolver.resolveIndexIdentity(
            "create-wallet",
            "bear.blocks.yaml:name=create-wallet,ir=bear-ir/create-wallet.bear.yaml,projectRoot=.",
            "CreateWallet"
        );
        BlockIdentityResolution two = BlockIdentityResolver.resolveIndexIdentity(
            "create-wallet",
            "bear.blocks.yaml:name=create-wallet,ir=bear-ir/create-wallet.bear.yaml,projectRoot=.",
            "create_wallet"
        );
        assertEquals("create-wallet", one.blockKey());
        assertEquals("create-wallet", two.blockKey());
    }

    private static void writeIndex(Path repoRoot, String content) throws Exception {
        Files.createDirectories(repoRoot);
        Files.writeString(repoRoot.resolve("bear.blocks.yaml"), content, StandardCharsets.UTF_8);
    }
}
