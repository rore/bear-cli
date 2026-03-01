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
            + "version: v1\n"
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
            + "version: v0\n"
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
            + "version: v1\n"
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
            + "version: v1\n"
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
            + "version: v1\n"
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
        String yaml = "- version: v1\n";

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
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "---\n"
            + "version: v1\n";
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
            + "version: v1\n"
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
            + "version: v1\n"
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
            + "version: v1\n"
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
            + "version: v1\n"
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
            + "version: v1\n"
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
            + "version: v1\n"
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
            + "version: v1\n"
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
            + "version: v1\n"
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
            + "version: v1\n"
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
    void acceptIdempotencyKeyFromInputsOnly(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: accountId, type: string}, {name: requestId, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: idempotency\n"
            + "        ops: [get, put]\n"
            + "  idempotency:\n"
            + "    keyFromInputs: [accountId, requestId]\n"
            + "    store:\n"
            + "      port: idempotency\n"
            + "      getOp: get\n"
            + "      putOp: put\n";

        Path file = tempDir.resolve("input.yaml");
        Files.writeString(file, yaml);
        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIr ir = parser.parse(file);
        validator.validate(ir);
    }

    @Test
    void rejectIdempotencyWithBothKeyAndKeyFromInputs(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: accountId, type: string}, {name: requestId, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: idempotency\n"
            + "        ops: [get, put]\n"
            + "  idempotency:\n"
            + "    key: requestId\n"
            + "    keyFromInputs: [accountId, requestId]\n"
            + "    store:\n"
            + "      port: idempotency\n"
            + "      getOp: get\n"
            + "      putOp: put\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_VALUE, ex.code());
        assertEquals("block.idempotency", ex.path());
    }

    @Test
    void rejectIdempotencyWithNeitherKeyNorKeyFromInputs(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: requestId, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: idempotency\n"
            + "        ops: [get, put]\n"
            + "  idempotency:\n"
            + "    store:\n"
            + "      port: idempotency\n"
            + "      getOp: get\n"
            + "      putOp: put\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_VALUE, ex.code());
        assertEquals("block.idempotency", ex.path());
    }

    @Test
    void rejectIdempotencyUnknownKeyFromInputsReference(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: accountId, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: idempotency\n"
            + "        ops: [get, put]\n"
            + "  idempotency:\n"
            + "    keyFromInputs: [accountId, requestId]\n"
            + "    store:\n"
            + "      port: idempotency\n"
            + "      getOp: get\n"
            + "      putOp: put\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.UNKNOWN_REFERENCE, ex.code());
        assertEquals("block.idempotency.keyFromInputs[1]", ex.path());
    }

    @Test
    void rejectIdempotencyDuplicateKeyFromInputs(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: requestId, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: idempotency\n"
            + "        ops: [get, put]\n"
            + "  idempotency:\n"
            + "    keyFromInputs: [requestId, requestId]\n"
            + "    store:\n"
            + "      port: idempotency\n"
            + "      getOp: get\n"
            + "      putOp: put\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.DUPLICATE, ex.code());
        assertEquals("block.idempotency.keyFromInputs[1]", ex.path());
    }

    @Test
    void rejectUnknownInvariantFieldReference(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
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
    void rejectInvariantTypeMismatchForNonEmpty(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: count, type: int}]\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  invariants:\n"
            + "    - kind: non_empty\n"
            + "      field: count\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_VALUE, ex.code());
        assertEquals("block.invariants[0].kind", ex.path());
    }

    @Test
    void rejectUnknownInvariantKind(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  invariants:\n"
            + "    - kind: unknown_kind\n"
            + "      field: o\n";

        BearIrValidationException ex = assertSchemaError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_ENUM, ex.code());
        assertEquals("block.invariants[0].kind", ex.path());
    }

    @Test
    void acceptEchoSafeEmptyEffectsWithOrderIndependentTupleMatching(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: Echo\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs:\n"
            + "      - name: b\n"
            + "        type: int\n"
            + "      - name: a\n"
            + "        type: string\n"
            + "    outputs:\n"
            + "      - name: a\n"
            + "        type: string\n"
            + "      - name: b\n"
            + "        type: int\n"
            + "  effects:\n"
            + "    allow: []\n";

        Path file = tempDir.resolve("echo.yaml");
        Files.writeString(file, yaml);
        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIr ir = parser.parse(file);
        validator.validate(ir);
    }

    @Test
    void rejectEmptyEffectsWhenOutputDoesNotMirrorInputTuple(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: NonEcho\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: requestId, type: string}]\n"
            + "    outputs: [{name: balanceCents, type: int}]\n"
            + "  effects:\n"
            + "    allow: []\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_VALUE, ex.code());
        assertEquals("block.effects.allow", ex.path());
    }

    @Test
    void rejectEmptyEffectsWhenInvariantsPresentEvenIfOutputMirrorsInput(@TempDir Path tempDir) throws IOException {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: EchoInvariant\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: status, type: string}]\n"
            + "    outputs: [{name: status, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  invariants:\n"
            + "    - kind: non_empty\n"
            + "      field: status\n";

        BearIrValidationException ex = assertSemanticError(tempDir, yaml);
        assertEquals(BearIrValidationException.Code.INVALID_VALUE, ex.code());
        assertEquals("block.effects.allow", ex.path());
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

