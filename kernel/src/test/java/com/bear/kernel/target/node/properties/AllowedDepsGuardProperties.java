package com.bear.kernel.target.node.properties;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.node.NodeTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for the impl.allowedDeps unsupported guard.
 * Feature: phase-b-node-target-scan-only
 */
class AllowedDepsGuardProperties {

    private final NodeTarget target = new NodeTarget();

    /** Property 32: impl.allowedDeps + Node -> considerContainmentSurfaces() returns false even with allowedDeps. */
    @Test
    void considerContainmentSurfacesAlwaysFalse_withAllowedDeps() {
        BearIr ir = makeBearIrWithAllowedDeps("PaymentService");
        assertFalse(target.considerContainmentSurfaces(ir, Path.of(".")),
            "NodeTarget.considerContainmentSurfaces should always return false");
    }

    @Test
    void considerContainmentSurfacesAlwaysFalse_withoutAllowedDeps() {
        BearIr ir = makeBearIrWithoutAllowedDeps("UserAuth");
        assertFalse(target.considerContainmentSurfaces(ir, Path.of(".")),
            "NodeTarget.considerContainmentSurfaces should always return false");
    }

    @Test
    void considerContainmentSurfacesAlwaysFalse_orderManager() {
        BearIr ir = makeBearIrWithoutAllowedDeps("OrderManager");
        assertFalse(target.considerContainmentSurfaces(ir, Path.of(".")));
    }

    /** Property 33: blockDeclaresAllowedDeps() returns false for non-existent IR file. */
    @Test
    void blockDeclaresAllowedDepsReturnsFalseForMissingFile() {
        assertFalse(target.blockDeclaresAllowedDeps(Path.of("nonexistent.yaml")));
    }

    @Test
    void blockDeclaresAllowedDepsReturnsFalseForAnotherMissingFile() {
        assertFalse(target.blockDeclaresAllowedDeps(Path.of("also-nonexistent.yaml")));
    }

    /** Property 34: sharedContainmentInScope always returns false for NodeTarget. */
    @Test
    void sharedContainmentInScopeAlwaysFalse_existingDir() {
        assertFalse(target.sharedContainmentInScope(Path.of(".")));
    }

    @Test
    void sharedContainmentInScopeAlwaysFalse_nonExistentDir() {
        assertFalse(target.sharedContainmentInScope(Path.of("/nonexistent/path")));
    }

    private BearIr makeBearIrWithAllowedDeps(String blockName) {
        return new BearIr("1", new BearIr.Block(
            blockName, BearIr.BlockKind.LOGIC,
            List.of(), new BearIr.Effects(List.of()),
            new BearIr.Impl(List.of(new BearIr.AllowedDep("com.example:lib", "1.0"))),
            null, List.of()
        ));
    }

    private BearIr makeBearIrWithoutAllowedDeps(String blockName) {
        return new BearIr("1", new BearIr.Block(
            blockName, BearIr.BlockKind.LOGIC,
            List.of(), new BearIr.Effects(List.of()),
            null, null, List.of()
        ));
    }
}
