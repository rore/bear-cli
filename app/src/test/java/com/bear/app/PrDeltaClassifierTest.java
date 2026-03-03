package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrDeltaClassifierTest {
    @Test
    void operationAddIsBoundaryExpandingSurfaceDelta(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String head = base.replace(
            "  effects:",
            "    - name: GetShadow\n"
                + "      contract:\n"
                + "        inputs:\n"
                + "        - name: accountId\n"
                + "          type: string\n"
                + "        outputs:\n"
                + "        - name: balance\n"
                + "          type: decimal\n"
                + "      uses:\n"
                + "        allow:\n"
                + "        - port: ledger\n"
                + "          ops:\n"
                + "          - getBalance\n"
                + "  effects:"
        );

        List<PrDelta> deltas = PrDeltaClassifier.computePrDeltas(parseIr(tempDir, "base.yaml", base), parseIr(tempDir, "head.yaml", head));
        PrDelta delta = find(deltas, PrCategory.SURFACE, PrChange.ADDED, "op.GetShadow");
        assertEquals(PrClass.BOUNDARY_EXPANDING, delta.clazz());
    }

    @Test
    void blockIdempotencyAddUsesBlockScopedKey(@TempDir Path tempDir) throws Exception {
        String head = fixtureIr();
        String base = head.replace(
            "  idempotency:\n"
                + "    store:\n"
                + "      port: idempotency\n"
                + "      getOp: get\n"
                + "      putOp: put\n",
            ""
        ).replace(
            "      idempotency:\n"
                + "        mode: use\n"
                + "        key: txId\n",
            ""
        ).replace(
            "          - port: idempotency\n"
                + "            ops:\n"
                + "              - get\n"
                + "              - put\n",
            ""
        );

        List<PrDelta> deltas = PrDeltaClassifier.computePrDeltas(parseIr(tempDir, "base.yaml", base), parseIr(tempDir, "head.yaml", head));
        PrDelta delta = find(deltas, PrCategory.IDEMPOTENCY, PrChange.ADDED, "block.idempotency");
        assertEquals(PrClass.BOUNDARY_EXPANDING, delta.clazz());
    }

    @Test
    void operationUsesDeltaIsBoundaryExpanding(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String head = base.replace(
            "          - port: ledger\n"
                + "            ops:\n"
                + "              - getBalance\n"
                + "              - setBalance\n",
            "          - port: ledger\n"
                + "            ops:\n"
                + "              - getBalance\n"
                + "              - setBalance\n"
                + "              - reverse\n"
        ).replace(
            "      - port: ledger\n"
                + "        ops:\n"
                + "          - getBalance\n"
                + "          - setBalance\n",
            "      - port: ledger\n"
                + "        ops:\n"
                + "          - getBalance\n"
                + "          - setBalance\n"
                + "          - reverse\n"
        );

        List<PrDelta> deltas = PrDeltaClassifier.computePrDeltas(parseIr(tempDir, "base.yaml", base), parseIr(tempDir, "head.yaml", head));
        PrDelta delta = find(deltas, PrCategory.OPS, PrChange.ADDED, "op.ExecuteWithdraw:uses.ledger.reverse");
        assertEquals(PrClass.BOUNDARY_EXPANDING, delta.clazz());
    }

    @Test
    void operationContractDeltaUsesOperationPrefix(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String head = base.replace(
            "          - name: txId\n            type: string\n",
            "          - name: txId\n            type: string\n          - name: note\n            type: string\n"
        );

        List<PrDelta> deltas = PrDeltaClassifier.computePrDeltas(parseIr(tempDir, "base.yaml", base), parseIr(tempDir, "head.yaml", head));
        PrDelta delta = find(deltas, PrCategory.CONTRACT, PrChange.ADDED, "op.ExecuteWithdraw:input.note:string");
        assertEquals(PrClass.ORDINARY, delta.clazz());
    }

    private static PrDelta find(List<PrDelta> deltas, PrCategory category, PrChange change, String key) {
        return deltas.stream()
            .filter(d -> d.category() == category && d.change() == change && d.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new AssertionError("delta not found: " + category + " " + change + " " + key + " in " + deltas));
    }

    private static BearIr parseIr(Path tempDir, String name, String ir) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, ir, StandardCharsets.UTF_8);
        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIr parsed = parser.parse(file);
        validator.validate(parsed);
        return parsed;
    }

    private static String fixtureIr() throws Exception {
        return Files.readString(TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml"), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }
}
