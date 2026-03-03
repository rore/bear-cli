package com.bear.kernel.ir;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BearIrNormalizer {
    public BearIr normalize(BearIr ir) {
        BearIr.Block block = ir.block();

        List<BearIr.Operation> operations = normalizeOperations(block.operations());
        List<BearIr.EffectPort> ports = sortPorts(block.effects().allow());
        BearIr.Effects effects = new BearIr.Effects(ports);
        BearIr.Impl impl = sortImpl(block.impl());

        BearIr.BlockIdempotency idempotency = normalizeBlockIdempotency(block.idempotency());
        List<BearIr.Invariant> invariants = block.invariants();
        if (invariants != null) {
            invariants = normalizeInvariants(invariants);
            if (invariants.isEmpty()) {
                invariants = null;
            }
        }

        BearIr.Block normalizedBlock = new BearIr.Block(
            block.name(),
            block.kind(),
            operations,
            effects,
            impl,
            idempotency,
            invariants
        );
        return new BearIr(ir.version(), normalizedBlock);
    }

    private List<BearIr.Operation> normalizeOperations(List<BearIr.Operation> operations) {
        ArrayList<BearIr.Operation> normalized = new ArrayList<>();
        for (BearIr.Operation operation : operations) {
            BearIr.Contract contract = new BearIr.Contract(
                sortFields(operation.contract().inputs()),
                sortFields(operation.contract().outputs())
            );
            BearIr.Effects uses = new BearIr.Effects(sortPorts(operation.uses().allow()));
            BearIr.OperationIdempotency idempotency = normalizeOperationIdempotency(operation.idempotency());
            List<BearIr.Invariant> invariants = operation.invariants();
            if (invariants != null) {
                invariants = normalizeInvariants(invariants);
                if (invariants.isEmpty()) {
                    invariants = null;
                }
            }
            normalized.add(new BearIr.Operation(
                operation.name(),
                contract,
                uses,
                idempotency,
                invariants
            ));
        }
        normalized.sort(Comparator.comparing(BearIr.Operation::name));
        return List.copyOf(normalized);
    }

    private List<BearIr.Field> sortFields(List<BearIr.Field> fields) {
        List<BearIr.Field> list = new ArrayList<>(fields);
        list.sort(Comparator.comparing(BearIr.Field::name));
        return list;
    }

    private List<BearIr.EffectPort> sortPorts(List<BearIr.EffectPort> ports) {
        List<BearIr.EffectPort> list = new ArrayList<>();
        for (BearIr.EffectPort port : ports) {
            List<String> ops = new ArrayList<>(port.ops());
            ops.sort(String::compareTo);
            list.add(new BearIr.EffectPort(port.port(), ops));
        }
        list.sort(Comparator.comparing(BearIr.EffectPort::port));
        return list;
    }

    private List<BearIr.Invariant> normalizeInvariants(List<BearIr.Invariant> invariants) {
        ArrayList<BearIr.Invariant> list = new ArrayList<>();
        for (BearIr.Invariant invariant : invariants) {
            BearIr.InvariantParams params = invariant.params() == null
                ? new BearIr.InvariantParams(null, List.of())
                : new BearIr.InvariantParams(
                    invariant.params().value(),
                    invariant.params().values() == null ? List.of() : List.copyOf(invariant.params().values())
                );
            list.add(new BearIr.Invariant(invariant.kind(), invariant.scope(), invariant.field(), params));
        }
        list.sort(Comparator.comparing(InvariantFingerprint::canonicalKey));
        return List.copyOf(list);
    }

    private BearIr.BlockIdempotency normalizeBlockIdempotency(BearIr.BlockIdempotency idempotency) {
        if (idempotency == null) {
            return null;
        }
        return new BearIr.BlockIdempotency(idempotency.store());
    }

    private BearIr.OperationIdempotency normalizeOperationIdempotency(BearIr.OperationIdempotency idempotency) {
        if (idempotency == null) {
            return null;
        }
        List<String> keyFromInputs = idempotency.keyFromInputs() == null
            ? null
            : List.copyOf(idempotency.keyFromInputs());
        return new BearIr.OperationIdempotency(idempotency.mode(), idempotency.key(), keyFromInputs);
    }

    private BearIr.Impl sortImpl(BearIr.Impl impl) {
        if (impl == null || impl.allowedDeps() == null || impl.allowedDeps().isEmpty()) {
            return new BearIr.Impl(List.of());
        }
        List<BearIr.AllowedDep> sorted = new ArrayList<>(impl.allowedDeps());
        sorted.sort(Comparator.comparing(BearIr.AllowedDep::maven));
        return new BearIr.Impl(sorted);
    }
}

