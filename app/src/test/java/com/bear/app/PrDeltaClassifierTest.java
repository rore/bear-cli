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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        List<PrDelta> deltas = computePrDeltas(tempDir, base, head);
        PrDelta delta = find(deltas, PrCategory.SURFACE, PrChange.ADDED, "op.GetShadow");
        assertEquals(PrClass.BOUNDARY_EXPANDING, delta.clazz());
    }

    @Test
    void operationRemoveIsBoundaryExpandingSurfaceDelta(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr().replace(
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
        String head = fixtureIr();

        List<PrDelta> deltas = computePrDeltas(tempDir, base, head);
        PrDelta delta = find(deltas, PrCategory.SURFACE, PrChange.REMOVED, "op.GetShadow");
        assertEquals(PrClass.BOUNDARY_EXPANDING, delta.clazz());
    }

    @Test
    void blockIdempotencyAddUsesBlockScopedKey(@TempDir Path tempDir) throws Exception {
        String head = fixtureIr();
        String base = withoutBlockIdempotency(head);

        List<PrDelta> deltas = computePrDeltas(tempDir, base, head);
        PrDelta delta = find(deltas, PrCategory.IDEMPOTENCY, PrChange.ADDED, "block.idempotency");
        assertEquals(PrClass.BOUNDARY_EXPANDING, delta.clazz());
    }

    @Test
    void blockIdempotencyRemoveIsBoundaryExpanding(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String head = withoutBlockIdempotency(base);

        List<PrDelta> deltas = computePrDeltas(tempDir, base, head);
        PrDelta delta = find(deltas, PrCategory.IDEMPOTENCY, PrChange.REMOVED, "block.idempotency");
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

        List<PrDelta> deltas = computePrDeltas(tempDir, base, head);
        PrDelta delta = find(deltas, PrCategory.OPS, PrChange.ADDED, "op.ExecuteWithdraw:uses.ledger.reverse");
        assertEquals(PrClass.BOUNDARY_EXPANDING, delta.clazz());
    }

    @Test
    void operationUsesRemovalIsBoundaryExpanding(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String head = base.replace(
            "          - port: ledger\n"
                + "            ops:\n"
                + "              - getBalance\n"
                + "              - setBalance\n",
            "          - port: ledger\n"
                + "            ops:\n"
                + "              - setBalance\n"
        );

        List<PrDelta> deltas = computePrDeltas(tempDir, base, head);
        PrDelta delta = find(deltas, PrCategory.OPS, PrChange.REMOVED, "op.ExecuteWithdraw:uses.ledger.getBalance");
        assertEquals(PrClass.BOUNDARY_EXPANDING, delta.clazz());
    }

    @Test
    void operationIdempotencyChangeAndRemovalAreBoundaryExpanding(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String changed = base.replace("        key: txId\n", "        key: accountId\n");
        String removed = base.replace(
            "      idempotency:\n"
                + "        mode: use\n"
                + "        key: txId\n",
            ""
        );

        List<PrDelta> changedDeltas = computePrDeltas(tempDir, base, changed);
        List<PrDelta> removedDeltas = computePrDeltas(tempDir, base, removed);

        assertEquals(
            PrClass.BOUNDARY_EXPANDING,
            find(changedDeltas, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "op.ExecuteWithdraw:idempotency.key").clazz()
        );
        assertEquals(
            PrClass.BOUNDARY_EXPANDING,
            find(removedDeltas, PrCategory.IDEMPOTENCY, PrChange.REMOVED, "op.ExecuteWithdraw:idempotency").clazz()
        );
    }

    @Test
    void operationContractDeltaUsesOperationPrefix(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String head = base.replace(
            "          - name: txId\n            type: string\n",
            "          - name: txId\n            type: string\n          - name: note\n            type: string\n"
        );

        List<PrDelta> deltas = computePrDeltas(tempDir, base, head);
        PrDelta delta = find(deltas, PrCategory.CONTRACT, PrChange.ADDED, "op.ExecuteWithdraw:input.note:string");
        assertEquals(PrClass.ORDINARY, delta.clazz());
    }

    @Test
    void contractOutputAddRemovalAndTypeChangeUseGovernanceClasses(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String outputAdded = base.replace(
            "        outputs:\n"
                + "          - name: balance\n"
                + "            type: decimal\n",
            "        outputs:\n"
                + "          - name: balance\n"
                + "            type: decimal\n"
                + "          - name: receiptId\n"
                + "            type: string\n"
        );
        String inputRemoved = base.replace(
            "          - name: currency\n"
                + "            type: string\n",
            ""
        );
        String inputTypeChanged = base.replace(
            "          - name: amount\n"
                + "            type: decimal\n",
            "          - name: amount\n"
                + "            type: int\n"
        );

        List<PrDelta> outputAddedDeltas = computePrDeltas(tempDir, base, outputAdded);
        List<PrDelta> inputRemovedDeltas = computePrDeltas(tempDir, base, inputRemoved);
        List<PrDelta> inputTypeChangedDeltas = computePrDeltas(tempDir, base, inputTypeChanged);

        assertEquals(
            PrClass.BOUNDARY_EXPANDING,
            find(outputAddedDeltas, PrCategory.CONTRACT, PrChange.ADDED, "op.ExecuteWithdraw:output.receiptId:string").clazz()
        );
        assertEquals(
            PrClass.BOUNDARY_EXPANDING,
            find(inputRemovedDeltas, PrCategory.CONTRACT, PrChange.REMOVED, "op.ExecuteWithdraw:input.currency:string").clazz()
        );
        assertEquals(
            PrClass.BOUNDARY_EXPANDING,
            find(inputTypeChangedDeltas, PrCategory.CONTRACT, PrChange.CHANGED, "op.ExecuteWithdraw:input.amount:decimal->int").clazz()
        );
    }

    @Test
    void blockAndOperationInvariantDeltasFollowGovernancePolicy(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String blockInvariantAdded = base.replace(
            "  invariants:\n"
                + "    - kind: non_negative\n"
                + "      field: balance\n",
            "  invariants:\n"
                + "    - kind: non_negative\n"
                + "      field: balance\n"
                + "    - kind: equals\n"
                + "      field: balance\n"
                + "      params:\n"
                + "        value: \"0\"\n"
        );
        String blockInvariantRemoved = base.replace(
            "  invariants:\n"
                + "    - kind: non_negative\n"
                + "      field: balance\n",
            ""
        ).replace(
            "      invariants:\n"
                + "        - kind: non_negative\n"
                + "          field: balance\n",
            ""
        );
        String opInvariantAdded = base.replace(
            "  invariants:\n"
                + "    - kind: non_negative\n"
                + "      field: balance\n",
            "  invariants:\n"
                + "    - kind: non_negative\n"
                + "      field: balance\n"
                + "    - kind: equals\n"
                + "      field: balance\n"
                + "      params:\n"
                + "        value: \"0\"\n"
        ).replace(
            "      invariants:\n"
                + "        - kind: non_negative\n"
                + "          field: balance\n",
            "      invariants:\n"
                + "        - kind: non_negative\n"
                + "          field: balance\n"
                + "        - kind: equals\n"
                + "          field: balance\n"
                + "          params:\n"
                + "            value: \"0\"\n"
        );
        String opInvariantRemoved = base.replace(
            "      invariants:\n"
                + "        - kind: non_negative\n"
                + "          field: balance\n",
            ""
        );

        assertEquals(
            PrClass.ORDINARY,
            find(computePrDeltas(tempDir, base, blockInvariantAdded), PrCategory.INVARIANTS, PrChange.ADDED, "block.invariant:kind=equals|scope=result|field=balance|params=value=0").clazz()
        );
        assertEquals(
            PrClass.BOUNDARY_EXPANDING,
            find(
                computePrDeltas(tempDir, base, blockInvariantRemoved),
                PrCategory.INVARIANTS,
                PrChange.REMOVED,
                "block.invariant:kind=non_negative|scope=result|field=balance|params=none"
            ).clazz()
        );
        assertEquals(
            PrClass.BOUNDARY_EXPANDING,
            find(
                computePrDeltas(tempDir, base, opInvariantAdded),
                PrCategory.INVARIANTS,
                PrChange.ADDED,
                "op.ExecuteWithdraw:invariant.kind=equals|scope=result|field=balance|params=value=0"
            ).clazz()
        );
        assertEquals(
            PrClass.BOUNDARY_EXPANDING,
            find(
                computePrDeltas(tempDir, base, opInvariantRemoved),
                PrCategory.INVARIANTS,
                PrChange.REMOVED,
                "op.ExecuteWithdraw:invariant.kind=non_negative|scope=result|field=balance|params=none"
            ).clazz()
        );
    }

    @Test
    void allowedDepDeltasUseExpectedGovernanceClasses(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String added = withAllowedDeps(base, "com.zeta:dep@1.0.0");
        String changedBase = withAllowedDeps(base, "com.alpha:dep@1.0.0");
        String changedHead = withAllowedDeps(base, "com.alpha:dep@2.0.0");

        List<PrDelta> addedDeltas = computePrDeltas(tempDir, base, added);
        List<PrDelta> changedDeltas = computePrDeltas(tempDir, changedBase, changedHead);
        List<PrDelta> removedDeltas = computePrDeltas(tempDir, changedBase, base);

        assertEquals(
            PrClass.BOUNDARY_EXPANDING,
            find(addedDeltas, PrCategory.ALLOWED_DEPS, PrChange.ADDED, "com.zeta:dep@1.0.0").clazz()
        );
        assertEquals(
            PrClass.BOUNDARY_EXPANDING,
            find(changedDeltas, PrCategory.ALLOWED_DEPS, PrChange.CHANGED, "com.alpha:dep@1.0.0->2.0.0").clazz()
        );
        assertEquals(
            PrClass.ORDINARY,
            find(removedDeltas, PrCategory.ALLOWED_DEPS, PrChange.REMOVED, "com.alpha:dep@1.0.0").clazz()
        );
    }

    @Test
    void mixedDeltasFollowSemanticSortOrder(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String head = withAllowedDeps(base, "com.zeta:dep@1.0.0")
            .replace(
                "      - port: idempotency\n        ops:\n          - get\n          - put\n",
                "      - port: audit\n        ops:\n          - write\n      - port: idempotency\n        ops:\n          - get\n          - put\n"
            )
            .replace(
                "          - port: ledger\n"
                    + "            ops:\n"
                    + "              - getBalance\n"
                    + "              - setBalance\n",
                "          - port: ledger\n"
                    + "            ops:\n"
                    + "              - getBalance\n"
                    + "              - setBalance\n"
                    + "              - reverse\n"
            )
            .replace(
                "      - port: ledger\n"
                    + "        ops:\n"
                    + "          - getBalance\n"
                    + "          - setBalance\n",
                "      - port: ledger\n"
                    + "        ops:\n"
                    + "          - getBalance\n"
                    + "          - setBalance\n"
                    + "          - reverse\n"
            )
            .replace("        key: txId\n", "        key: accountId\n")
            .replace(
                "          - name: txId\n            type: string\n",
                "          - name: txId\n            type: string\n          - name: note\n            type: string\n"
            );

        List<PrDelta> deltas = computePrDeltas(tempDir, base, head);

        assertEquals(List.of(
            new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.PORTS, PrChange.ADDED, "audit"),
            new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.ALLOWED_DEPS, PrChange.ADDED, "com.zeta:dep@1.0.0"),
            new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.OPS, PrChange.ADDED, "ledger.reverse"),
            new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.OPS, PrChange.ADDED, "op.ExecuteWithdraw:uses.ledger.reverse"),
            new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "op.ExecuteWithdraw:idempotency.key"),
            new PrDelta(PrClass.ORDINARY, PrCategory.CONTRACT, PrChange.ADDED, "op.ExecuteWithdraw:input.note:string")
        ), deltas);
    }

    private static List<PrDelta> computePrDeltas(Path tempDir, String base, String head) throws Exception {
        return PrDeltaClassifier.computePrDeltas(parseIr(tempDir, "base.yaml", base), parseIr(tempDir, "head.yaml", head));
    }

    private static PrDelta find(List<PrDelta> deltas, PrCategory category, PrChange change, String key) {
        return deltas.stream()
            .filter(d -> d.category() == category && d.change() == change && d.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new AssertionError("delta not found: " + category + " " + change + " " + key + " in " + deltas));
    }

    private static String withoutBlockIdempotency(String ir) {
        return ir.replace(
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
    }

    private static String withAllowedDeps(String ir, String... gavs) {
        String allowedDeps = List.of(gavs).stream()
            .map(PrDeltaClassifierTest::toAllowedDepYaml)
            .collect(Collectors.joining());
        int blockInvariantIndex = ir.lastIndexOf("\n  invariants:\n");
        if (blockInvariantIndex < 0) {
            throw new IllegalArgumentException("block invariants anchor missing");
        }
        return ir.substring(0, blockInvariantIndex + 1)
            + "  impl:\n"
            + "    allowedDeps:\n"
            + allowedDeps
            + ir.substring(blockInvariantIndex + 1);
    }

    private static String toAllowedDepYaml(String gav) {
        String[] parts = gav.split("@", 2);
        return "      - maven: " + parts[0] + "\n"
            + "        version: " + parts[1] + "\n";
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
        return Files.readString(TestRepoPaths.repoRoot().resolve("bear-ir/fixtures/withdraw.bear.yaml"), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }
}
