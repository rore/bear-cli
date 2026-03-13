package com.bear.kernel.target.node.properties;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.node.BoundaryDecision;
import com.bear.kernel.target.node.NodeImportBoundaryResolver;
import com.bear.kernel.target.node.NodeImportContainmentScanner;
import com.bear.kernel.target.node.NodeImportSpecifierExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for import containment.
 * Feature: phase-b-node-target-scan-only
 */
class ImportContainmentProperties {

    private final NodeImportBoundaryResolver resolver = new NodeImportBoundaryResolver();
    private final NodeImportSpecifierExtractor extractor = new NodeImportSpecifierExtractor();

    // --- Property 11: same-block relative import passes ---

    @Test
    void sameBlockImportPasses_userAuth(@TempDir Path projectRoot) throws IOException {
        assertSameBlockImportPasses("user-auth", projectRoot);
    }

    @Test
    void sameBlockImportPasses_payment(@TempDir Path projectRoot) throws IOException {
        assertSameBlockImportPasses("payment", projectRoot);
    }

    // --- Property 12: _shared import passes ---

    @Test
    void sharedImportPasses_userAuth(@TempDir Path projectRoot) throws IOException {
        assertSharedImportPasses("user-auth", projectRoot);
    }

    @Test
    void sharedImportPasses_orderManager(@TempDir Path projectRoot) throws IOException {
        assertSharedImportPasses("order-manager", projectRoot);
    }

    // --- Property 14: escaping block root fails ---

    @Test
    void escapingImportFails_userAuth(@TempDir Path projectRoot) throws IOException {
        assertEscapingImportFails("user-auth", projectRoot);
    }

    @Test
    void escapingImportFails_payment(@TempDir Path projectRoot) throws IOException {
        assertEscapingImportFails("payment", projectRoot);
    }

    // --- Property 16: bare package import fails ---

    @Test
    void barePackageImportFails_userAuth(@TempDir Path projectRoot) throws IOException {
        assertBarePackageImportFails("user-auth", projectRoot);
    }

    @Test
    void barePackageImportFails_orderManager(@TempDir Path projectRoot) throws IOException {
        assertBarePackageImportFails("order-manager", projectRoot);
    }

    // --- Property 18: finding includes path and specifier ---

    @Test
    void findingIncludesPathAndSpecifier(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.createDirectories(projectRoot.resolve("src/blocks/other"));
        Files.writeString(blockRoot.resolve("index.ts"), "import { X } from '../other/index';\n");

        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(
            projectRoot, List.of(makeManifest("user-auth")));

        assertFalse(findings.isEmpty());
        BoundaryBypassFinding f = findings.get(0);
        assertNotNull(f.path());
        assertFalse(f.path().isBlank());
        assertNotNull(f.detail());
        assertFalse(f.detail().isBlank());
    }

    // --- Property 20: extractor finds all specifiers with valid locations ---

    @Test
    void extractorFindsNamedImportWithLocation() {
        String source = "import { A } from './a';";
        var specifiers = extractor.extractImports(source, source);
        assertEquals(1, specifiers.size());
        assertTrue(specifiers.get(0).lineNumber() >= 1);
        assertFalse(specifiers.get(0).specifier().isBlank());
    }

    @Test
    void extractorFindsAllSixPatterns() {
        String source = """
            import { A } from './a';
            import * as B from './b';
            import C from './c';
            import './d';
            export { E } from './e';
            export * from './f';
            """;
        var specifiers = extractor.extractImports(source, source);
        assertEquals(6, specifiers.size());
        for (var s : specifiers) {
            assertTrue(s.lineNumber() >= 1);
            assertFalse(s.specifier().isBlank());
        }
    }

    @Test
    void extractorReturnsEmptyForNoImports() {
        String source = "// no imports here\nconst x = 1;\n";
        var specifiers = extractor.extractImports(source, source);
        assertTrue(specifiers.isEmpty());
    }

    // --- Property 22: same block root passes (resolver) ---

    @Test
    void resolverSameBlockRootPasses(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Path importingFile = blockRoot.resolve("index.ts");
        BoundaryDecision decision = resolver.resolve(importingFile, "./utils", Set.of(blockRoot), projectRoot);
        assertTrue(decision.pass());
    }

    // --- Property 23: _shared passes (resolver) ---

    @Test
    void resolverSharedRootPasses(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.createDirectories(projectRoot.resolve("src/blocks/_shared"));
        Path importingFile = blockRoot.resolve("index.ts");
        BoundaryDecision decision = resolver.resolve(
            importingFile, "../_shared/util", Set.of(blockRoot), projectRoot);
        assertTrue(decision.pass());
    }

    // --- Property 24: generated dir passes (resolver) ---

    @Test
    void resolverGeneratedDirPasses(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.createDirectories(projectRoot.resolve("build/generated/bear/types/user-auth"));
        Path importingFile = blockRoot.resolve("index.ts");
        BoundaryDecision decision = resolver.resolve(
            importingFile, "../../../build/generated/bear/types/user-auth/Ports",
            Set.of(blockRoot), projectRoot);
        assertTrue(decision.pass());
    }

    // --- Property 25: sibling block fails (resolver) ---

    @Test
    void resolverSiblingBlockFails(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Path siblingRoot = Files.createDirectories(projectRoot.resolve("src/blocks/payment"));
        Path importingFile = blockRoot.resolve("index.ts");
        BoundaryDecision decision = resolver.resolve(
            importingFile, "../payment/index", Set.of(blockRoot, siblingRoot), projectRoot);
        assertTrue(decision.isFail());
    }

    // --- Property 28: FAIL uses structured reason ---

    @Test
    void resolverFailHasStructuredReason(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Path importingFile = blockRoot.resolve("index.ts");
        BoundaryDecision decision = resolver.resolve(importingFile, "lodash", Set.of(blockRoot), projectRoot);
        assertTrue(decision.isFail());
        assertNotNull(decision.failureReason());
        assertFalse(decision.failureReason().isBlank());
    }

    // --- helpers ---

    private void assertSameBlockImportPasses(String blockKey, Path projectRoot) throws IOException {
        Path servicesDir = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey + "/services"));
        Files.writeString(servicesDir.resolve("a.ts"), "import { B } from './b';\n");
        Files.createFile(servicesDir.resolve("b.ts"));

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertTrue(findings.isEmpty(), "same-block relative import should pass");
    }

    private void assertSharedImportPasses(String blockKey, Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));
        Files.createDirectories(projectRoot.resolve("src/blocks/_shared"));
        Files.writeString(blockRoot.resolve("index.ts"), "import { util } from '../_shared/util';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertTrue(findings.isEmpty(), "_shared import should pass");
    }

    private void assertEscapingImportFails(String blockKey, Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));
        Files.createDirectories(projectRoot.resolve("src/blocks/other-block"));
        Files.writeString(blockRoot.resolve("index.ts"), "import { X } from '../other-block/index';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertFalse(findings.isEmpty(), "escaping import should fail");
        assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
    }

    private void assertBarePackageImportFails(String blockKey, Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("index.ts"), "import _ from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertFalse(findings.isEmpty(), "bare package import should fail");
        assertEquals("BARE_PACKAGE_IMPORT", findings.get(0).rule());
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
