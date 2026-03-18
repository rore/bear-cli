package com.bear.kernel.target.node;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.TargetCheckIssue;
import com.bear.kernel.target.TargetCheckIssueKind;
import com.bear.kernel.target.WiringManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

        // Generator uses PascalCase BlockName as filename prefix: "UserAuth" + "Ports.ts" etc.
        assertTrue(Files.exists(tempDir.resolve("build/generated/bear/types/user-auth/UserAuthPorts.ts")));
        assertTrue(Files.exists(tempDir.resolve("build/generated/bear/types/user-auth/UserAuthLogic.ts")));
        assertTrue(Files.exists(tempDir.resolve("build/generated/bear/types/user-auth/UserAuthWrapper.ts")));
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

    // --- Governed Roots: single block ---

    @Test
    void governedRoots_singleBlock_scansOnlyBlockFiles(@TempDir Path projectRoot) throws IOException {
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockDir.resolve("index.ts"), "// clean\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "clean .ts file in single block should produce no findings");
    }

    // --- Governed Roots: multi-block ---

    @Test
    void governedRoots_multiBlock_scansAllBlockFiles(@TempDir Path projectRoot) throws IOException {
        Path blockA = Files.createDirectories(projectRoot.resolve("src/blocks/auth"));
        Path blockB = Files.createDirectories(projectRoot.resolve("src/blocks/payment"));
        Files.writeString(blockA.resolve("index.ts"), "// clean\n");
        Files.writeString(blockB.resolve("service.ts"), "// clean\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot,
            List.of(makeManifest("auth"), makeManifest("payment")));
        assertTrue(findings.isEmpty(), "clean .ts files in multi-block should produce no findings");
    }

    // --- Governed Roots: with _shared ---

    @Test
    void governedRoots_withShared_scansSharedFiles(@TempDir Path projectRoot) throws IOException {
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Path sharedDir = Files.createDirectories(projectRoot.resolve("src/blocks/_shared"));
        Files.writeString(blockDir.resolve("index.ts"), "// clean\n");
        Files.writeString(sharedDir.resolve("utils.ts"), "// clean\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "clean files in block + _shared should produce no findings");
    }

    // --- Governed Roots: without _shared ---

    @Test
    void governedRoots_withoutShared_noError(@TempDir Path projectRoot) throws IOException {
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockDir.resolve("index.ts"), "// clean\n");
        // No _shared directory — should not cause an error

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "absent _shared should not cause errors");
    }

    // --- Governed Roots: test file exclusion ---

    @Test
    void governedRoots_testFilesExcluded(@TempDir Path projectRoot) throws IOException {
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        // .test.ts file with a bare import that would fail if scanned
        Files.writeString(blockDir.resolve("index.test.ts"), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "*.test.ts files should be excluded from scanning");
    }

    // --- Governed Roots: extension filtering ---

    @Test
    void governedRoots_jsFilesExcluded(@TempDir Path projectRoot) throws IOException {
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockDir.resolve("index.js"), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".js files should be excluded from scanning");
    }

    @Test
    void governedRoots_jsxFilesExcluded(@TempDir Path projectRoot) throws IOException {
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockDir.resolve("App.jsx"), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".jsx files should be excluded from scanning");
    }

    @Test
    void governedRoots_tsxFilesExcluded(@TempDir Path projectRoot) throws IOException {
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockDir.resolve("App.tsx"), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".tsx files should be excluded from scanning");
    }

    @Test
    void governedRoots_mjsFilesExcluded(@TempDir Path projectRoot) throws IOException {
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockDir.resolve("index.mjs"), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".mjs files should be excluded from scanning");
    }

    @Test
    void governedRoots_cjsFilesExcluded(@TempDir Path projectRoot) throws IOException {
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockDir.resolve("index.cjs"), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".cjs files should be excluded from scanning");
    }

    @Test
    void governedRoots_ctsFilesExcluded(@TempDir Path projectRoot) throws IOException {
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockDir.resolve("index.cts"), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".cts files should be excluded from scanning");
    }

    @Test
    void governedRoots_mtsFilesExcluded(@TempDir Path projectRoot) throws IOException {
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockDir.resolve("index.mts"), "import lodash from 'lodash';\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".mts files should be excluded from scanning");
    }

    // --- Governed Roots: files outside src/blocks/ excluded ---

    @Test
    void governedRoots_filesOutsideBlocksExcluded(@TempDir Path projectRoot) throws IOException {
        Path outsideDir = Files.createDirectories(projectRoot.resolve("src/other"));
        Files.writeString(outsideDir.resolve("outside.ts"), "import lodash from 'lodash';\n");
        Path blockDir = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockDir.resolve("index.ts"), "// clean\n");

        var findings = NodeImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "files outside src/blocks/ should not be scanned");
    }

    // --- Drift Gate: clean state → no findings ---

    @Test
    void driftGate_cleanState_noFindings(@TempDir Path projectRoot) throws IOException {
        NodeTarget target = new NodeTarget();
        BearIr ir = createTestIrWithOp("UserAuth", "login");

        target.compile(ir, projectRoot, "user-auth");

        List<TargetCheckIssue> findings = target.checkDrift(ir, projectRoot, "user-auth");
        assertTrue(findings.isEmpty(), "freshly compiled project should have no drift findings");
    }

    // --- Drift Gate: modified generated file → DRIFT_DETECTED ---

    @Test
    void driftGate_modifiedGeneratedFile_driftDetected(@TempDir Path projectRoot) throws IOException {
        NodeTarget target = new NodeTarget();
        BearIr ir = createTestIrWithOp("UserAuth", "login");

        target.compile(ir, projectRoot, "user-auth");

        // Modify a generated file
        Path portsFile = projectRoot.resolve("build/generated/bear/types/user-auth/UserAuthPorts.ts");
        Files.writeString(portsFile, "// tampered content\n");

        List<TargetCheckIssue> findings = target.checkDrift(ir, projectRoot, "user-auth");
        assertFalse(findings.isEmpty(), "modified generated file should produce drift findings");
        assertTrue(findings.stream().anyMatch(f -> f.kind() == TargetCheckIssueKind.DRIFT_DETECTED),
            "should contain DRIFT_DETECTED finding");
    }

    // --- Drift Gate: modified wiring manifest → DRIFT_DETECTED ---

    @Test
    void driftGate_modifiedWiringManifest_driftDetected(@TempDir Path projectRoot) throws IOException {
        NodeTarget target = new NodeTarget();
        BearIr ir = createTestIrWithOp("UserAuth", "login");

        target.compile(ir, projectRoot, "user-auth");

        // Modify the wiring manifest
        Path wiringFile = projectRoot.resolve("build/generated/bear/wiring/user-auth.wiring.json");
        Files.writeString(wiringFile, "{ \"modified\": true }");

        List<TargetCheckIssue> findings = target.checkDrift(ir, projectRoot, "user-auth");
        assertFalse(findings.isEmpty(), "modified wiring manifest should produce drift findings");
        assertTrue(findings.stream().anyMatch(f -> f.kind() == TargetCheckIssueKind.DRIFT_DETECTED),
            "should contain DRIFT_DETECTED finding");
    }

    // --- Drift Gate: missing generated file → DRIFT_MISSING_BASELINE ---

    @Test
    void driftGate_missingGeneratedFile_driftMissingBaseline(@TempDir Path projectRoot) throws IOException {
        NodeTarget target = new NodeTarget();
        BearIr ir = createTestIrWithOp("UserAuth", "login");

        target.compile(ir, projectRoot, "user-auth");

        // Delete a generated file
        Files.delete(projectRoot.resolve("build/generated/bear/types/user-auth/UserAuthPorts.ts"));

        List<TargetCheckIssue> findings = target.checkDrift(ir, projectRoot, "user-auth");
        assertFalse(findings.isEmpty(), "missing generated file should produce drift findings");
        assertTrue(findings.stream().anyMatch(f -> f.kind() == TargetCheckIssueKind.DRIFT_MISSING_BASELINE),
            "should contain DRIFT_MISSING_BASELINE finding");
    }

    // --- Drift Gate: missing wiring manifest → DRIFT_MISSING_BASELINE ---

    @Test
    void driftGate_missingWiringManifest_driftMissingBaseline(@TempDir Path projectRoot) throws IOException {
        NodeTarget target = new NodeTarget();
        BearIr ir = createTestIrWithOp("UserAuth", "login");

        target.compile(ir, projectRoot, "user-auth");

        // Delete the wiring manifest
        Files.delete(projectRoot.resolve("build/generated/bear/wiring/user-auth.wiring.json"));

        List<TargetCheckIssue> findings = target.checkDrift(ir, projectRoot, "user-auth");
        assertFalse(findings.isEmpty(), "missing wiring manifest should produce drift findings");
        assertTrue(findings.stream().anyMatch(f -> f.kind() == TargetCheckIssueKind.DRIFT_MISSING_BASELINE),
            "should contain DRIFT_MISSING_BASELINE finding");
    }

    // --- Drift Gate: user impl modified → no findings ---

    @Test
    void driftGate_userImplModified_noFindings(@TempDir Path projectRoot) throws IOException {
        NodeTarget target = new NodeTarget();
        BearIr ir = createTestIrWithOp("UserAuth", "login");

        target.compile(ir, projectRoot, "user-auth");

        // Modify the user-owned impl file
        Path implFile = projectRoot.resolve("src/blocks/user-auth/impl/UserAuthImpl.ts");
        Files.writeString(implFile, "// user modified implementation\nexport class UserAuthImpl {}\n");

        List<TargetCheckIssue> findings = target.checkDrift(ir, projectRoot, "user-auth");
        assertTrue(findings.isEmpty(), "user impl modification should not produce drift findings");
    }

    // --- AllowedDeps Guard: blockDeclaresAllowedDeps ---

    @Test
    void blockDeclaresAllowedDeps_returnsFalseForNonExistentFile() {
        NodeTarget target = new NodeTarget();
        assertFalse(target.blockDeclaresAllowedDeps(Path.of("nonexistent-ir.bear.yaml")));
    }

    @Test
    void blockDeclaresAllowedDeps_returnsTrueForIrWithAllowedDeps(@TempDir Path tempDir) throws IOException {
        NodeTarget target = new NodeTarget();
        Path irFile = tempDir.resolve("test.bear.yaml");
        Files.writeString(irFile, ""
            + "version: v1\n"
            + "block:\n"
            + "  name: PaymentService\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: charge\n"
            + "      contract:\n"
            + "        inputs:\n"
            + "          - name: amount\n"
            + "            type: int\n"
            + "        outputs:\n"
            + "          - name: receipt\n"
            + "            type: string\n"
            + "      uses:\n"
            + "        allow: []\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  impl:\n"
            + "    allowedDeps:\n"
            + "      - maven: com.example:lib\n"
            + "        version: \"1.0\"\n");
        assertTrue(target.blockDeclaresAllowedDeps(irFile),
            "blockDeclaresAllowedDeps should return true when IR has impl.allowedDeps");
    }

    @Test
    void blockDeclaresAllowedDeps_returnsFalseForIrWithoutAllowedDeps(@TempDir Path tempDir) throws IOException {
        NodeTarget target = new NodeTarget();
        Path irFile = tempDir.resolve("test.bear.yaml");
        Files.writeString(irFile, ""
            + "version: v1\n"
            + "block:\n"
            + "  name: UserAuth\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: login\n"
            + "      contract:\n"
            + "        inputs:\n"
            + "          - name: username\n"
            + "            type: string\n"
            + "        outputs:\n"
            + "          - name: token\n"
            + "            type: string\n"
            + "      uses:\n"
            + "        allow: []\n"
            + "  effects:\n"
            + "    allow: []\n");
        assertFalse(target.blockDeclaresAllowedDeps(irFile),
            "blockDeclaresAllowedDeps should return false when IR has no impl.allowedDeps");
    }

    // --- AllowedDeps Guard: considerContainmentSurfaces always false ---

    @Test
    void considerContainmentSurfaces_returnsFalseEvenWithAllowedDeps() {
        NodeTarget target = new NodeTarget();
        BearIr irWithAllowedDeps = new BearIr("1", new BearIr.Block(
            "PaymentService", BearIr.BlockKind.LOGIC,
            List.of(), new BearIr.Effects(List.of()),
            new BearIr.Impl(List.of(new BearIr.AllowedDep("com.example:lib", "1.0"))),
            null, List.of()
        ));
        assertFalse(target.considerContainmentSurfaces(irWithAllowedDeps, Path.of(".")),
            "NodeTarget.considerContainmentSurfaces should always return false, even with allowedDeps");
    }

    @Test
    void considerContainmentSurfaces_returnsFalseWithoutAllowedDeps() {
        NodeTarget target = new NodeTarget();
        BearIr irWithoutAllowedDeps = createTestIr();
        assertFalse(target.considerContainmentSurfaces(irWithoutAllowedDeps, Path.of(".")),
            "NodeTarget.considerContainmentSurfaces should always return false");
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

    private BearIr createTestIrWithOp(String blockName, String opName) {
        return new BearIr("1", new BearIr.Block(
            blockName,
            BearIr.BlockKind.LOGIC,
            java.util.List.of(new BearIr.Operation(
                opName,
                new BearIr.Contract(
                    java.util.List.of(new BearIr.Field("input", BearIr.FieldType.STRING)),
                    java.util.List.of(new BearIr.Field("result", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(java.util.List.of()), null, java.util.List.of()
            )),
            new BearIr.Effects(java.util.List.of()),
            null,
            null,
            java.util.List.of()
        ));
    }
}
