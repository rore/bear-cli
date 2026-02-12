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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BearIrValidatorTest {
    @Test
    void rejectMissingVersion(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSchemaError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.MISSING_FIELD, ex.code());
        assertEquals("version", ex.path());
    }

    @Test
    void rejectUnknownRootKey(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "extra: true\n"
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
        assertEquals("root", ex.path());
    }

    @Test
    void rejectInvalidVersion(@TempDir Path tempDir) throws IOException {
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
        assertEquals(BearIrValidationException.Code.INVALID_ENUM, ex.code());
        assertEquals("version", ex.path());
    }

    @Test
    void rejectInvalidBlockKind(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: capability\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSchemaError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_ENUM, ex.code());
        assertEquals("block.kind", ex.path());
    }

    @Test
    void rejectUnknownKeyInFieldObject(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs:\n"
            + "      - name: i\n"
            + "        type: string\n"
            + "        extra: x\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSchemaError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.UNKNOWN_KEY, ex.code());
        assertEquals("block.contract.inputs[0]", ex.path());
    }

    @Test
    void rejectInvalidFieldTypeEnum(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: float}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSchemaError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_ENUM, ex.code());
        assertEquals("block.contract.inputs[0].type", ex.path());
    }

    @Test
    void rejectInvalidRootType(@TempDir Path tempDir) throws IOException {
        String yaml = "- version: v0\n";

        BearIrValidationException ex = assertSchemaError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_TYPE, ex.code());
        assertEquals("root", ex.path());
    }

    @Test
    void rejectInvalidYaml(@TempDir Path tempDir) throws IOException {
        String yaml = "version: [\n";

        BearIrValidationException ex = assertSchemaError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_YAML, ex.code());
        assertEquals("root", ex.path());
    }

    @Test
    void rejectMultiDocumentYaml(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "---\n"
            + "version: v0\n";
        Path file = tempDir.resolve("multi.yaml");
        Files.writeString(file, yaml);

        BearIrParser parser = new BearIrParser();
        BearIrValidationException ex = assertThrows(BearIrValidationException.class, () -> parser.parse(file));
        assertEquals(BearIrValidationException.Category.SCHEMA, ex.category());
        assertEquals(BearIrValidationException.Code.MULTI_DOCUMENT, ex.code());
        assertEquals("root", ex.path());
    }

    @Test
    void rejectEmptyInputs(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: []\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_VALUE, ex.code());
        assertEquals("block.contract.inputs", ex.path());
    }

    @Test
    void rejectEmptyOutputs(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: []\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_VALUE, ex.code());
        assertEquals("block.contract.outputs", ex.path());
    }

    @Test
    void rejectDuplicateInputNames(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs:\n"
            + "      - name: i\n"
            + "        type: string\n"
            + "      - name: i\n"
            + "        type: string\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.DUPLICATE, ex.code());
        assertEquals("block.contract.inputs[1].name", ex.path());
    }

    @Test
    void rejectDuplicateOutputNames(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs:\n"
            + "      - name: o\n"
            + "        type: string\n"
            + "      - name: o\n"
            + "        type: string\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.DUPLICATE, ex.code());
        assertEquals("block.contract.outputs[1].name", ex.path());
    }

    @Test
    void rejectDuplicatePorts(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: ledger\n"
            + "        ops: [get]\n"
            + "      - port: ledger\n"
            + "        ops: [put]\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.DUPLICATE, ex.code());
        assertEquals("block.effects.allow[1].port", ex.path());
    }

    @Test
    void rejectDuplicateOps(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: ledger\n"
            + "        ops: [get, get]\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.DUPLICATE, ex.code());
        assertEquals("block.effects.allow[0].ops[1]", ex.path());
    }

    @Test
    void rejectUnknownIdempotencyKeyReference(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: txId, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: idempotency\n"
            + "        ops: [get, put]\n"
            + "  idempotency:\n"
            + "    key: other\n"
            + "    store:\n"
            + "      port: idempotency\n"
            + "      getOp: get\n"
            + "      putOp: put\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.UNKNOWN_REFERENCE, ex.code());
        assertEquals("block.idempotency.key", ex.path());
    }

    @Test
    void rejectUnknownIdempotencyPortReference(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: txId, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: idempotency\n"
            + "        ops: [get, put]\n"
            + "  idempotency:\n"
            + "    key: txId\n"
            + "    store:\n"
            + "      port: missing\n"
            + "      getOp: get\n"
            + "      putOp: put\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.UNKNOWN_REFERENCE, ex.code());
        assertEquals("block.idempotency.store.port", ex.path());
    }

    @Test
    void rejectUnknownIdempotencyOpReference(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: txId, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: idempotency\n"
            + "        ops: [get, put]\n"
            + "  idempotency:\n"
            + "    key: txId\n"
            + "    store:\n"
            + "      port: idempotency\n"
            + "      getOp: missing\n"
            + "      putOp: put\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.UNKNOWN_REFERENCE, ex.code());
        assertEquals("block.idempotency.store.getOp", ex.path());
    }

    @Test
    void rejectUnknownInvariantFieldReference(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: balance, type: decimal}]\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  invariants:\n"
            + "    - kind: non_negative\n"
            + "      field: other\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.UNKNOWN_REFERENCE, ex.code());
        assertEquals("block.invariants[0].field", ex.path());
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

    private static String normalizeLf(String text) {
        return text.replace("\r\n", "\n");
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
}
