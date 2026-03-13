package com.bear.kernel.target.python.properties;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.python.PythonTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for the drift gate.
 * Feature: phase-p-python-scan-only
 */
class DriftGateProperties {

    private final PythonTarget target = new PythonTarget();

    /** Property 28: compile() then immediate re-compile to temp -> wiring manifests match. */
    @Test
    void compileProducesDeterministicWiring_userAuth(@TempDir Path projectRoot) throws IOException {
        assertDeterministicWiring(makeBearIr("UserAuth", "login"), projectRoot);
    }

    @Test
    void compileProducesDeterministicWiring_paymentService(@TempDir Path projectRoot) throws IOException {
        assertDeterministicWiring(makeBearIr("PaymentService", "process"), projectRoot);
    }

    @Test
    void compileProducesDeterministicWiring_orderManager(@TempDir Path projectRoot) throws IOException {
        assertDeterministicWiring(makeBearIr("OrderManager", "create"), projectRoot);
    }

    /** Property 29: Generated file modified after compile() -> content differs from fresh compile. */
    @Test
    void modifiedGeneratedFileDetectedAsDrift_userAuth(@TempDir Path projectRoot) throws IOException {
        assertModifiedFileDetected(makeBearIr("UserAuth", "login"), projectRoot);
    }

    @Test
    void modifiedGeneratedFileDetectedAsDrift_paymentService(@TempDir Path projectRoot) throws IOException {
        assertModifiedFileDetected(makeBearIr("PaymentService", "process"), projectRoot);
    }

    /** Property 30: User-owned impl modified -> generated artifacts still match fresh compile. */
    @Test
    void userImplModifiedDoesNotAffectGeneratedArtifacts_userAuth(@TempDir Path projectRoot) throws IOException {
        assertUserImplModificationDoesNotAffectGenerated(makeBearIr("UserAuth", "login"), projectRoot);
    }

    @Test
    void userImplModifiedDoesNotAffectGeneratedArtifacts_orderManager(@TempDir Path projectRoot) throws IOException {
        assertUserImplModificationDoesNotAffectGenerated(makeBearIr("OrderManager", "create"), projectRoot);
    }

    private void assertDeterministicWiring(BearIr ir, Path projectRoot) throws IOException {
        String blockKey = toKebabCase(ir.block().name());
        target.compile(ir, projectRoot, blockKey);

        Path tempRoot = Files.createTempDirectory("bear-drift-temp-");
        try {
            target.compile(ir, tempRoot, blockKey);
            Path wiringA = projectRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
            Path wiringB = tempRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
            assertEquals(Files.readString(wiringA), Files.readString(wiringB),
                "wiring manifest should be identical across two compiles");
        } finally {
            deleteQuietly(tempRoot);
        }
    }

    private void assertModifiedFileDetected(BearIr ir, Path projectRoot) throws IOException {
        String blockKey = toKebabCase(ir.block().name());
        target.compile(ir, projectRoot, blockKey);

        Path wiringFile = projectRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
        Files.writeString(wiringFile, "{ \"modified\": true }");

        Path tempRoot = Files.createTempDirectory("bear-drift-mod-temp-");
        try {
            target.compile(ir, tempRoot, blockKey);
            Path freshWiring = tempRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
            assertNotEquals(Files.readString(wiringFile), Files.readString(freshWiring),
                "modified wiring should differ from fresh compile");
        } finally {
            deleteQuietly(tempRoot);
        }
    }

    private void assertUserImplModificationDoesNotAffectGenerated(BearIr ir, Path projectRoot) throws IOException {
        String blockKey = toKebabCase(ir.block().name());
        String blockName = toSnakeCase(blockKey);
        target.compile(ir, projectRoot, blockKey);

        Path implFile = projectRoot.resolve("src/blocks/" + blockKey + "/impl/" + blockName + "_impl.py");
        Files.writeString(implFile, "# user modified\n");

        Path tempRoot = Files.createTempDirectory("bear-drift-impl-temp-");
        try {
            target.compile(ir, tempRoot, blockKey);
            Path wiringA = projectRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
            Path wiringB = tempRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
            assertEquals(Files.readString(wiringA), Files.readString(wiringB),
                "user impl modification should not affect generated wiring");
        } finally {
            deleteQuietly(tempRoot);
        }
    }

    private static BearIr makeBearIr(String blockName, String opName) {
        return new BearIr("1", new BearIr.Block(
            blockName, BearIr.BlockKind.LOGIC,
            List.of(new BearIr.Operation(
                opName,
                new BearIr.Contract(
                    List.of(new BearIr.Field("input", BearIr.FieldType.STRING)),
                    List.of(new BearIr.Field("result", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(List.of()), null, List.of()
            )),
            new BearIr.Effects(List.of()), null, null, List.of()
        ));
    }

    private static String toKebabCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private static String toSnakeCase(String kebab) {
        return kebab.replace("-", "_");
    }

    private void deleteQuietly(Path dir) {
        try {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) {}
    }
}
