package com.bear.kernel.ir;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BearIrNormalizer {
    public BearIr normalize(BearIr ir) {
        BearIr.Block block = ir.block();

        List<BearIr.Field> inputs = sortFields(block.contract().inputs());
        List<BearIr.Field> outputs = sortFields(block.contract().outputs());
        BearIr.Contract contract = new BearIr.Contract(inputs, outputs);

        List<BearIr.EffectPort> ports = sortPorts(block.effects().allow());
        BearIr.Effects effects = new BearIr.Effects(ports);
        BearIr.Impl impl = sortImpl(block.impl());

        List<BearIr.Invariant> invariants = block.invariants();
        if (invariants != null) {
            invariants = sortInvariants(invariants);
            if (invariants.isEmpty()) {
                invariants = null;
            }
        }

        BearIr.Block normalizedBlock = new BearIr.Block(
            block.name(),
            block.kind(),
            contract,
            effects,
            impl,
            block.idempotency(),
            invariants
        );
        return new BearIr(ir.version(), normalizedBlock);
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

    private List<BearIr.Invariant> sortInvariants(List<BearIr.Invariant> invariants) {
        List<BearIr.Invariant> list = new ArrayList<>(invariants);
        list.sort(Comparator
            .comparing(BearIr.Invariant::kind)
            .thenComparing(BearIr.Invariant::field));
        return list;
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

