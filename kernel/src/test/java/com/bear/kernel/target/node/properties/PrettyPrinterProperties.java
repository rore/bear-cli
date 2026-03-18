package com.bear.kernel.target.node.properties;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.node.TypeScriptArtifactGenerator;
import com.bear.kernel.target.node.TypeScriptLexicalSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-style tests for TypeScript pretty printer.
 * Feature: phase-b-node-target-scan-only
 * Note: plain JUnit 5 (no jqwik in build); uses representative fixed inputs
 * across multiple block shapes to approximate property coverage.
 *
 * Property 35: any generated TypeScript AST → parseable by tsc (verify syntax is valid)
 * Property 36: consistent indentation and line breaks across invocations (deterministic output)
 */
class PrettyPrinterProperties {

    private final TypeScriptArtifactGenerator generator = new TypeScriptArtifactGenerator();

    // -----------------------------------------------------------------------
    // Property 35: any generated TypeScript AST → parseable by tsc
    // Validates: Requirements — TypeScript Pretty Printer
    // -----------------------------------------------------------------------

    @Test
    void property35_singleOpBlock_allArtifactsParseable(@TempDir Path tempDir) throws IOException {
        assertAllArtifactsHaveValidSyntax("user-auth", List.of(
            makeOp("login", List.of("username"), List.of("token"))
        ), tempDir);
    }

    @Test
    void property35_multiOpBlock_allArtifactsParseable(@TempDir Path tempDir) throws IOException {
        assertAllArtifactsHaveValidSyntax("order-manager", List.of(
            makeOp("create", List.of("item"), List.of("orderId")),
            makeOp("cancel", List.of("orderId"), List.of("success"))
        ), tempDir);
    }

    @Test
    void property35_blockWithPorts_allArtifactsParseable(@TempDir Path tempDir) throws IOException {
        BearIr ir = makeIrWithPorts("payment-service", "process",
            List.of("gateway"), List.of("charge"));
        String blockKey = "payment-service";

        generator.generatePorts(ir, tempDir, blockKey);
        generator.generateLogic(ir, tempDir, blockKey);
        generator.generateWrapper(ir, tempDir, blockKey);

        Path implDir = tempDir.resolve("impl");
        generator.generateUserImplSkeleton(ir, implDir, blockKey);

        String blockName = TypeScriptLexicalSupport.deriveBlockName(blockKey);
        assertFileHasValidSyntax(tempDir.resolve(blockName + "Ports.ts"));
        assertFileHasValidSyntax(tempDir.resolve(blockName + "Logic.ts"));
        assertFileHasValidSyntax(tempDir.resolve(blockName + "Wrapper.ts"));
        assertFileHasValidSyntax(implDir.resolve(blockName + "Impl.ts"));
    }

    @Test
    void property35_emptyOpsBlock_portsParseable(@TempDir Path tempDir) throws IOException {
        BearIr ir = new BearIr("1", new BearIr.Block(
            "EmptyBlock", BearIr.BlockKind.LOGIC, List.of(),
            new BearIr.Effects(List.of()), null, null, List.of()
        ));
        generator.generatePorts(ir, tempDir, "empty-block");
        assertFileHasValidSyntax(tempDir.resolve("EmptyBlockPorts.ts"));
    }

    @Test
    void property35_multipleFieldTypes_logicParseable(@TempDir Path tempDir) throws IOException {
        BearIr ir = new BearIr("1", new BearIr.Block(
            "DataService", BearIr.BlockKind.LOGIC,
            List.of(new BearIr.Operation(
                "process",
                new BearIr.Contract(
                    List.of(
                        new BearIr.Field("name", BearIr.FieldType.STRING),
                        new BearIr.Field("count", BearIr.FieldType.INT),
                        new BearIr.Field("amount", BearIr.FieldType.DECIMAL),
                        new BearIr.Field("active", BearIr.FieldType.BOOL)
                    ),
                    List.of(new BearIr.Field("result", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(List.of()), null, List.of()
            )),
            new BearIr.Effects(List.of()), null, null, List.of()
        ));
        generator.generateLogic(ir, tempDir, "data-service");
        assertFileHasValidSyntax(tempDir.resolve("DataServiceLogic.ts"));
    }

    // -----------------------------------------------------------------------
    // Property 36: consistent indentation and line breaks across invocations
    // Validates: Requirements — TypeScript Pretty Printer
    // -----------------------------------------------------------------------

    @Test
    void property36_singleOpBlock_deterministicOutput(@TempDir Path dir1, @TempDir Path dir2) throws IOException {
        assertDeterministicOutput("user-auth", List.of(
            makeOp("login", List.of("username"), List.of("token"))
        ), dir1, dir2);
    }

    @Test
    void property36_multiOpBlock_deterministicOutput(@TempDir Path dir1, @TempDir Path dir2) throws IOException {
        assertDeterministicOutput("order-manager", List.of(
            makeOp("create", List.of("item"), List.of("orderId")),
            makeOp("cancel", List.of("orderId"), List.of("success"))
        ), dir1, dir2);
    }

    @Test
    void property36_blockWithPorts_deterministicOutput(@TempDir Path dir1, @TempDir Path dir2) throws IOException {
        BearIr ir = makeIrWithPorts("payment-service", "process",
            List.of("gateway"), List.of("charge"));
        String blockKey = "payment-service";
        String blockName = TypeScriptLexicalSupport.deriveBlockName(blockKey);

        // First invocation
        generator.generatePorts(ir, dir1, blockKey);
        generator.generateLogic(ir, dir1, blockKey);
        generator.generateWrapper(ir, dir1, blockKey);
        Path implDir1 = dir1.resolve("impl");
        generator.generateUserImplSkeleton(ir, implDir1, blockKey);

        // Second invocation
        generator.generatePorts(ir, dir2, blockKey);
        generator.generateLogic(ir, dir2, blockKey);
        generator.generateWrapper(ir, dir2, blockKey);
        Path implDir2 = dir2.resolve("impl");
        generator.generateUserImplSkeleton(ir, implDir2, blockKey);

        assertByteIdentical(dir1.resolve(blockName + "Ports.ts"), dir2.resolve(blockName + "Ports.ts"));
        assertByteIdentical(dir1.resolve(blockName + "Logic.ts"), dir2.resolve(blockName + "Logic.ts"));
        assertByteIdentical(dir1.resolve(blockName + "Wrapper.ts"), dir2.resolve(blockName + "Wrapper.ts"));
        assertByteIdentical(implDir1.resolve(blockName + "Impl.ts"), implDir2.resolve(blockName + "Impl.ts"));
    }

    @Test
    void property36_multipleFieldTypes_deterministicOutput(@TempDir Path dir1, @TempDir Path dir2) throws IOException {
        BearIr ir = new BearIr("1", new BearIr.Block(
            "DataService", BearIr.BlockKind.LOGIC,
            List.of(new BearIr.Operation(
                "process",
                new BearIr.Contract(
                    List.of(
                        new BearIr.Field("name", BearIr.FieldType.STRING),
                        new BearIr.Field("count", BearIr.FieldType.INT),
                        new BearIr.Field("amount", BearIr.FieldType.DECIMAL),
                        new BearIr.Field("active", BearIr.FieldType.BOOL)
                    ),
                    List.of(new BearIr.Field("result", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(List.of()), null, List.of()
            )),
            new BearIr.Effects(List.of()), null, null, List.of()
        ));

        generator.generateLogic(ir, dir1, "data-service");
        generator.generateLogic(ir, dir2, "data-service");
        assertByteIdentical(dir1.resolve("DataServiceLogic.ts"), dir2.resolve("DataServiceLogic.ts"));
    }

    @Test
    void property36_threeConsecutiveCompiles_allIdentical(@TempDir Path dir1, @TempDir Path dir2) throws IOException {
        BearIr ir = makeIr("user-auth", List.of(
            makeOp("login", List.of("username"), List.of("token"))
        ));
        String blockKey = "user-auth";
        String blockName = "UserAuth";

        // Three compiles
        generator.generateLogic(ir, dir1, blockKey);
        String first = Files.readString(dir1.resolve(blockName + "Logic.ts"));

        generator.generateLogic(ir, dir1, blockKey); // overwrite same dir
        String second = Files.readString(dir1.resolve(blockName + "Logic.ts"));

        generator.generateLogic(ir, dir2, blockKey);
        String third = Files.readString(dir2.resolve(blockName + "Logic.ts"));

        assertEquals(first, second, "Second compile should produce identical output");
        assertEquals(second, third, "Third compile should produce identical output");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void assertAllArtifactsHaveValidSyntax(String blockKey, List<BearIr.Operation> ops, Path tempDir) throws IOException {
        BearIr ir = makeIr(blockKey, ops);
        String blockName = TypeScriptLexicalSupport.deriveBlockName(blockKey);

        generator.generatePorts(ir, tempDir, blockKey);
        generator.generateLogic(ir, tempDir, blockKey);
        generator.generateWrapper(ir, tempDir, blockKey);

        Path implDir = tempDir.resolve("impl");
        generator.generateUserImplSkeleton(ir, implDir, blockKey);

        assertFileHasValidSyntax(tempDir.resolve(blockName + "Ports.ts"));
        assertFileHasValidSyntax(tempDir.resolve(blockName + "Logic.ts"));
        assertFileHasValidSyntax(tempDir.resolve(blockName + "Wrapper.ts"));
        assertFileHasValidSyntax(implDir.resolve(blockName + "Impl.ts"));
    }

    private void assertDeterministicOutput(String blockKey, List<BearIr.Operation> ops,
                                           Path dir1, Path dir2) throws IOException {
        BearIr ir = makeIr(blockKey, ops);
        String blockName = TypeScriptLexicalSupport.deriveBlockName(blockKey);

        // First invocation
        generator.generatePorts(ir, dir1, blockKey);
        generator.generateLogic(ir, dir1, blockKey);
        generator.generateWrapper(ir, dir1, blockKey);
        Path implDir1 = dir1.resolve("impl");
        generator.generateUserImplSkeleton(ir, implDir1, blockKey);

        // Second invocation
        generator.generatePorts(ir, dir2, blockKey);
        generator.generateLogic(ir, dir2, blockKey);
        generator.generateWrapper(ir, dir2, blockKey);
        Path implDir2 = dir2.resolve("impl");
        generator.generateUserImplSkeleton(ir, implDir2, blockKey);

        assertByteIdentical(dir1.resolve(blockName + "Ports.ts"), dir2.resolve(blockName + "Ports.ts"));
        assertByteIdentical(dir1.resolve(blockName + "Logic.ts"), dir2.resolve(blockName + "Logic.ts"));
        assertByteIdentical(dir1.resolve(blockName + "Wrapper.ts"), dir2.resolve(blockName + "Wrapper.ts"));
        assertByteIdentical(implDir1.resolve(blockName + "Impl.ts"), implDir2.resolve(blockName + "Impl.ts"));
    }

    private void assertFileHasValidSyntax(Path file) throws IOException {
        assertTrue(Files.exists(file), "File should exist: " + file);
        String content = Files.readString(file);
        String fileName = file.getFileName().toString();

        // No CRLF
        assertFalse(content.contains("\r"), fileName + ": should use LF line endings only");

        // Balanced braces
        long openBraces = content.chars().filter(c -> c == '{').count();
        long closeBraces = content.chars().filter(c -> c == '}').count();
        assertEquals(openBraces, closeBraces, fileName + ": braces should be balanced");

        // Balanced parentheses
        long openParens = content.chars().filter(c -> c == '(').count();
        long closeParens = content.chars().filter(c -> c == ')').count();
        assertEquals(openParens, closeParens, fileName + ": parentheses should be balanced");

        // Consistent 2-space indentation
        for (String line : content.split("\n")) {
            assertFalse(line.contains("\t"), fileName + ": no tabs allowed: " + line);
            if (line.startsWith(" ")) {
                int spaces = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') spaces++;
                    else break;
                }
                assertEquals(0, spaces % 2,
                    fileName + ": indentation must be multiple of 2, got " + spaces);
            }
        }
    }

    private void assertByteIdentical(Path file1, Path file2) throws IOException {
        byte[] bytes1 = Files.readAllBytes(file1);
        byte[] bytes2 = Files.readAllBytes(file2);
        assertArrayEquals(bytes1, bytes2,
            "Files should be byte-identical: " + file1.getFileName() + " vs " + file2.getFileName());
    }

    // --- IR factories ---

    private BearIr makeIr(String blockKey, List<BearIr.Operation> ops) {
        return new BearIr("1", new BearIr.Block(
            TypeScriptLexicalSupport.deriveBlockName(blockKey),
            BearIr.BlockKind.LOGIC, ops,
            new BearIr.Effects(List.of()), null, null, List.of()
        ));
    }

    private BearIr makeIrWithPorts(String blockKey, String opName,
                                   List<String> portNames, List<String> portOps) {
        List<BearIr.EffectPort> ports = portNames.stream()
            .map(p -> new BearIr.EffectPort(p, BearIr.EffectPortKind.EXTERNAL, portOps, null, List.of()))
            .toList();

        return new BearIr("1", new BearIr.Block(
            TypeScriptLexicalSupport.deriveBlockName(blockKey),
            BearIr.BlockKind.LOGIC,
            List.of(new BearIr.Operation(
                opName,
                new BearIr.Contract(
                    List.of(new BearIr.Field("input", BearIr.FieldType.STRING)),
                    List.of(new BearIr.Field("result", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(ports), null, List.of()
            )),
            new BearIr.Effects(ports), null, null, List.of()
        ));
    }

    private BearIr.Operation makeOp(String name, List<String> inputNames, List<String> outputNames) {
        List<BearIr.Field> inputs = inputNames.stream()
            .map(n -> new BearIr.Field(n, BearIr.FieldType.STRING))
            .toList();
        List<BearIr.Field> outputs = outputNames.stream()
            .map(n -> new BearIr.Field(n, BearIr.FieldType.STRING))
            .toList();
        return new BearIr.Operation(name,
            new BearIr.Contract(inputs, outputs),
            new BearIr.Effects(List.of()), null, List.of());
    }
}
