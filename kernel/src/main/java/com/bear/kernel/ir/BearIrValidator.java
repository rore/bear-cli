package com.bear.kernel.ir;

import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class BearIrValidator {
    private static final Pattern MAVEN_COORDINATE = Pattern.compile("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+");

    public void validate(BearIr ir) {
        validateBlock(ir.block());
    }

    private void validateBlock(BearIr.Block block) {
        requireNonBlank(block.name(), "block.name");
        requireNonNull(block.kind(), "block.kind");

        validateContract(block.contract());
        validateEffects(block.effects());
        validateImpl(block.impl());

        if (block.idempotency() != null) {
            validateIdempotency(block);
        }

        if (block.invariants() != null) {
            validateInvariants(block);
        }

        validateEmptyEffectsPolicy(block);
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
        boolean hasKey = idempotency.key() != null;
        boolean hasKeyFromInputs = idempotency.keyFromInputs() != null;
        if (hasKey == hasKeyFromInputs) {
            throw semantic(
                "block.idempotency",
                BearIrValidationException.Code.INVALID_VALUE,
                "exactly one of key or keyFromInputs must be provided"
            );
        }
        if (hasKey) {
            requireNonBlank(idempotency.key(), "block.idempotency.key");
        }
        if (hasKeyFromInputs) {
            requireNonNull(idempotency.keyFromInputs(), "block.idempotency.keyFromInputs");
            if (idempotency.keyFromInputs().isEmpty()) {
                throw semantic("block.idempotency.keyFromInputs", BearIrValidationException.Code.INVALID_VALUE, "must be a non-empty list");
            }
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < idempotency.keyFromInputs().size(); i++) {
                String value = idempotency.keyFromInputs().get(i);
                String fieldPath = "block.idempotency.keyFromInputs[" + i + "]";
                requireNonBlank(value, fieldPath);
                if (!seen.add(value)) {
                    throw semantic(fieldPath, BearIrValidationException.Code.DUPLICATE, "duplicate key field: " + value);
                }
            }
        }
        requireNonNull(idempotency.store(), "block.idempotency.store");
        requireNonBlank(idempotency.store().port(), "block.idempotency.store.port");
        requireNonBlank(idempotency.store().getOp(), "block.idempotency.store.getOp");
        requireNonBlank(idempotency.store().putOp(), "block.idempotency.store.putOp");

        Set<String> inputNames = new HashSet<>();
        for (BearIr.Field input : block.contract().inputs()) {
            inputNames.add(input.name());
        }
        if (hasKey) {
            if (!inputNames.contains(idempotency.key())) {
                throw semantic("block.idempotency.key", BearIrValidationException.Code.UNKNOWN_REFERENCE, "must reference an input field");
            }
        } else {
            for (int i = 0; i < idempotency.keyFromInputs().size(); i++) {
                String field = idempotency.keyFromInputs().get(i);
                if (!inputNames.contains(field)) {
                    throw semantic(
                        "block.idempotency.keyFromInputs[" + i + "]",
                        BearIrValidationException.Code.UNKNOWN_REFERENCE,
                        "must reference an input field"
                    );
                }
            }
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
        Map<String, BearIr.FieldType> outputTypesByName = new HashMap<>();
        for (BearIr.Field output : block.contract().outputs()) {
            outputTypesByName.put(output.name(), output.type());
        }

        for (int i = 0; i < block.invariants().size(); i++) {
            BearIr.Invariant invariant = block.invariants().get(i);
            String basePath = "block.invariants[" + i + "]";
            requireNonNull(invariant.kind(), basePath + ".kind");
            requireNonNull(invariant.scope(), basePath + ".scope");
            requireNonBlank(invariant.field(), basePath + ".field");
            requireNonNull(invariant.params(), basePath + ".params");
            if (!outputTypesByName.containsKey(invariant.field())) {
                throw semantic(basePath + ".field", BearIrValidationException.Code.UNKNOWN_REFERENCE, "must reference an output field");
            }
            if (invariant.scope() != BearIr.InvariantScope.RESULT) {
                throw semantic(basePath + ".scope", BearIrValidationException.Code.INVALID_VALUE, "scope must be result");
            }

            BearIr.FieldType fieldType = outputTypesByName.get(invariant.field());
            switch (invariant.kind()) {
                case NON_NEGATIVE -> {
                    if (fieldType != BearIr.FieldType.INT && fieldType != BearIr.FieldType.DECIMAL) {
                        throw semantic(basePath + ".kind", BearIrValidationException.Code.INVALID_VALUE, "non_negative requires int or decimal output");
                    }
                    if (invariant.params().value() != null || !invariant.params().values().isEmpty()) {
                        throw semantic(basePath + ".params", BearIrValidationException.Code.INVALID_VALUE, "non_negative does not accept params");
                    }
                }
                case NON_EMPTY -> {
                    if (fieldType != BearIr.FieldType.STRING) {
                        throw semantic(basePath + ".kind", BearIrValidationException.Code.INVALID_VALUE, "non_empty requires string output");
                    }
                    if (invariant.params().value() != null || !invariant.params().values().isEmpty()) {
                        throw semantic(basePath + ".params", BearIrValidationException.Code.INVALID_VALUE, "non_empty does not accept params");
                    }
                }
                case EQUALS -> {
                    if (invariant.params().value() == null) {
                        throw semantic(basePath + ".params.value", BearIrValidationException.Code.INVALID_VALUE, "equals requires params.value");
                    }
                    if (!invariant.params().values().isEmpty()) {
                        throw semantic(basePath + ".params.values", BearIrValidationException.Code.INVALID_VALUE, "equals does not accept params.values");
                    }
                }
                case ONE_OF -> {
                    if (invariant.params().value() != null) {
                        throw semantic(basePath + ".params.value", BearIrValidationException.Code.INVALID_VALUE, "one_of does not accept params.value");
                    }
                    if (invariant.params().values().isEmpty()) {
                        throw semantic(basePath + ".params.values", BearIrValidationException.Code.INVALID_VALUE, "one_of requires non-empty params.values");
                    }
                    Set<String> seen = new LinkedHashSet<>();
                    for (int j = 0; j < invariant.params().values().size(); j++) {
                        String value = invariant.params().values().get(j);
                        String valuePath = basePath + ".params.values[" + j + "]";
                        requireNonNull(value, valuePath);
                        if (!seen.add(value)) {
                            throw semantic(valuePath, BearIrValidationException.Code.DUPLICATE, "duplicate params.values entry: " + value);
                        }
                    }
                }
            }
        }
    }

    private void validateImpl(BearIr.Impl impl) {
        requireNonNull(impl, "block.impl");
        requireNonNull(impl.allowedDeps(), "block.impl.allowedDeps");

        Set<String> seenGa = new HashSet<>();
        for (int i = 0; i < impl.allowedDeps().size(); i++) {
            BearIr.AllowedDep dep = impl.allowedDeps().get(i);
            String depPath = "block.impl.allowedDeps[" + i + "]";
            requireNonBlank(dep.maven(), depPath + ".maven");
            requireNonBlank(dep.version(), depPath + ".version");
            if (!MAVEN_COORDINATE.matcher(dep.maven()).matches()) {
                throw semantic(depPath + ".maven", BearIrValidationException.Code.INVALID_VALUE, "expected groupId:artifactId");
            }
            if (dep.maven().contains("*")) {
                throw semantic(depPath + ".maven", BearIrValidationException.Code.INVALID_VALUE, "wildcards are not allowed");
            }
            if (dep.version().contains("*") || dep.version().contains("[") || dep.version().contains("]")
                || dep.version().contains("(") || dep.version().contains(")") || dep.version().contains(",")
                || dep.version().contains("+")) {
                throw semantic(depPath + ".version", BearIrValidationException.Code.INVALID_VALUE, "version must be pinned");
            }
            if (!seenGa.add(dep.maven())) {
                throw semantic(depPath + ".maven", BearIrValidationException.Code.DUPLICATE, "duplicate allowed dep: " + dep.maven());
            }
        }
    }

    private void validateEmptyEffectsPolicy(BearIr.Block block) {
        if (block.effects().allow() == null || !block.effects().allow().isEmpty()) {
            return;
        }
        if (isEchoSafeEmptyEffectsBlock(block)) {
            return;
        }
        throw semantic(
            "block.effects.allow",
            BearIrValidationException.Code.INVALID_VALUE,
            "empty effects.allow requires echo-safe block (no idempotency, no invariants, outputs must mirror input name:type pairs)"
        );
    }

    private boolean isEchoSafeEmptyEffectsBlock(BearIr.Block block) {
        if (block.idempotency() != null) {
            return false;
        }
        if (block.invariants() != null && !block.invariants().isEmpty()) {
            return false;
        }
        TreeSet<String> inputTuples = canonicalFieldTuples(block.contract().inputs());
        TreeSet<String> outputTuples = canonicalFieldTuples(block.contract().outputs());
        return inputTuples.containsAll(outputTuples);
    }

    private TreeSet<String> canonicalFieldTuples(List<BearIr.Field> fields) {
        TreeSet<String> tuples = new TreeSet<>();
        for (BearIr.Field field : fields) {
            tuples.add(field.name() + ":" + field.type().name());
        }
        return tuples;
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

