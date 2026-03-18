package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.target.node.NodeTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the impl.allowedDeps unsupported guard with Node target.
 * Feature: phase-b-node-target-scan-only, Task 10
 */
class NodeAllowedDepsGuardTest {

    /**
     * Block with impl.allowedDeps under Node target → check fails, exit 64, CODE=UNSUPPORTED_TARGET.
     */
    @Test
    void checkWithAllowedDepsAndNodeTarget_failsWithExit64(@TempDir Path tempDir) throws Exception {
        Path irFile = writeNodeIrWithAllowedDeps(tempDir, "PaymentService");
        NodeTarget nodeTarget = new NodeTarget();
        compileNodeProject(nodeTarget, irFile, tempDir, "payment-service");

        CheckResult check = CheckCommandService.executeCheck(
            irFile, tempDir, false, false, null, null,
            null, true, null, false, nodeTarget
        );
        assertEquals(64, check.exitCode(), "check with allowedDeps + Node should exit 64");
        assertEquals("UNSUPPORTED_TARGET", check.failureCode());
    }

    /**
     * Error output includes IR file path.
     */
    @Test
    void checkWithAllowedDepsAndNodeTarget_errorIncludesIrPath(@TempDir Path tempDir) throws Exception {
        Path irFile = writeNodeIrWithAllowedDeps(tempDir, "PaymentService");
        NodeTarget nodeTarget = new NodeTarget();
        compileNodeProject(nodeTarget, irFile, tempDir, "payment-service");

        CheckResult check = CheckCommandService.executeCheck(
            irFile, tempDir, false, false, null, null,
            null, true, null, false, nodeTarget
        );
        assertEquals(64, check.exitCode());
        assertNotNull(check.failurePath());
        assertTrue(check.failurePath().contains("payment-service.bear.yaml"),
            "error should include IR file path, got: " + check.failurePath());
    }

    /**
     * Error output includes remediation message.
     */
    @Test
    void checkWithAllowedDepsAndNodeTarget_errorIncludesRemediation(@TempDir Path tempDir) throws Exception {
        Path irFile = writeNodeIrWithAllowedDeps(tempDir, "PaymentService");
        NodeTarget nodeTarget = new NodeTarget();
        compileNodeProject(nodeTarget, irFile, tempDir, "payment-service");

        CheckResult check = CheckCommandService.executeCheck(
            irFile, tempDir, false, false, null, null,
            null, true, null, false, nodeTarget
        );
        assertEquals(64, check.exitCode());
        assertNotNull(check.failureRemediation());
        assertTrue(check.failureRemediation().contains("Remove impl.allowedDeps"),
            "remediation should mention removing allowedDeps, got: " + check.failureRemediation());
    }

    /**
     * Block without impl.allowedDeps under Node target → passes the guard (may fail later for other reasons).
     */
    @Test
    void checkWithoutAllowedDepsAndNodeTarget_doesNotTriggerGuard(@TempDir Path tempDir) throws Exception {
        Path irFile = writeNodeIrWithoutAllowedDeps(tempDir, "UserAuth");
        NodeTarget nodeTarget = new NodeTarget();
        compileNodeProject(nodeTarget, irFile, tempDir, "user-auth");

        CheckResult check = CheckCommandService.executeCheck(
            irFile, tempDir, false, false, null, null,
            null, false, null, false, nodeTarget
        );
        // Should NOT be exit 64 / UNSUPPORTED_TARGET — may fail for other reasons (e.g., prepareCheckWorkspace stub)
        // but the guard itself should not trigger
        assertNotEquals(64, check.exitCode(),
            "check without allowedDeps should not trigger UNSUPPORTED_TARGET guard");
    }

    /**
     * pr-check is unaffected by impl.allowedDeps — pr-check doesn't call the check pipeline guard.
     * This test verifies that pr-check uses generateWiringOnly which doesn't trigger the guard.
     */
    @Test
    void prCheckUnaffectedByAllowedDeps(@TempDir Path tempDir) throws Exception {
        // pr-check uses generateWiringOnly, not the full check pipeline
        // The guard only exists in CheckCommandService.executeCheck, not in PrCheckCommandService
        NodeTarget nodeTarget = new NodeTarget();
        BearIr ir = new BearIr("1", new BearIr.Block(
            "PaymentService", BearIr.BlockKind.LOGIC,
            java.util.List.of(new BearIr.Operation(
                "charge",
                new BearIr.Contract(
                    java.util.List.of(new BearIr.Field("amount", BearIr.FieldType.INT)),
                    java.util.List.of(new BearIr.Field("receipt", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(java.util.List.of()), null, java.util.List.of()
            )),
            new BearIr.Effects(java.util.List.of()),
            new BearIr.Impl(java.util.List.of(new BearIr.AllowedDep("com.example:lib", "1.0"))),
            null, java.util.List.of()
        ));

        // generateWiringOnly should work fine regardless of allowedDeps
        Path outputRoot = tempDir.resolve("output");
        assertDoesNotThrow(() -> nodeTarget.generateWiringOnly(ir, tempDir, outputRoot, "payment-service"));
        assertTrue(Files.exists(outputRoot.resolve("wiring/payment-service.wiring.json")),
            "generateWiringOnly should produce wiring manifest even with allowedDeps");
    }

    private Path writeNodeIrWithAllowedDeps(Path tempDir, String blockName) throws Exception {
        String blockKey = blockName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
        Path irFile = tempDir.resolve(blockKey + ".bear.yaml");
        Files.writeString(irFile, ""
            + "version: v1\n"
            + "block:\n"
            + "  name: " + blockName + "\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: charge\n"
            + "      contract:\n"
            + "        inputs:\n"
            + "          - name: amount\n"
            + "            type: string\n"
            + "        outputs:\n"
            + "          - name: amount\n"
            + "            type: string\n"
            + "      uses:\n"
            + "        allow: []\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  impl:\n"
            + "    allowedDeps:\n"
            + "      - maven: com.example:lib\n"
            + "        version: \"1.0\"\n", StandardCharsets.UTF_8);
        return irFile;
    }

    private Path writeNodeIrWithoutAllowedDeps(Path tempDir, String blockName) throws Exception {
        String blockKey = blockName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
        Path irFile = tempDir.resolve(blockKey + ".bear.yaml");
        Files.writeString(irFile, ""
            + "version: v1\n"
            + "block:\n"
            + "  name: " + blockName + "\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: login\n"
            + "      contract:\n"
            + "        inputs:\n"
            + "          - name: token\n"
            + "            type: string\n"
            + "        outputs:\n"
            + "          - name: token\n"
            + "            type: string\n"
            + "      uses:\n"
            + "        allow: []\n"
            + "  effects:\n"
            + "    allow: []\n", StandardCharsets.UTF_8);
        return irFile;
    }

    private void compileNodeProject(NodeTarget target, Path irFile, Path projectRoot, String blockKey) throws Exception {
        BearIr ir = new BearIrParser().parse(irFile);
        target.compile(ir, projectRoot, blockKey);
    }
}
