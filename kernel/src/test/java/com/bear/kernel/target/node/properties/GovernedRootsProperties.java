package com.bear.kernel.target.node.properties;

import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.node.NodeImportContainmentScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for governed roots computation.
 * Feature: phase-b-node-target-scan-only
 *
 * Uses representative fixed inputs across multiple block keys to approximate
 * property-based coverage (no jqwik in build).
 */
class GovernedRootsProperties {

    // -----------------------------------------------------------------------
    // Property 7: any block key → src/blocks/<blockKey>/ in governed roots
    // Validates: Requirements — Governed Source Roots
    // -----------------------------------------------------------------------

    @Test
    void property7_blockKeyFilesAreScanned_userAuth(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("user-auth", projectRoot);
    }

    @Test
    void property7_blockKeyFilesAreScanned_payment(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("payment", projectRoot);
    }

    @Test
    void property7_blockKeyFilesAreScanned_orderManager(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("order-manager", projectRoot);
    }

    @Test
    void property7_blockKeyFilesAreScanned_inventoryService(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("inventory-service", projectRoot);
    }

    @Test
    void property7_blockKeyFilesAreScanned_singleWord(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("billing", projectRoot);
    }

    @Test
    void property7_multipleBlocks_allScanned(@TempDir Path projectRoot) throws IOException {
        // Multiple blocks should all be in governed roots
        Path blockA = Files.createDirectories(projectRoot.resolve("src/blocks/auth"));
        Path blockB = Files.createDirectories(projectRoot.resolve("src/blocks/payment"));
        Path blockC = Files.createDirectories(projectRoot.resolve("src/blocks/orders"));
        Files.writeString(blockA.resolve("index.ts"), "// clean\n");
        Files.writeString(blockB.resolve("service.ts"), "// clean\n");
        Files.writeString(blockC.resolve("handler.ts"), "// clean\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot,
            List.of(makeManifest("auth"), makeManifest("payment"), makeManifest("orders")));
        assertTrue(findings.isEmpty(), "all block roots should be governed — clean files produce no findings");
    }

    // -----------------------------------------------------------------------
    // Property 8: any path outside src/blocks/ → excluded from governed roots
    // Validates: Requirements — Governed Source Roots
    // -----------------------------------------------------------------------

    @Test
    void property8_filesOutsideBlocksNotScanned_srcOther(@TempDir Path projectRoot) throws IOException {
        assertOutsideFilesNotScanned(projectRoot, "src/other");
    }

    @Test
    void property8_filesOutsideBlocksNotScanned_srcLib(@TempDir Path projectRoot) throws IOException {
        assertOutsideFilesNotScanned(projectRoot, "src/lib");
    }

    @Test
    void property8_filesOutsideBlocksNotScanned_rootLevel(@TempDir Path projectRoot) throws IOException {
        assertOutsideFilesNotScanned(projectRoot, "scripts");
    }

    @Test
    void property8_filesOutsideBlocksNotScanned_srcUtils(@TempDir Path projectRoot) throws IOException {
        assertOutsideFilesNotScanned(projectRoot, "src/utils");
    }

    @Test
    void property8_filesOutsideBlocksNotScanned_buildDir(@TempDir Path projectRoot) throws IOException {
        assertOutsideFilesNotScanned(projectRoot, "build/output");
    }

    // -----------------------------------------------------------------------
    // Property 9: any *.test.ts → excluded from governed source files
    // Validates: Requirements — Governed Source Roots
    // -----------------------------------------------------------------------

    @Test
    void property9_testFilesExcluded_userAuth(@TempDir Path projectRoot) throws IOException {
        assertTestFilesExcluded("user-auth", "index.test.ts", projectRoot);
    }

    @Test
    void property9_testFilesExcluded_payment(@TempDir Path projectRoot) throws IOException {
        assertTestFilesExcluded("payment", "service.test.ts", projectRoot);
    }

    @Test
    void property9_testFilesExcluded_deepNested(@TempDir Path projectRoot) throws IOException {
        // Test file in a subdirectory of the block
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/auth/impl/__tests__"));
        Files.writeString(blockDir.resolve("handler.test.ts"), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("auth")));
        assertTrue(findings.isEmpty(), "nested *.test.ts files should be excluded");
    }

    @Test
    void property9_testFilesExcluded_sharedDir(@TempDir Path projectRoot) throws IOException {
        // Test file in _shared
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/auth"));
        Files.writeString(blockDir.resolve("index.ts"), "// clean\n");
        Path sharedDir = Files.createDirectories(projectRoot.resolve("src/blocks/_shared"));
        Files.writeString(sharedDir.resolve("utils.test.ts"), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("auth")));
        assertTrue(findings.isEmpty(), "*.test.ts in _shared should be excluded");
    }

    // -----------------------------------------------------------------------
    // Property 10: any non-.ts extension in src/blocks/ → excluded
    // Validates: Requirements — Governed Source Roots
    // -----------------------------------------------------------------------

    @Test
    void property10_nonTsExcluded_js(@TempDir Path projectRoot) throws IOException {
        assertNonTsExtensionExcluded("user-auth", "index.js", projectRoot);
    }

    @Test
    void property10_nonTsExcluded_jsx(@TempDir Path projectRoot) throws IOException {
        assertNonTsExtensionExcluded("user-auth", "App.jsx", projectRoot);
    }

    @Test
    void property10_nonTsExcluded_tsx(@TempDir Path projectRoot) throws IOException {
        assertNonTsExtensionExcluded("user-auth", "App.tsx", projectRoot);
    }

    @Test
    void property10_nonTsExcluded_mjs(@TempDir Path projectRoot) throws IOException {
        assertNonTsExtensionExcluded("payment", "index.mjs", projectRoot);
    }

    @Test
    void property10_nonTsExcluded_cjs(@TempDir Path projectRoot) throws IOException {
        assertNonTsExtensionExcluded("payment", "index.cjs", projectRoot);
    }

    @Test
    void property10_nonTsExcluded_cts(@TempDir Path projectRoot) throws IOException {
        assertNonTsExtensionExcluded("orders", "index.cts", projectRoot);
    }

    @Test
    void property10_nonTsExcluded_mts(@TempDir Path projectRoot) throws IOException {
        assertNonTsExtensionExcluded("orders", "index.mts", projectRoot);
    }

    @Test
    void property10_nonTsExcluded_json(@TempDir Path projectRoot) throws IOException {
        assertNonTsExtensionExcluded("auth", "config.json", projectRoot);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void assertBlockFilesScanned(String blockKey, Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("index.ts"), "// clean\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertTrue(findings.isEmpty(), "clean file in block root '" + blockKey + "' should produce no findings");
    }

    private void assertOutsideFilesNotScanned(Path projectRoot, String outsidePath) throws IOException {
        Path outsideDir = Files.createDirectories(projectRoot.resolve(outsidePath));
        // Write a file with a bare import that would fail if scanned
        Files.writeString(outsideDir.resolve("outside.ts"), "import lodash from 'lodash';\n");
        // Need at least one block for the manifest
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("index.ts"), "// clean\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "files in '" + outsidePath + "' should not be scanned");
    }

    private void assertTestFilesExcluded(String blockKey, String testFileName, Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));
        // Write a bare import that would fail if scanned
        Files.writeString(blockRoot.resolve(testFileName), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertTrue(findings.isEmpty(), "'" + testFileName + "' should be excluded from scanning");
    }

    private void assertNonTsExtensionExcluded(String blockKey, String fileName, Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));
        // Write a bare import that would fail if scanned
        Files.writeString(blockRoot.resolve(fileName), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertTrue(findings.isEmpty(), "'" + fileName + "' should not be scanned (non-.ts extension)");
    }

    private WiringManifest makeManifest(String blockKey) {
        return new WiringManifest(
            "1", blockKey, blockKey, blockKey + "Logic", blockKey + "Impl",
            "src/blocks/" + blockKey + "/impl/" + blockKey + "Impl.ts",
            "src/blocks/" + blockKey,
            List.of("src/blocks/" + blockKey),
            List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
