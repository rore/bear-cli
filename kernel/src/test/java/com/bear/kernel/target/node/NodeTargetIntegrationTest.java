package com.bear.kernel.target.node;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.*;
import com.bear.kernel.target.jvm.JvmTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the Node target pipeline.
 * Tests NodeTargetDetector, NodeTarget (compile, drift, containment),
 * and NodeImportContainmentScanner working together against fixture projects.
 */
class NodeTargetIntegrationTest {

    // ---------------------------------------------------------------
    // Detect → compile → check (clean) — valid-single-block fixture
    // ---------------------------------------------------------------

    @Test
    void detectCompileCheck_cleanProject_noFindings(@TempDir Path workDir) throws Exception {
        copyFixtureTo("valid-single-block", workDir);

        // Step 1: Detect
        NodeTargetDetector detector = new NodeTargetDetector();
        DetectedTarget detection = detector.detect(workDir);
        assertEquals(DetectionStatus.SUPPORTED, detection.status());
        assertEquals(TargetId.NODE, detection.targetId());

        // Step 2: Compile
        NodeTarget target = new NodeTarget();
        BearIr ir = createIrWithOp("UserAuth", "login");
        target.compile(ir, workDir, "user-auth");

        // Verify artifacts were generated
        assertTrue(Files.exists(workDir.resolve("build/generated/bear/types/user-auth/UserAuthPorts.ts")));
        assertTrue(Files.exists(workDir.resolve("build/generated/bear/types/user-auth/UserAuthLogic.ts")));
        assertTrue(Files.exists(workDir.resolve("build/generated/bear/types/user-auth/UserAuthWrapper.ts")));
        assertTrue(Files.exists(workDir.resolve("build/generated/bear/wiring/user-auth.wiring.json")));
        assertTrue(Files.exists(workDir.resolve("src/blocks/user-auth/impl/UserAuthImpl.ts")));

        // Step 3: Check drift — should be clean
        List<TargetCheckIssue> driftFindings = target.checkDrift(ir, workDir, "user-auth");
        assertTrue(driftFindings.isEmpty(), "freshly compiled project should have no drift");

        // Step 4: Check containment — should be clean
        WiringManifest manifest = makeManifest("user-auth");
        List<BoundaryBypassFinding> containmentFindings =
            target.scanBoundaryBypass(workDir, List.of(manifest), Set.of());
        assertTrue(containmentFindings.isEmpty(), "valid-single-block should have no containment violations");
    }

    // ---------------------------------------------------------------
    // Detect → compile → modify generated file → check (drift, exit 5)
    // ---------------------------------------------------------------

    @Test
    void detectCompileModifyCheck_driftDetected(@TempDir Path workDir) throws Exception {
        copyFixtureTo("valid-single-block", workDir);

        // Detect + compile
        NodeTargetDetector detector = new NodeTargetDetector();
        assertEquals(DetectionStatus.SUPPORTED, detector.detect(workDir).status());

        NodeTarget target = new NodeTarget();
        BearIr ir = createIrWithOp("UserAuth", "login");
        target.compile(ir, workDir, "user-auth");

        // Modify a generated artifact
        Path portsFile = workDir.resolve("build/generated/bear/types/user-auth/UserAuthPorts.ts");
        assertTrue(Files.exists(portsFile));
        Files.writeString(portsFile, "// tampered content\n");

        // Check drift — should detect drift (exit 5)
        List<TargetCheckIssue> findings = target.checkDrift(ir, workDir, "user-auth");
        assertFalse(findings.isEmpty(), "modified generated file should produce drift findings");
        assertTrue(findings.stream().anyMatch(f -> f.kind() == TargetCheckIssueKind.DRIFT_DETECTED),
            "should contain DRIFT_DETECTED finding");
    }

    // ---------------------------------------------------------------
    // Detect → compile → boundary bypass → check (fail, exit 7)
    // ---------------------------------------------------------------

    @Test
    void boundaryBypassEscape_producesFindings() throws Exception {
        Path fixture = getFixturePath("boundary-bypass-escape");

        // Detect
        NodeTargetDetector detector = new NodeTargetDetector();
        assertEquals(DetectionStatus.SUPPORTED, detector.detect(fixture).status());

        // Scan containment — should find boundary bypass (escape)
        NodeTarget target = new NodeTarget();
        WiringManifest manifest = makeManifest("user-auth");
        List<BoundaryBypassFinding> findings =
            target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());

        assertFalse(findings.isEmpty(), "boundary-bypass-escape should produce findings");
        assertTrue(findings.stream().anyMatch(f ->
                f.path().contains("user-auth") && f.detail().contains("../../config.js")),
            "finding should reference the escaping import specifier");
    }

    @Test
    void boundaryBypassSibling_producesFindings() throws Exception {
        Path fixture = getFixturePath("boundary-bypass-sibling");

        NodeTargetDetector detector = new NodeTargetDetector();
        assertEquals(DetectionStatus.SUPPORTED, detector.detect(fixture).status());

        NodeTarget target = new NodeTarget();
        WiringManifest authManifest = makeManifest("user-auth");
        WiringManifest paymentManifest = makeManifest("payment");
        List<BoundaryBypassFinding> findings =
            target.scanBoundaryBypass(fixture, List.of(authManifest, paymentManifest), Set.of());

        assertFalse(findings.isEmpty(), "boundary-bypass-sibling should produce findings");
        assertTrue(findings.stream().anyMatch(f ->
                f.path().contains("user-auth") && f.detail().contains("../payment/index.js")),
            "finding should reference the sibling block import specifier");
    }

    @Test
    void boundaryBypassBareImport_producesFindings() throws Exception {
        Path fixture = getFixturePath("boundary-bypass-bare-import");

        NodeTargetDetector detector = new NodeTargetDetector();
        assertEquals(DetectionStatus.SUPPORTED, detector.detect(fixture).status());

        NodeTarget target = new NodeTarget();
        WiringManifest manifest = makeManifest("user-auth");
        List<BoundaryBypassFinding> findings =
            target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());

        assertFalse(findings.isEmpty(), "boundary-bypass-bare-import should produce findings");
        assertTrue(findings.stream().anyMatch(f ->
                f.detail().contains("lodash")),
            "finding should reference the bare package import specifier");
    }

    // ---------------------------------------------------------------
    // Detect with allowedDeps → check (exit 64)
    // ---------------------------------------------------------------

    @Test
    void allowedDeps_nodeTarget_blockDeclaresAllowedDeps(@TempDir Path workDir) throws IOException {
        Path irFile = workDir.resolve("ir.bear.yaml");
        Files.writeString(irFile, """
            version: v1
            block:
              name: PaymentService
              kind: logic
              operations:
                - name: charge
                  contract:
                    inputs:
                      - name: amount
                        type: int
                    outputs:
                      - name: receipt
                        type: string
                  uses:
                    allow: []
              effects:
                allow: []
              impl:
                allowedDeps:
                  - maven: com.example:lib
                    version: "1.0"
            """);

        NodeTarget target = new NodeTarget();
        assertTrue(target.blockDeclaresAllowedDeps(irFile),
            "NodeTarget should detect impl.allowedDeps in IR file");
    }

    @Test
    void allowedDeps_absent_passesGuard(@TempDir Path workDir) throws IOException {
        Path irFile = workDir.resolve("ir.bear.yaml");
        Files.writeString(irFile, """
            version: v1
            block:
              name: UserAuth
              kind: logic
              operations:
                - name: login
                  contract:
                    inputs:
                      - name: username
                        type: string
                    outputs:
                      - name: token
                        type: string
                  uses:
                    allow: []
              effects:
                allow: []
            """);

        NodeTarget target = new NodeTarget();
        assertFalse(target.blockDeclaresAllowedDeps(irFile),
            "NodeTarget should not detect allowedDeps when absent");
    }

    // ---------------------------------------------------------------
    // JVM project → resolves to JvmTarget (no interference)
    // ---------------------------------------------------------------

    @Test
    void jvmProject_resolvesToJvmTarget_noNodeInterference(@TempDir Path workDir) throws IOException {
        Files.writeString(workDir.resolve("build.gradle"), "// JVM project\n");

        TargetRegistry registry = TargetRegistry.defaultRegistry();
        Target resolved = registry.resolve(workDir);

        assertEquals(TargetId.JVM, resolved.targetId(),
            "JVM project should resolve to JvmTarget, not NodeTarget");
        assertInstanceOf(JvmTarget.class, resolved);
    }

    @Test
    void nodeProject_resolvesToNodeTarget() throws Exception {
        Path fixture = getFixturePath("valid-single-block");

        TargetRegistry registry = TargetRegistry.defaultRegistry();
        Target resolved = registry.resolve(fixture);

        assertEquals(TargetId.NODE, resolved.targetId(),
            "Node project should resolve to NodeTarget");
        assertInstanceOf(NodeTarget.class, resolved);
    }

    // ---------------------------------------------------------------
    // Invalid fixtures — detection edge cases
    // ---------------------------------------------------------------

    @Test
    void invalidWorkspace_detectedAsUnsupported() throws Exception {
        Path fixture = getFixturePath("invalid-workspace");

        NodeTargetDetector detector = new NodeTargetDetector();
        DetectedTarget result = detector.detect(fixture);
        assertEquals(DetectionStatus.UNSUPPORTED, result.status(),
            "workspace project should be UNSUPPORTED");
    }

    @Test
    void invalidMissingLockfile_detectedAsNone() throws Exception {
        Path fixture = getFixturePath("invalid-missing-lockfile");

        NodeTargetDetector detector = new NodeTargetDetector();
        DetectedTarget result = detector.detect(fixture);
        assertEquals(DetectionStatus.NONE, result.status(),
            "project without pnpm-lock.yaml should be NONE");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Path getFixturePath(String fixtureName) throws URISyntaxException {
        return Paths.get(
            Objects.requireNonNull(
                getClass().getClassLoader().getResource("fixtures/node/" + fixtureName)
            ).toURI()
        );
    }

    private void copyFixtureTo(String fixtureName, Path targetDir) throws Exception {
        Path source = getFixturePath(fixtureName);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Files.createDirectories(targetDir.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Files.copy(file, targetDir.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
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

    private BearIr createIrWithOp(String blockName, String opName) {
        return new BearIr("1", new BearIr.Block(
            blockName,
            BearIr.BlockKind.LOGIC,
            List.of(new BearIr.Operation(
                opName,
                new BearIr.Contract(
                    List.of(new BearIr.Field("input", BearIr.FieldType.STRING)),
                    List.of(new BearIr.Field("result", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(List.of()), null, List.of()
            )),
            new BearIr.Effects(List.of()),
            null,
            null,
            List.of()
        ));
    }
}
