package com.bear.kernel;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.ir.BearIrValidator;
import com.bear.kernel.ir.BearIrYamlEmitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BearIrValidatorTest {
    @Test
    void rejectLegacyBlockContract(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSchemaError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.UNKNOWN_KEY, ex.code());
        assertEquals("block", ex.path());
    }

    @Test
    void rejectMissingOperations(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSchemaError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.MISSING_FIELD, ex.code());
        assertEquals("block.operations", ex.path());
    }

    @Test
    void rejectDuplicateOperationNames(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: Read\n"
            + "      contract:\n"
            + "        inputs: [{name: id, type: string}]\n"
            + "        outputs: [{name: value, type: string}]\n"
            + "      uses: {allow: []}\n"
            + "    - name: Read\n"
            + "      contract:\n"
            + "        inputs: [{name: id, type: string}]\n"
            + "        outputs: [{name: value, type: string}]\n"
            + "      uses: {allow: []}\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.DUPLICATE, ex.code());
        assertEquals("block.operations[1].name", ex.path());
    }

    @Test
    void perOperationFieldUniquenessIsIndependent(@TempDir Path tempDir) throws Exception {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: Wallet\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: GetA\n"
            + "      contract:\n"
            + "        inputs: [{name: id, type: string}]\n"
            + "        outputs: [{name: balance, type: int}]\n"
            + "      uses: {allow: []}\n"
            + "    - name: GetB\n"
            + "      contract:\n"
            + "        inputs: [{name: id, type: string}]\n"
            + "        outputs: [{name: balance, type: int}]\n"
            + "      uses: {allow: []}\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: walletStore\n"
            + "        ops: [get]\n";

        parseValidate(tempDir, yaml);
    }

    @Test
    void rejectUsesOutsideBlockEffects(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: Read\n"
            + "      contract:\n"
            + "        inputs: [{name: id, type: string}]\n"
            + "        outputs: [{name: value, type: string}]\n"
            + "      uses:\n"
            + "        allow:\n"
            + "          - port: store\n"
            + "            ops: [get]\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.UNKNOWN_REFERENCE, ex.code());
        assertEquals("block.operations[0].uses.allow[0].port", ex.path());
    }

    @Test
    void rejectOperationIdempotencyUseWithoutBlockStore(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: Write\n"
            + "      contract:\n"
            + "        inputs: [{name: txId, type: string}]\n"
            + "        outputs: [{name: ok, type: bool}]\n"
            + "      uses: {allow: []}\n"
            + "      idempotency:\n"
            + "        mode: use\n"
            + "        key: txId\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_VALUE, ex.code());
        assertEquals("block.operations[0].idempotency.mode", ex.path());
    }

    @Test
    void rejectIdempotencyUseWithoutStoreOpsInUses(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: Write\n"
            + "      contract:\n"
            + "        inputs: [{name: txId, type: string}]\n"
            + "        outputs: [{name: ok, type: bool}]\n"
            + "      uses:\n"
            + "        allow:\n"
            + "          - port: store\n"
            + "            ops: [save]\n"
            + "      idempotency:\n"
            + "        mode: use\n"
            + "        key: txId\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: store\n"
            + "        ops: [save]\n"
            + "      - port: idem\n"
            + "        ops: [get, put]\n"
            + "  idempotency:\n"
            + "    store:\n"
            + "      port: idem\n"
            + "      getOp: get\n"
            + "      putOp: put\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_VALUE, ex.code());
        assertEquals("block.operations[0].idempotency", ex.path());
    }

    @Test
    void rejectModeNoneWithKey(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: Write\n"
            + "      contract:\n"
            + "        inputs: [{name: txId, type: string}]\n"
            + "        outputs: [{name: ok, type: bool}]\n"
            + "      uses: {allow: []}\n"
            + "      idempotency:\n"
            + "        mode: none\n"
            + "        key: txId\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_VALUE, ex.code());
        assertEquals("block.operations[0].idempotency", ex.path());
    }

    @Test
    void rejectOperationInvariantOutsideBlockAllowedSet(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: Read\n"
            + "      contract:\n"
            + "        inputs: [{name: id, type: string}]\n"
            + "        outputs: [{name: balance, type: int}]\n"
            + "      uses: {allow: []}\n"
            + "      invariants:\n"
            + "        - kind: non_negative\n"
            + "          field: balance\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  invariants:\n"
            + "    - kind: equals\n"
            + "      field: balance\n"
            + "      params:\n"
            + "        value: \"1\"\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.UNKNOWN_REFERENCE, ex.code());
        assertEquals("block.operations[0].invariants[0]", ex.path());
    }

    @Test
    void rejectEmptyEffectsWhenAnyOperationIsNotEchoSafe(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: Read\n"
            + "      contract:\n"
            + "        inputs: [{name: id, type: string}]\n"
            + "        outputs: [{name: value, type: string}]\n"
            + "      uses: {allow: []}\n"
            + "    - name: Broken\n"
            + "      contract:\n"
            + "        inputs: [{name: id, type: string}]\n"
            + "        outputs: [{name: amount, type: int}]\n"
            + "      uses: {allow: []}\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_VALUE, ex.code());
        assertEquals("block.effects.allow", ex.path());
    }

    @Test
    void acceptWithdrawFixture() throws Exception {
        Path fixture = TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml");
        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
    }

    @Test
    void goldenCanonicalYamlForWithdrawFixture() throws IOException {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        Path golden = repoRoot.resolve("spec/golden/withdraw.canonical.yaml");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        BearIrYamlEmitter emitter = new BearIrYamlEmitter();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);

        String actual = normalizeLf(emitter.toCanonicalYaml(normalized));
        String expected = normalizeLf(Files.readString(golden));
        assertEquals(expected, actual);
    }

    private static BearIr parseValidate(Path tempDir, String yaml) throws Exception {
        Path file = tempDir.resolve("input.yaml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIr ir = parser.parse(file);
        validator.validate(ir);
        return ir;
    }

    private static BearIrValidationException assertSchemaError(Path tempDir, String yaml) throws IOException {
        Path file = tempDir.resolve("input.yaml");
        Files.writeString(file, yaml);
        BearIrParser parser = new BearIrParser();
        BearIrValidationException ex = assertThrows(BearIrValidationException.class, () -> parser.parse(file));
        assertEquals(BearIrValidationException.Category.SCHEMA, ex.category());
        return ex;
    }

    private static BearIrValidationException assertSemanticError(Path tempDir, String yaml) throws IOException {
        Path file = tempDir.resolve("input.yaml");
        Files.writeString(file, yaml);
        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIr ir = parser.parse(file);
        BearIrValidationException ex = assertThrows(BearIrValidationException.class, () -> validator.validate(ir));
        assertEquals(BearIrValidationException.Category.SEMANTIC, ex.category());
        return ex;
    }

    private static String normalizeLf(String text) {
        return text.replace("\r\n", "\n");
    }
}
