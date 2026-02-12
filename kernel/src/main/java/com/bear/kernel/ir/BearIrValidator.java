package com.bear.kernel.ir;

import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BearIrValidator {
    public void validate(BearIr ir) {
        validateBlock(ir.block());
    }

    private void validateBlock(BearIr.Block block) {
        requireNonBlank(block.name(), "block.name");
        requireNonNull(block.kind(), "block.kind");

        validateContract(block.contract());
        validateEffects(block.effects());

        if (block.idempotency() != null) {
            validateIdempotency(block);
        }

        if (block.invariants() != null) {
            validateInvariants(block);
        }
    }

    private void validateContract(BearIr.Contract contract) {
        requireNonNull(contract, "block.contract");
        requireNonEmpty(contract.inputs(), "block.contract.inputs");
        requireNonEmpty(contract.outputs(), "block.contract.outputs");

        validateUniqueFieldNames(contract.inputs(), "block.contract.inputs");
        validateUniqueFieldNames(contract.outputs(), "block.contract.outputs");
    }

    private void validateUniqueFieldNames(List<BearIr.Field> fields, String path) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            BearIr.Field field = fields.get(i);
            String namePath = path + "[" + i + "].name";
            requireNonBlank(field.name(), namePath);
            requireNonNull(field.type(), path + "[" + i + "].type");
            if (!seen.add(field.name())) {
                throw semantic(namePath, BearIrValidationException.Code.DUPLICATE, "duplicate name: " + field.name());
            }
        }
    }

    private void validateEffects(BearIr.Effects effects) {
        requireNonNull(effects, "block.effects");
        requireNonNull(effects.allow(), "block.effects.allow");

        Set<String> seenPorts = new HashSet<>();
        for (int i = 0; i < effects.allow().size(); i++) {
            BearIr.EffectPort port = effects.allow().get(i);
            String portPath = "block.effects.allow[" + i + "]";
            requireNonBlank(port.port(), portPath + ".port");
            if (!seenPorts.add(port.port())) {
                throw semantic(portPath + ".port", BearIrValidationException.Code.DUPLICATE, "duplicate port: " + port.port());
            }

            requireNonNull(port.ops(), portPath + ".ops");
            Set<String> seenOps = new HashSet<>();
            for (int j = 0; j < port.ops().size(); j++) {
                String op = port.ops().get(j);
                String opPath = portPath + ".ops[" + j + "]";
                requireNonBlank(op, opPath);
                if (!seenOps.add(op)) {
                    throw semantic(opPath, BearIrValidationException.Code.DUPLICATE, "duplicate op: " + op);
                }
            }
        }
    }

    private void validateIdempotency(BearIr.Block block) {
        BearIr.Idempotency idempotency = block.idempotency();
        requireNonBlank(idempotency.key(), "block.idempotency.key");
        requireNonNull(idempotency.store(), "block.idempotency.store");
        requireNonBlank(idempotency.store().port(), "block.idempotency.store.port");
        requireNonBlank(idempotency.store().getOp(), "block.idempotency.store.getOp");
        requireNonBlank(idempotency.store().putOp(), "block.idempotency.store.putOp");

        Set<String> inputNames = new HashSet<>();
        for (BearIr.Field input : block.contract().inputs()) {
            inputNames.add(input.name());
        }
        if (!inputNames.contains(idempotency.key())) {
            throw semantic("block.idempotency.key", BearIrValidationException.Code.UNKNOWN_REFERENCE, "must reference an input field");
        }

        Map<String, Set<String>> portToOps = effectsMap(block.effects());
        if (!portToOps.containsKey(idempotency.store().port())) {
            throw semantic("block.idempotency.store.port", BearIrValidationException.Code.UNKNOWN_REFERENCE, "unknown port: " + idempotency.store().port());
        }
        Set<String> ops = portToOps.get(idempotency.store().port());
        if (!ops.contains(idempotency.store().getOp())) {
            throw semantic("block.idempotency.store.getOp", BearIrValidationException.Code.UNKNOWN_REFERENCE, "unknown op: " + idempotency.store().getOp());
        }
        if (!ops.contains(idempotency.store().putOp())) {
            throw semantic("block.idempotency.store.putOp", BearIrValidationException.Code.UNKNOWN_REFERENCE, "unknown op: " + idempotency.store().putOp());
        }
    }

    private void validateInvariants(BearIr.Block block) {
        Set<String> outputNames = new HashSet<>();
        for (BearIr.Field output : block.contract().outputs()) {
            outputNames.add(output.name());
        }

        for (int i = 0; i < block.invariants().size(); i++) {
            BearIr.Invariant invariant = block.invariants().get(i);
            String basePath = "block.invariants[" + i + "]";
            requireNonNull(invariant.kind(), basePath + ".kind");
            requireNonBlank(invariant.field(), basePath + ".field");
            if (!outputNames.contains(invariant.field())) {
                throw semantic(basePath + ".field", BearIrValidationException.Code.UNKNOWN_REFERENCE, "must reference an output field");
            }
        }
    }

    private Map<String, Set<String>> effectsMap(BearIr.Effects effects) {
        Map<String, Set<String>> map = new HashMap<>();
        for (BearIr.EffectPort port : effects.allow()) {
            map.put(port.port(), new HashSet<>(port.ops()));
        }
        return map;
    }

    private void requireNonNull(Object value, String path) {
        if (value == null) {
            throw semantic(path, BearIrValidationException.Code.INVALID_VALUE, "must not be null");
        }
    }

    private void requireNonEmpty(List<?> list, String path) {
        requireNonNull(list, path);
        if (list.isEmpty()) {
            throw semantic(path, BearIrValidationException.Code.INVALID_VALUE, "must be a non-empty list");
        }
    }

    private void requireNonBlank(String value, String path) {
        requireNonNull(value, path);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw semantic(path, BearIrValidationException.Code.INVALID_VALUE, "must be a non-blank trimmed string");
        }
    }

    private BearIrValidationException semantic(String path, BearIrValidationException.Code code, String message) {
        return new BearIrValidationException(BearIrValidationException.Category.SEMANTIC, path, code, message);
    }
}
