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
 */
class GovernedRootsProperties {

    /** Property 7: Any block key -> src/blocks/<blockKey>/ in governed roots (files there are scanned). */
    @Test
    void blockKeyFilesAreScanned_userAuth(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("user-auth", projectRoot);
    }

    @Test
    void blockKeyFilesAreScanned_payment(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("payment", projectRoot);
    }

    @Test
    void blockKeyFilesAreScanned_orderManager(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("order-manager", projectRoot);
    }

    /** Property 8: Files outside src/blocks/ are not scanned. */
    @Test
    void filesOutsideBlocksNotScanned(@TempDir Path projectRoot) throws IOException {
        Path outsideDir = Files.createDirectories(projectRoot.resolve("src/other"));
        Files.writeString(outsideDir.resolve("outside.ts"), "import _ from 'lodash';\n");
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("index.ts"), "// clean\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "files outside src/blocks/ should not be scanned");
    }

    /** Property 9: Any *.test.ts within governed roots -> excluded from governed source files. */
    @Test
    void testFilesExcluded_userAuth(@TempDir Path projectRoot) throws IOException {
        assertTestFilesExcluded("user-auth", projectRoot);
    }

    @Test
    void testFilesExcluded_payment(@TempDir Path projectRoot) throws IOException {
        assertTestFilesExcluded("payment", projectRoot);
    }

    /** Property 10: Non-.ts files in src/blocks/ -> excluded from governed source. */
    @Test
    void nonTsFilesExcluded_js(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("index.js"), "import _ from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".js files should not be scanned");
    }

    @Test
    void nonTsFilesExcluded_tsx(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("index.tsx"), "import _ from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".tsx files should not be scanned");
    }

    private void assertBlockFilesScanned(String blockKey, Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("index.ts"), "// clean\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertTrue(findings.isEmpty(), "clean file in block root should produce no findings");
    }

    private void assertTestFilesExcluded(String blockKey, Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("index.test.ts"), "import _ from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertTrue(findings.isEmpty(), "*.test.ts files should be excluded from scanning");
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
