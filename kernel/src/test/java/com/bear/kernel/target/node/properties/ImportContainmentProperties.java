package com.bear.kernel.target.node.properties;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.node.BoundaryDecision;
import com.bear.kernel.target.node.NodeDynamicImportDetector;
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
    private final NodeDynamicImportDetector dynamicDetector = new NodeDynamicImportDetector();

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

    // --- Property 13: BEAR-generated import passes ---
    // Feature: phase-b-node-target-scan-only, Property 13: relative import resolving to build/generated/bear/ → no findings

    @Test
    void bearGeneratedImportPasses(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.createDirectories(projectRoot.resolve("build/generated/bear/types/user-auth"));
        // From src/blocks/user-auth/ need ../../../ to reach project root
        Files.writeString(blockRoot.resolve("index.ts"),
            "import { Ports } from '../../../build/generated/bear/types/user-auth/Ports';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "BEAR-generated import should pass");
    }

    @Test
    void bearGeneratedImportPasses_payment(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/payment"));
        Files.createDirectories(projectRoot.resolve("build/generated/bear/types/payment"));
        // From src/blocks/payment/ need ../../../ to reach project root
        Files.writeString(blockRoot.resolve("index.ts"),
            "import { Ports } from '../../../build/generated/bear/types/payment/Ports';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("payment")));
        assertTrue(findings.isEmpty(), "BEAR-generated import should pass");
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

    // --- Property 15: sibling block import fails ---
    // Feature: phase-b-node-target-scan-only, Property 15: import resolving to sibling block → finding, exit 7, CODE=BOUNDARY_BYPASS

    @Test
    void siblingBlockImportFails_userAuth(@TempDir Path projectRoot) throws IOException {
        assertSiblingBlockImportFails("user-auth", "payment", projectRoot);
    }

    @Test
    void siblingBlockImportFails_payment(@TempDir Path projectRoot) throws IOException {
        assertSiblingBlockImportFails("payment", "user-auth", projectRoot);
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

    // --- Property 17: # alias import fails ---
    // Feature: phase-b-node-target-scan-only, Property 17: # alias import from governed root → finding, exit 7, CODE=BOUNDARY_BYPASS

    @Test
    void aliasImportFails_userAuth(@TempDir Path projectRoot) throws IOException {
        assertAliasImportFails("user-auth", projectRoot);
    }

    @Test
    void aliasImportFails_payment(@TempDir Path projectRoot) throws IOException {
        assertAliasImportFails("payment", projectRoot);
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

    // --- Property 19: _shared importing block fails ---
    // Feature: phase-b-node-target-scan-only, Property 19: _shared file importing a block root → finding, exit 7, CODE=BOUNDARY_BYPASS

    @Test
    void sharedImportingBlockFails(@TempDir Path projectRoot) throws IOException {
        Path sharedRoot = Files.createDirectories(projectRoot.resolve("src/blocks/_shared"));
        Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(sharedRoot.resolve("util.ts"),
            "import { X } from '../user-auth/index';\n");

        // _shared is a governed root, so we need a manifest that causes it to be scanned
        // The scanner adds _shared automatically if it exists
        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertFalse(findings.isEmpty(), "_shared importing block should fail");
        assertTrue(findings.stream().anyMatch(f ->
            f.rule().equals("SHARED_IMPORTS_BLOCK") || f.rule().equals("BOUNDARY_BYPASS")));
    }

    @Test
    void sharedImportingBlockFails_payment(@TempDir Path projectRoot) throws IOException {
        Path sharedRoot = Files.createDirectories(projectRoot.resolve("src/blocks/_shared"));
        Files.createDirectories(projectRoot.resolve("src/blocks/payment"));
        Files.writeString(sharedRoot.resolve("helper.ts"),
            "import { Pay } from '../payment/service';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("payment")));
        assertFalse(findings.isEmpty(), "_shared importing block should fail");
        assertTrue(findings.stream().anyMatch(f ->
            f.rule().equals("SHARED_IMPORTS_BLOCK") || f.rule().equals("BOUNDARY_BYPASS")));
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

    // --- Property 21: dynamic import detection ---
    // Feature: phase-b-node-target-scan-only, Property 21: any TypeScript source with import() → all dynamic imports identified

    @Test
    void dynamicImportDetected() {
        String source = "const mod = import(\"./my-module\");";
        var imports = dynamicDetector.detectDynamicImports(source);
        assertEquals(1, imports.size());
        assertEquals("./my-module", imports.get(0).specifier());
        assertTrue(imports.get(0).lineNumber() >= 1);
    }

    @Test
    void dynamicImportDetectedMultiple() {
        String source = """
            const a = import("./a");
            const b = import("./b");
            const c = import('./c');
            """;
        var imports = dynamicDetector.detectDynamicImports(source);
        assertEquals(3, imports.size());
        assertEquals("./a", imports.get(0).specifier());
        assertEquals("./b", imports.get(1).specifier());
        assertEquals("./c", imports.get(2).specifier());
    }

    @Test
    void dynamicImportNotConfusedWithStatic() {
        String source = """
            import { x } from './static';
            const dynamic = import("./dynamic");
            """;
        var imports = dynamicDetector.detectDynamicImports(source);
        assertEquals(1, imports.size());
        assertEquals("./dynamic", imports.get(0).specifier());
    }

    @Test
    void dynamicImportEmptySourceReturnsEmpty() {
        var imports = dynamicDetector.detectDynamicImports("");
        assertTrue(imports.isEmpty());
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

    // --- Property 26: nongoverned source path fails (resolver) ---
    // Feature: phase-b-node-target-scan-only, Property 26: resolved path in nongoverned source → FAIL

    @Test
    void resolverNongovernedSourceFails(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Path importingFile = blockRoot.resolve("index.ts");
        // Resolve to a path outside any governed root (e.g., src/lib/utils)
        BoundaryDecision decision = resolver.resolve(
            importingFile, "../../lib/utils", Set.of(blockRoot), projectRoot);
        assertTrue(decision.isFail());
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    // --- Property 27: escaped block root path fails (resolver) ---
    // Feature: phase-b-node-target-scan-only, Property 27: resolved path escaping block root → FAIL

    @Test
    void resolverEscapedBlockRootFails(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Path importingFile = blockRoot.resolve("deep/nested/file.ts");
        // Resolve to a path that escapes the block root entirely (goes above src/blocks)
        BoundaryDecision decision = resolver.resolve(
            importingFile, "../../../../outside/module", Set.of(blockRoot), projectRoot);
        assertTrue(decision.isFail());
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
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

    private void assertSiblingBlockImportFails(String blockKey, String siblingKey, Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));
        Files.createDirectories(projectRoot.resolve("src/blocks/" + siblingKey));
        Files.writeString(blockRoot.resolve("index.ts"),
            "import { X } from '../" + siblingKey + "/index';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertFalse(findings.isEmpty(), "sibling block import should fail");
        assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
    }

    private void assertAliasImportFails(String blockKey, Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("index.ts"), "import { X } from '#utils/helper';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertFalse(findings.isEmpty(), "# alias import should fail");
        assertEquals("ALIAS_IMPORT", findings.get(0).rule());
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
