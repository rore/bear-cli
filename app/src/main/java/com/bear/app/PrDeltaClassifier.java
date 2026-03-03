package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.InvariantFingerprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

final class PrDeltaClassifier {
    private PrDeltaClassifier() {
    }

    static List<BoundarySignal> computeBoundarySignals(BoundaryManifest baseline, BoundaryManifest candidate) {
        List<BoundarySignal> signals = new ArrayList<>();
        for (String capability : candidate.capabilities().keySet()) {
            if (!baseline.capabilities().containsKey(capability)) {
                signals.add(new BoundarySignal(BoundaryType.CAPABILITY_ADDED, capability));
            }
        }
        for (Map.Entry<String, String> dep : candidate.allowedDeps().entrySet()) {
            String ga = dep.getKey();
            if (!baseline.allowedDeps().containsKey(ga)) {
                signals.add(new BoundarySignal(BoundaryType.PURE_DEP_ADDED, ga + "@" + dep.getValue()));
                continue;
            }
            String oldVersion = baseline.allowedDeps().get(ga);
            if (!oldVersion.equals(dep.getValue())) {
                signals.add(new BoundarySignal(BoundaryType.PURE_DEP_VERSION_CHANGED, ga + "@" + oldVersion + "->" + dep.getValue()));
            }
        }
        for (Map.Entry<String, TreeSet<String>> entry : candidate.capabilities().entrySet()) {
            String capability = entry.getKey();
            if (!baseline.capabilities().containsKey(capability)) {
                continue;
            }
            TreeSet<String> baselineOps = baseline.capabilities().get(capability);
            for (String op : entry.getValue()) {
                if (!baselineOps.contains(op)) {
                    signals.add(new BoundarySignal(BoundaryType.CAPABILITY_OP_ADDED, capability + "." + op));
                }
            }
        }
        for (String invariant : baseline.invariants()) {
            if (!candidate.invariants().contains(invariant)) {
                signals.add(new BoundarySignal(BoundaryType.INVARIANT_RELAXED, invariant));
            }
        }
        signals.sort(Comparator
            .comparing((BoundarySignal signal) -> signal.type().order)
            .thenComparing(BoundarySignal::key));
        return signals;
    }

    static List<PrDelta> computePrDeltas(BearIr baseIr, BearIr headIr) {
        PrSurface base = baseIr == null ? emptyPrSurface() : toPrSurface(baseIr);
        PrSurface head = toPrSurface(headIr);

        List<PrDelta> deltas = new ArrayList<>();
        addBlockEffectDeltas(deltas, base, head);
        addBlockIdempotencyDeltas(deltas, base.blockIdempotency(), head.blockIdempotency());
        addAllowedDepDeltas(deltas, base.allowedDeps(), head.allowedDeps());
        addBlockInvariantDeltas(deltas, base.blockInvariants(), head.blockInvariants());
        addOperationSurfaceDeltas(deltas, base.operations(), head.operations());
        addOperationDeltas(deltas, base, head);

        deltas.sort(Comparator
            .comparing((PrDelta delta) -> delta.clazz().order)
            .thenComparing(delta -> delta.category().order)
            .thenComparing(delta -> delta.change().order)
            .thenComparing(PrDelta::key));
        return deltas;
    }

    private static void addBlockEffectDeltas(List<PrDelta> deltas, PrSurface base, PrSurface head) {
        for (String port : head.ports()) {
            if (!base.ports().contains(port)) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.PORTS, PrChange.ADDED, port));
            }
        }
        for (String port : base.ports()) {
            if (!head.ports().contains(port)) {
                deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.PORTS, PrChange.REMOVED, port));
            }
        }

        TreeSet<String> commonPorts = new TreeSet<>(head.ports());
        commonPorts.retainAll(base.ports());
        for (String port : commonPorts) {
            TreeSet<String> headOps = head.opsByPort().getOrDefault(port, new TreeSet<>());
            TreeSet<String> baseOps = base.opsByPort().getOrDefault(port, new TreeSet<>());
            for (String op : headOps) {
                if (!baseOps.contains(op)) {
                    deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.OPS, PrChange.ADDED, port + "." + op));
                }
            }
            for (String op : baseOps) {
                if (!headOps.contains(op)) {
                    deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.OPS, PrChange.REMOVED, port + "." + op));
                }
            }
        }
    }

    static void addAllowedDepDeltas(List<PrDelta> deltas, Map<String, String> base, Map<String, String> head) {
        TreeSet<String> names = new TreeSet<>();
        names.addAll(base.keySet());
        names.addAll(head.keySet());
        for (String ga : names) {
            boolean inBase = base.containsKey(ga);
            boolean inHead = head.containsKey(ga);
            if (!inBase) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.ALLOWED_DEPS, PrChange.ADDED, ga + "@" + head.get(ga)));
                continue;
            }
            if (!inHead) {
                deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.ALLOWED_DEPS, PrChange.REMOVED, ga + "@" + base.get(ga)));
                continue;
            }
            if (!base.get(ga).equals(head.get(ga))) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.ALLOWED_DEPS,
                    PrChange.CHANGED,
                    ga + "@" + base.get(ga) + "->" + head.get(ga)
                ));
            }
        }
    }

    private static void addBlockIdempotencyDeltas(
        List<PrDelta> deltas,
        BearIr.BlockIdempotency base,
        BearIr.BlockIdempotency head
    ) {
        if (base == null && head == null) {
            return;
        }
        if (base == null) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.ADDED, "block.idempotency"));
            return;
        }
        if (head == null) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.REMOVED, "block.idempotency"));
            return;
        }
        if (!base.store().port().equals(head.store().port())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "block.idempotency.store.port"));
        }
        if (!base.store().getOp().equals(head.store().getOp())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "block.idempotency.store.getOp"));
        }
        if (!base.store().putOp().equals(head.store().putOp())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "block.idempotency.store.putOp"));
        }
    }

    private static void addBlockInvariantDeltas(List<PrDelta> deltas, TreeSet<String> base, TreeSet<String> head) {
        for (String invariant : head) {
            if (!base.contains(invariant)) {
                deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.INVARIANTS, PrChange.ADDED, "block.invariant:" + invariant));
            }
        }
        for (String invariant : base) {
            if (!head.contains(invariant)) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.INVARIANTS, PrChange.REMOVED, "block.invariant:" + invariant));
            }
        }
    }

    private static void addOperationSurfaceDeltas(List<PrDelta> deltas, TreeSet<String> base, TreeSet<String> head) {
        for (String operation : head) {
            if (!base.contains(operation)) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.SURFACE, PrChange.ADDED, "op." + operation));
            }
        }
        for (String operation : base) {
            if (!head.contains(operation)) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.SURFACE, PrChange.REMOVED, "op." + operation));
            }
        }
    }

    private static void addOperationDeltas(List<PrDelta> deltas, PrSurface base, PrSurface head) {
        TreeSet<String> commonOperations = new TreeSet<>(head.operations());
        commonOperations.retainAll(base.operations());

        for (String op : commonOperations) {
            addOperationUsesDeltas(
                deltas,
                op,
                base.usesByOperation().getOrDefault(op, new TreeSet<>()),
                head.usesByOperation().getOrDefault(op, new TreeSet<>())
            );
            addOperationIdempotencyDeltas(
                deltas,
                op,
                base.idempotencyByOperation().get(op),
                head.idempotencyByOperation().get(op)
            );
            addOperationContractDeltas(
                deltas,
                op,
                base.inputsByOperation().getOrDefault(op, Map.of()),
                head.inputsByOperation().getOrDefault(op, Map.of()),
                true
            );
            addOperationContractDeltas(
                deltas,
                op,
                base.outputsByOperation().getOrDefault(op, Map.of()),
                head.outputsByOperation().getOrDefault(op, Map.of()),
                false
            );
            addOperationInvariantDeltas(
                deltas,
                op,
                base.invariantsByOperation().getOrDefault(op, new TreeSet<>()),
                head.invariantsByOperation().getOrDefault(op, new TreeSet<>())
            );
        }
    }

    private static void addOperationUsesDeltas(List<PrDelta> deltas, String operation, TreeSet<String> base, TreeSet<String> head) {
        for (String token : head) {
            if (!base.contains(token)) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.OPS,
                    PrChange.ADDED,
                    "op." + operation + ":uses." + token
                ));
            }
        }
        for (String token : base) {
            if (!head.contains(token)) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.OPS,
                    PrChange.REMOVED,
                    "op." + operation + ":uses." + token
                ));
            }
        }
    }

    private static void addOperationIdempotencyDeltas(
        List<PrDelta> deltas,
        String operation,
        BearIr.OperationIdempotency base,
        BearIr.OperationIdempotency head
    ) {
        if (base == null && head == null) {
            return;
        }
        if (base == null) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.ADDED, "op." + operation + ":idempotency"));
            return;
        }
        if (head == null) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.REMOVED, "op." + operation + ":idempotency"));
            return;
        }
        if (base.mode() != head.mode()) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "op." + operation + ":idempotency.mode"));
        }
        if (!Objects.equals(base.key(), head.key())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "op." + operation + ":idempotency.key"));
        }
        if (!Objects.equals(base.keyFromInputs(), head.keyFromInputs())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "op." + operation + ":idempotency.keyFromInputs"));
        }
    }

    private static void addOperationContractDeltas(
        List<PrDelta> deltas,
        String operation,
        Map<String, BearIr.FieldType> base,
        Map<String, BearIr.FieldType> head,
        boolean input
    ) {
        TreeSet<String> names = new TreeSet<>();
        names.addAll(base.keySet());
        names.addAll(head.keySet());
        for (String name : names) {
            boolean inBase = base.containsKey(name);
            boolean inHead = head.containsKey(name);
            String prefix = input ? "input." : "output.";
            String opPrefix = "op." + operation + ":" + prefix;

            if (!inBase) {
                PrClass clazz = input ? PrClass.ORDINARY : PrClass.BOUNDARY_EXPANDING;
                deltas.add(new PrDelta(
                    clazz,
                    PrCategory.CONTRACT,
                    PrChange.ADDED,
                    opPrefix + name + ":" + typeToken(head.get(name))
                ));
                continue;
            }
            if (!inHead) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.CONTRACT,
                    PrChange.REMOVED,
                    opPrefix + name + ":" + typeToken(base.get(name))
                ));
                continue;
            }
            if (base.get(name) != head.get(name)) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.CONTRACT,
                    PrChange.CHANGED,
                    opPrefix + name + ":" + typeToken(base.get(name)) + "->" + typeToken(head.get(name))
                ));
            }
        }
    }

    private static void addOperationInvariantDeltas(
        List<PrDelta> deltas,
        String operation,
        TreeSet<String> base,
        TreeSet<String> head
    ) {
        for (String invariant : head) {
            if (!base.contains(invariant)) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.INVARIANTS,
                    PrChange.ADDED,
                    "op." + operation + ":invariant." + invariant
                ));
            }
        }
        for (String invariant : base) {
            if (!head.contains(invariant)) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.INVARIANTS,
                    PrChange.REMOVED,
                    "op." + operation + ":invariant." + invariant
                ));
            }
        }
    }

    static String typeToken(BearIr.FieldType type) {
        return type.name().toLowerCase();
    }

    static PrSurface toPrSurface(BearIr ir) {
        TreeSet<String> ports = new TreeSet<>();
        Map<String, TreeSet<String>> opsByPort = new TreeMap<>();
        for (BearIr.EffectPort port : ir.block().effects().allow()) {
            ports.add(port.port());
            opsByPort.put(port.port(), new TreeSet<>(port.ops()));
        }
        Map<String, String> allowedDeps = new TreeMap<>();
        if (ir.block().impl() != null && ir.block().impl().allowedDeps() != null) {
            for (BearIr.AllowedDep dep : ir.block().impl().allowedDeps()) {
                allowedDeps.put(dep.maven(), dep.version());
            }
        }

        TreeSet<String> blockInvariants = new TreeSet<>();
        if (ir.block().invariants() != null) {
            for (BearIr.Invariant invariant : ir.block().invariants()) {
                blockInvariants.add(InvariantFingerprint.canonicalKey(invariant));
            }
        }

        TreeSet<String> operations = new TreeSet<>();
        Map<String, Map<String, BearIr.FieldType>> inputsByOperation = new TreeMap<>();
        Map<String, Map<String, BearIr.FieldType>> outputsByOperation = new TreeMap<>();
        Map<String, TreeSet<String>> usesByOperation = new TreeMap<>();
        Map<String, BearIr.OperationIdempotency> idempotencyByOperation = new TreeMap<>();
        Map<String, TreeSet<String>> invariantsByOperation = new TreeMap<>();

        for (BearIr.Operation operation : ir.block().operations()) {
            operations.add(operation.name());
            inputsByOperation.put(operation.name(), toFieldTypeMap(operation.contract().inputs()));
            outputsByOperation.put(operation.name(), toFieldTypeMap(operation.contract().outputs()));
            usesByOperation.put(operation.name(), toUsesTokens(operation.uses()));
            if (operation.idempotency() != null) {
                idempotencyByOperation.put(operation.name(), operation.idempotency());
            }
            TreeSet<String> opInvariants = new TreeSet<>();
            if (operation.invariants() != null) {
                for (BearIr.Invariant invariant : operation.invariants()) {
                    opInvariants.add(InvariantFingerprint.canonicalKey(invariant));
                }
            }
            invariantsByOperation.put(operation.name(), opInvariants);
        }

        return new PrSurface(
            ports,
            opsByPort,
            allowedDeps,
            ir.block().idempotency(),
            blockInvariants,
            operations,
            inputsByOperation,
            outputsByOperation,
            usesByOperation,
            idempotencyByOperation,
            invariantsByOperation
        );
    }

    private static Map<String, BearIr.FieldType> toFieldTypeMap(List<BearIr.Field> fields) {
        TreeMap<String, BearIr.FieldType> map = new TreeMap<>();
        for (BearIr.Field field : fields) {
            map.put(field.name(), field.type());
        }
        return map;
    }

    private static TreeSet<String> toUsesTokens(BearIr.Effects uses) {
        TreeSet<String> tokens = new TreeSet<>();
        for (BearIr.EffectPort port : uses.allow()) {
            for (String op : port.ops()) {
                tokens.add(port.port() + "." + op);
            }
        }
        return tokens;
    }

    static PrSurface emptyPrSurface() {
        return new PrSurface(
            new TreeSet<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            null,
            new TreeSet<>(),
            new TreeSet<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            new TreeMap<>()
        );
    }
}
