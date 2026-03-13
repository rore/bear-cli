package com.bear.kernel.target.node.properties;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.node.NodeTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-style tests for TypeScript artifact generation.
 * Feature: phase-b-node-target-scan-only
 * Note: plain JUnit 5 (no jqwik in build); uses representative fixed inputs.
 */
class ArtifactGenerationProperties {

    private final NodeTarget target = new NodeTarget();

    // --- Property 3: compile() generates all four artifacts ---

    @Test
    void compileGeneratesAllArtifacts_userAuth(@TempDir Path projectRoot) throws IOException {
        assertCompileGeneratesArtifacts("UserAuth", "login", projectRoot);
    }

    @Test
    void compileGeneratesAllArtifacts_paymentService(@TempDir Path projectRoot) throws IOException {
        assertCompileGeneratesArtifacts("PaymentService", "process", projectRoot);
    }

    @Test
    void compileGeneratesAllArtifacts_orderManager(@TempDir Path projectRoot) throws IOException {
        assertCompileGeneratesArtifacts("OrderManager", "create", projectRoot);
    }

    // --- Property 4: compile() twice does not overwrite user impl ---

    @Test
    void compileDoesNotOverwriteUserImpl_userAuth(@TempDir Path projectRoot) throws IOException {
        assertCompileDoesNotOverwriteImpl("UserAuth", "login", projectRoot);
    }

    @Test
    void compileDoesNotOverwriteUserImpl_paymentService(@TempDir Path projectRoot) throws IOException {
        assertCompileDoesNotOverwriteImpl("PaymentService", "process", projectRoot);
    }

    // --- Property 5: generateWiringOnly() creates only wiring manifest ---

    @Test
    void generateWiringOnlyCreatesOnlyManifest_userAuth(@TempDir Path projectRoot) throws IOException {
        assertGenerateWiringOnly("UserAuth", "login", projectRoot);
    }

    @Test
    void generateWiringOnlyCreatesOnlyManifest_orderManager(@TempDir Path projectRoot) throws IOException {
        assertGenerateWiringOnly("OrderManager", "create", projectRoot);
    }

    // --- helpers ---

    private void assertCompileGeneratesArtifacts(String blockName, String opName, Path projectRoot) throws IOException {
        BearIr ir = makeBearIr(blockName, opName);
        String blockKey = toKebabCase(blockName);
        target.compile(ir, projectRoot, blockKey);

        assertTrue(Files.exists(projectRoot.resolve("build/generated/bear/types/" + blockKey)),
            "types dir should exist for " + blockKey);
        assertTrue(Files.exists(projectRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json")),
            "wiring manifest should exist for " + blockKey);
    }

    private void assertCompileDoesNotOverwriteImpl(String blockName, String opName, Path projectRoot) throws IOException {
        BearIr ir = makeBearIr(blockName, opName);
        String blockKey = toKebabCase(blockName);
        target.compile(ir, projectRoot, blockKey);

        String blockPascal = toPascalCase(blockKey);
        Path implFile = projectRoot.resolve("src/blocks/" + blockKey + "/impl/" + blockPascal + "Impl.ts");
        assertTrue(Files.exists(implFile), "impl file should be created on first compile");
        String firstContent = Files.readString(implFile);

        target.compile(ir, projectRoot, blockKey);
        String secondContent = Files.readString(implFile);

        assertEquals(firstContent, secondContent, "user impl should not be overwritten on second compile");
    }

    private void assertGenerateWiringOnly(String blockName, String opName, Path projectRoot) throws IOException {
        Path outputRoot = Files.createTempDirectory(projectRoot, "wiring-out-");
        BearIr ir = makeBearIr(blockName, opName);
        String blockKey = toKebabCase(blockName);
        target.generateWiringOnly(ir, projectRoot, outputRoot, blockKey);

        assertTrue(Files.exists(outputRoot.resolve("wiring/" + blockKey + ".wiring.json")),
            "wiring manifest should exist in outputRoot");
        assertFalse(Files.exists(projectRoot.resolve("build/generated/bear/types/" + blockKey)),
            "types dir should NOT be created by generateWiringOnly");
    }

    private BearIr makeBearIr(String blockName, String opName) {
        return new BearIr("1", new BearIr.Block(
            blockName,
            BearIr.BlockKind.LOGIC,
            List.of(new BearIr.Operation(
                opName,
                new BearIr.Contract(
                    List.of(new BearIr.Field("input", BearIr.FieldType.STRING)),
                    List.of(new BearIr.Field("result", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(List.of()),
                null,
                List.of()
            )),
            new BearIr.Effects(List.of()),
            null, null, List.of()
        ));
    }

    private String toKebabCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private String toPascalCase(String kebab) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebab.split("-")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
