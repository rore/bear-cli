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
 * Property-style tests for Python artifact generation.
 * Feature: phase-p-python-scan-only
 * Note: plain JUnit 5 (no jqwik in build); uses representative fixed inputs.
 */
class ArtifactGenerationProperties {

    private final PythonTarget target = new PythonTarget();

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

    // --- Property 6: all generated Python files are parseable ---

    @Test
    void generatedPythonFilesAreParseable_userAuth(@TempDir Path projectRoot) throws IOException {
        assertGeneratedPythonIsParseable("UserAuth", "login", projectRoot);
    }

    @Test
    void generatedPythonFilesAreParseable_paymentService(@TempDir Path projectRoot) throws IOException {
        assertGeneratedPythonIsParseable("PaymentService", "process", projectRoot);
    }

    @Test
    void generatedPythonFilesAreParseable_orderManager(@TempDir Path projectRoot) throws IOException {
        assertGeneratedPythonIsParseable("OrderManager", "create", projectRoot);
    }

    // --- helpers ---

    private void assertCompileGeneratesArtifacts(String blockName, String opName, Path projectRoot) throws IOException {
        BearIr ir = makeBearIr(blockName, opName);
        String blockKey = toKebabCase(blockName);
        target.compile(ir, projectRoot, blockKey);

        String blockSnake = toSnakeCase(blockKey);
        Path generatedDir = projectRoot.resolve("build/generated/bear/" + blockKey);
        
        assertTrue(Files.exists(generatedDir.resolve(blockSnake + "_ports.py")),
            "ports.py should exist for " + blockKey);
        assertTrue(Files.exists(generatedDir.resolve(blockSnake + "_logic.py")),
            "logic.py should exist for " + blockKey);
        assertTrue(Files.exists(generatedDir.resolve(blockSnake + "_wrapper.py")),
            "wrapper.py should exist for " + blockKey);
        assertTrue(Files.exists(projectRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json")),
            "wiring manifest should exist for " + blockKey);
    }

    private void assertCompileDoesNotOverwriteImpl(String blockName, String opName, Path projectRoot) throws IOException {
        BearIr ir = makeBearIr(blockName, opName);
        String blockKey = toKebabCase(blockName);
        target.compile(ir, projectRoot, blockKey);

        String blockSnake = toSnakeCase(blockKey);
        Path implFile = projectRoot.resolve("src/blocks/" + blockKey + "/impl/" + blockSnake + "_impl.py");
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
        assertFalse(Files.exists(projectRoot.resolve("build/generated/bear/" + blockKey)),
            "generated dir should NOT be created by generateWiringOnly");
    }

    private void assertGeneratedPythonIsParseable(String blockName, String opName, Path projectRoot) throws IOException {
        BearIr ir = makeBearIr(blockName, opName);
        String blockKey = toKebabCase(blockName);
        target.compile(ir, projectRoot, blockKey);

        String blockSnake = toSnakeCase(blockKey);
        Path generatedDir = projectRoot.resolve("build/generated/bear/" + blockKey);
        
        // Check that generated Python files have valid syntax (basic checks)
        String portsContent = Files.readString(generatedDir.resolve(blockSnake + "_ports.py"));
        assertValidPythonSyntax(portsContent, "ports.py");
        
        String logicContent = Files.readString(generatedDir.resolve(blockSnake + "_logic.py"));
        assertValidPythonSyntax(logicContent, "logic.py");
        
        String wrapperContent = Files.readString(generatedDir.resolve(blockSnake + "_wrapper.py"));
        assertValidPythonSyntax(wrapperContent, "wrapper.py");
    }

    private void assertValidPythonSyntax(String content, String filename) {
        // Basic Python syntax validation
        assertFalse(content.contains(";;"), filename + " should not contain double semicolons");
        assertFalse(content.contains("{}"), filename + " should not contain empty braces (not Python)");
        
        // Check for Python header comment or imports (all generated files have these)
        assertTrue(content.contains("# Generated by bear compile") || 
                   content.contains("from typing import") ||
                   content.contains("from dataclasses import") ||
                   content.contains("class ") ||
                   content.contains("def "),
            filename + " should contain Python code markers");
        
        // Check for proper indentation (no tabs, consistent spaces)
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("\t")) {
                fail(filename + " should not contain tab characters for indentation");
            }
        }
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

    private String toSnakeCase(String kebab) {
        return kebab.replace('-', '_');
    }
}
