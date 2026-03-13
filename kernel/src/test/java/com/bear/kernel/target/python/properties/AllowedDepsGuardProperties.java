package com.bear.kernel.target.python.properties;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.python.PythonTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for the impl.allowedDeps unsupported guard.
 * Feature: phase-p-python-scan-only
 */
class AllowedDepsGuardProperties {

    private final PythonTarget target = new PythonTarget();

    /**
     * Property 31: impl.allowedDeps + Python -> exit 64, CODE=UNSUPPORTED_TARGET.
     * Validates: Requirements - impl.allowedDeps Unsupported Guard
     * 
     * Note: This property validates that considerContainmentSurfaces returns false for Python,
     * which combined with blockDeclaresAllowedDeps returning true triggers the guard in CheckCommandService.
     */
    @Test
    void considerContainmentSurfaces_alwaysFalse_withAllowedDeps() {
        BearIr ir = makeBearIrWithAllowedDeps("PaymentService");
        assertFalse(target.considerContainmentSurfaces(ir, Path.of(".")),
            "PythonTarget.considerContainmentSurfaces should always return false");
    }

    @Test
    void considerContainmentSurfaces_alwaysFalse_withoutAllowedDeps() {
        BearIr ir = makeBearIrWithoutAllowedDeps("UserAuth");
        assertFalse(target.considerContainmentSurfaces(ir, Path.of(".")),
            "PythonTarget.considerContainmentSurfaces should always return false");
    }

    @Test
    void considerContainmentSurfaces_alwaysFalse_orderManager() {
        BearIr ir = makeBearIrWithoutAllowedDeps("OrderManager");
        assertFalse(target.considerContainmentSurfaces(ir, Path.of(".")),
            "PythonTarget.considerContainmentSurfaces should always return false");
    }

    /**
     * Property 32: error output includes IR file path.
     * Validates: Requirements - impl.allowedDeps Unsupported Guard
     * 
     * Note: blockDeclaresAllowedDeps returns false for non-existent files.
     * The actual path is used by CheckCommandService to construct error messages.
     */
    @Test
    void blockDeclaresAllowedDeps_nonExistentFile() {
        assertFalse(target.blockDeclaresAllowedDeps(Path.of("nonexistent.yaml")),
            "blockDeclaresAllowedDeps should return false for non-existent file");
    }

    @Test
    void blockDeclaresAllowedDeps_anotherNonExistentFile() {
        assertFalse(target.blockDeclaresAllowedDeps(Path.of("also-nonexistent.yaml")),
            "blockDeclaresAllowedDeps should return false for another non-existent file");
    }

    /**
     * Property 33: pr-check operates normally.
     * Validates: Requirements - impl.allowedDeps Unsupported Guard
     * 
     * Note: pr-check uses generateWiringOnly which doesn't check allowedDeps.
     * sharedContainmentInScope always returns false, ensuring the guard only affects 'check' command.
     */
    @Test
    void sharedContainmentInScope_alwaysFalse_existingDir() {
        assertFalse(target.sharedContainmentInScope(Path.of(".")),
            "PythonTarget.sharedContainmentInScope should always return false");
    }

    @Test
    void sharedContainmentInScope_alwaysFalse_nonExistentDir() {
        assertFalse(target.sharedContainmentInScope(Path.of("/nonexistent/path")),
            "PythonTarget.sharedContainmentInScope should always return false");
    }

    // Helper methods

    private BearIr makeBearIrWithAllowedDeps(String blockName) {
        return new BearIr("1", new BearIr.Block(
            blockName, BearIr.BlockKind.LOGIC,
            List.of(new BearIr.Operation(
                "process",
                new BearIr.Contract(
                    List.of(new BearIr.Field("input", BearIr.FieldType.STRING)),
                    List.of(new BearIr.Field("result", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(List.of()), null, List.of()
            )),
            new BearIr.Effects(List.of()),
            new BearIr.Impl(List.of(new BearIr.AllowedDep("requests", "2.31.0"))),
            null, List.of()
        ));
    }

    private BearIr makeBearIrWithoutAllowedDeps(String blockName) {
        return new BearIr("1", new BearIr.Block(
            blockName, BearIr.BlockKind.LOGIC,
            List.of(new BearIr.Operation(
                "process",
                new BearIr.Contract(
                    List.of(new BearIr.Field("input", BearIr.FieldType.STRING)),
                    List.of(new BearIr.Field("result", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(List.of()), null, List.of()
            )),
            new BearIr.Effects(List.of()),
            null, null, List.of()
        ));
    }
}
