package com.bear.kernel.ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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

        validateEffects(block.effects(), "block.effects");
        validateImpl(block.impl());

        List<OperationShape> operationShapes = validateOperations(block.operations());
        validateOperationUsesWithinBlockEffects(block.effects(), operationShapes);

        if (block.idempotency() != null) {
            validateBlockIdempotencyStore(block.idempotency(), effectsMap(block.effects()), "block.idempotency");
        }
        validateOperationIdempotency(block, operationShapes);

        validateBlockInvariants(block.invariants());
        validateOperationInvariants(block, operationShapes);

        validateEmptyEffectsPolicy(block, operationShapes);
    }

    private List<OperationShape> validateOperations(List<BearIr.Operation> operations) {
        requireNonEmpty(operations, "block.operations");
        HashSet<String> seenNames = new HashSet<>();
        ArrayList<OperationShape> shapes = new ArrayList<>();
        for (int i = 0; i < operations.size(); i++) {
            BearIr.Operation operation = operations.get(i);
            String operationPath = "block.operations[" + i + "]";
            requireNonNull(operation, operationPath);
            requireNonBlank(operation.name(), operationPath + ".name");
            if (!seenNames.add(operation.name())) {
                throw semantic(
                    operationPath + ".name",
                    BearIrValidationException.Code.DUPLICATE,
                    "duplicate operation name: " + operation.name()
                );
            }
            validateContract(operation.contract(), operationPath + ".contract");
            validateEffects(operation.uses(), operationPath + ".uses");

            TreeMap<String, BearIr.FieldType> inputTypes = toFieldTypeMap(operation.contract().inputs());
            TreeMap<String, BearIr.FieldType> outputTypes = toFieldTypeMap(operation.contract().outputs());
            shapes.add(new OperationShape(i, operation, operationPath, inputTypes, outputTypes));
        }
        return List.copyOf(shapes);
    }

    private void validateContract(BearIr.Contract contract, String path) {
        requireNonNull(contract, path);
        requireNonEmpty(contract.inputs(), path + ".inputs");
        requireNonEmpty(contract.outputs(), path + ".outputs");
        validateUniqueFieldNames(contract.inputs(), path + ".inputs");
        validateUniqueFieldNames(contract.outputs(), path + ".outputs");
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

    private TreeMap<String, BearIr.FieldType> toFieldTypeMap(List<BearIr.Field> fields) {
        TreeMap<String, BearIr.FieldType> map = new TreeMap<>();
        for (BearIr.Field field : fields) {
            map.put(field.name(), field.type());
        }
        return map;
    }

    private void validateEffects(BearIr.Effects effects, String path) {
        requireNonNull(effects, path);
        requireNonNull(effects.allow(), path + ".allow");

        Set<String> seenPorts = new HashSet<>();
        for (int i = 0; i < effects.allow().size(); i++) {
            BearIr.EffectPort port = effects.allow().get(i);
            String portPath = path + ".allow[" + i + "]";
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

    private void validateOperationUsesWithinBlockEffects(BearIr.Effects blockEffects, List<OperationShape> operationShapes) {
        Map<String, Set<String>> allowed = effectsMap(blockEffects);
        for (OperationShape shape : operationShapes) {
            List<BearIr.EffectPort> opPorts = shape.operation().uses().allow();
            for (int i = 0; i < opPorts.size(); i++) {
                BearIr.EffectPort port = opPorts.get(i);
                String portPath = shape.path() + ".uses.allow[" + i + "]";
                if (!allowed.containsKey(port.port())) {
                    throw semantic(
                        portPath + ".port",
                        BearIrValidationException.Code.UNKNOWN_REFERENCE,
                        "unknown block effect port: " + port.port()
                    );
                }
                Set<String> blockOps = allowed.get(port.port());
                for (int j = 0; j < port.ops().size(); j++) {
                    String op = port.ops().get(j);
                    if (!blockOps.contains(op)) {
                        throw semantic(
                            portPath + ".ops[" + j + "]",
                            BearIrValidationException.Code.UNKNOWN_REFERENCE,
                            "unknown block effect op: " + port.port() + "." + op
                        );
                    }
                }
            }
        }
    }

    private void validateBlockIdempotencyStore(
        BearIr.BlockIdempotency idempotency,
        Map<String, Set<String>> blockEffects,
        String path
    ) {
        requireNonNull(idempotency, path);
        requireNonNull(idempotency.store(), path + ".store");
        requireNonBlank(idempotency.store().port(), path + ".store.port");
        requireNonBlank(idempotency.store().getOp(), path + ".store.getOp");
        requireNonBlank(idempotency.store().putOp(), path + ".store.putOp");

        if (!blockEffects.containsKey(idempotency.store().port())) {
            throw semantic(path + ".store.port", BearIrValidationException.Code.UNKNOWN_REFERENCE, "unknown port: " + idempotency.store().port());
        }
        Set<String> ops = blockEffects.get(idempotency.store().port());
        if (!ops.contains(idempotency.store().getOp())) {
            throw semantic(path + ".store.getOp", BearIrValidationException.Code.UNKNOWN_REFERENCE, "unknown op: " + idempotency.store().getOp());
        }
        if (!ops.contains(idempotency.store().putOp())) {
            throw semantic(path + ".store.putOp", BearIrValidationException.Code.UNKNOWN_REFERENCE, "unknown op: " + idempotency.store().putOp());
        }
    }

    private void validateOperationIdempotency(BearIr.Block block, List<OperationShape> operationShapes) {
        BearIr.BlockIdempotency blockIdempotency = block.idempotency();
        for (OperationShape shape : operationShapes) {
            BearIr.OperationIdempotency idempotency = shape.operation().idempotency();
            if (idempotency == null) {
                continue;
            }
            String path = shape.path() + ".idempotency";
            requireNonNull(idempotency.mode(), path + ".mode");
            boolean hasKey = idempotency.key() != null;
            boolean hasKeyFromInputs = idempotency.keyFromInputs() != null;

            if (idempotency.mode() == BearIr.OperationIdempotencyMode.NONE) {
                if (hasKey || hasKeyFromInputs) {
                    throw semantic(
                        path,
                        BearIrValidationException.Code.INVALID_VALUE,
                        "mode=none does not allow key or keyFromInputs"
                    );
                }
                continue;
            }

            if (blockIdempotency == null) {
                throw semantic(
                    path + ".mode",
                    BearIrValidationException.Code.INVALID_VALUE,
                    "mode=use requires block.idempotency.store"
                );
            }

            if (hasKey == hasKeyFromInputs) {
                throw semantic(
                    path,
                    BearIrValidationException.Code.INVALID_VALUE,
                    "mode=use requires exactly one of key or keyFromInputs"
                );
            }
            if (hasKey) {
                requireNonBlank(idempotency.key(), path + ".key");
                if (!shape.inputTypes().containsKey(idempotency.key())) {
                    throw semantic(path + ".key", BearIrValidationException.Code.UNKNOWN_REFERENCE, "must reference an input field");
                }
            }
            if (hasKeyFromInputs) {
                requireNonNull(idempotency.keyFromInputs(), path + ".keyFromInputs");
                if (idempotency.keyFromInputs().isEmpty()) {
                    throw semantic(path + ".keyFromInputs", BearIrValidationException.Code.INVALID_VALUE, "must be a non-empty list");
                }
                LinkedHashSet<String> seen = new LinkedHashSet<>();
                for (int i = 0; i < idempotency.keyFromInputs().size(); i++) {
                    String value = idempotency.keyFromInputs().get(i);
                    String fieldPath = path + ".keyFromInputs[" + i + "]";
                    requireNonBlank(value, fieldPath);
                    if (!seen.add(value)) {
                        throw semantic(fieldPath, BearIrValidationException.Code.DUPLICATE, "duplicate key field: " + value);
                    }
                    if (!shape.inputTypes().containsKey(value)) {
                        throw semantic(fieldPath, BearIrValidationException.Code.UNKNOWN_REFERENCE, "must reference an input field");
                    }
                }
            }

            if (!usesContains(
                shape.operation().uses(),
                blockIdempotency.store().port(),
                blockIdempotency.store().getOp()
            )) {
                throw semantic(
                    path,
                    BearIrValidationException.Code.INVALID_VALUE,
                    "mode=use requires operation.uses to include idempotency store getOp"
                );
            }
            if (!usesContains(
                shape.operation().uses(),
                blockIdempotency.store().port(),
                blockIdempotency.store().putOp()
            )) {
                throw semantic(
                    path,
                    BearIrValidationException.Code.INVALID_VALUE,
                    "mode=use requires operation.uses to include idempotency store putOp"
                );
            }
        }
    }

    private boolean usesContains(BearIr.Effects uses, String portName, String opName) {
        for (BearIr.EffectPort port : uses.allow()) {
            if (!portName.equals(port.port())) {
                continue;
            }
            return port.ops().contains(opName);
        }
        return false;
    }

    private void validateBlockInvariants(List<BearIr.Invariant> blockInvariants) {
        if (blockInvariants == null) {
            return;
        }
        for (int i = 0; i < blockInvariants.size(); i++) {
            BearIr.Invariant invariant = blockInvariants.get(i);
            String path = "block.invariants[" + i + "]";
            validateInvariantDefinition(invariant, path);
        }
    }

    private void validateOperationInvariants(BearIr.Block block, List<OperationShape> shapes) {
        TreeSet<String> blockAllowed = new TreeSet<>();
        if (block.invariants() != null) {
            for (int i = 0; i < block.invariants().size(); i++) {
                blockAllowed.add(InvariantFingerprint.canonicalKey(block.invariants().get(i)));
            }
        }
        for (OperationShape shape : shapes) {
            List<BearIr.Invariant> opInvariants = shape.operation().invariants();
            if (opInvariants == null || opInvariants.isEmpty()) {
                continue;
            }
            if (blockAllowed.isEmpty()) {
                throw semantic(
                    shape.path() + ".invariants",
                    BearIrValidationException.Code.INVALID_VALUE,
                    "operation invariants require block.invariants allowed set"
                );
            }
            for (int i = 0; i < opInvariants.size(); i++) {
                BearIr.Invariant invariant = opInvariants.get(i);
                String path = shape.path() + ".invariants[" + i + "]";
                validateInvariantRule(invariant, path, shape.outputTypes());
                String fingerprint = InvariantFingerprint.canonicalKey(invariant);
                if (!blockAllowed.contains(fingerprint)) {
                    throw semantic(
                        path,
                        BearIrValidationException.Code.UNKNOWN_REFERENCE,
                        "operation invariant is not declared in block.invariants allowed set"
                    );
                }
            }
        }
    }

    private void validateInvariantRule(BearIr.Invariant invariant, String path, Map<String, BearIr.FieldType> outputTypesByName) {
        validateInvariantDefinition(invariant, path);
        if (!outputTypesByName.containsKey(invariant.field())) {
            throw semantic(path + ".field", BearIrValidationException.Code.UNKNOWN_REFERENCE, "must reference an output field");
        }

        BearIr.FieldType fieldType = outputTypesByName.get(invariant.field());
        switch (invariant.kind()) {
            case NON_NEGATIVE -> {
                if (fieldType != BearIr.FieldType.INT && fieldType != BearIr.FieldType.DECIMAL) {
                    throw semantic(path + ".kind", BearIrValidationException.Code.INVALID_VALUE, "non_negative requires int or decimal output");
                }
            }
            case NON_EMPTY -> {
                if (fieldType != BearIr.FieldType.STRING) {
                    throw semantic(path + ".kind", BearIrValidationException.Code.INVALID_VALUE, "non_empty requires string output");
                }
            }
            case EQUALS, ONE_OF -> {
                // No additional type restrictions beyond definition validation.
            }
        }
    }

    private void validateInvariantDefinition(BearIr.Invariant invariant, String path) {
        requireNonNull(invariant.kind(), path + ".kind");
        requireNonNull(invariant.scope(), path + ".scope");
        requireNonBlank(invariant.field(), path + ".field");
        requireNonNull(invariant.params(), path + ".params");
        if (invariant.scope() != BearIr.InvariantScope.RESULT) {
            throw semantic(path + ".scope", BearIrValidationException.Code.INVALID_VALUE, "scope must be result");
        }

        switch (invariant.kind()) {
            case NON_NEGATIVE, NON_EMPTY -> {
                if (invariant.params().value() != null || !invariant.params().values().isEmpty()) {
                    throw semantic(path + ".params", BearIrValidationException.Code.INVALID_VALUE, invariant.kind().name().toLowerCase() + " does not accept params");
                }
            }
            case EQUALS -> {
                if (invariant.params().value() == null) {
                    throw semantic(path + ".params.value", BearIrValidationException.Code.INVALID_VALUE, "equals requires params.value");
                }
                if (!invariant.params().values().isEmpty()) {
                    throw semantic(path + ".params.values", BearIrValidationException.Code.INVALID_VALUE, "equals does not accept params.values");
                }
            }
            case ONE_OF -> {
                if (invariant.params().value() != null) {
                    throw semantic(path + ".params.value", BearIrValidationException.Code.INVALID_VALUE, "one_of does not accept params.value");
                }
                if (invariant.params().values().isEmpty()) {
                    throw semantic(path + ".params.values", BearIrValidationException.Code.INVALID_VALUE, "one_of requires non-empty params.values");
                }
                Set<String> seen = new LinkedHashSet<>();
                for (int i = 0; i < invariant.params().values().size(); i++) {
                    String value = invariant.params().values().get(i);
                    String valuePath = path + ".params.values[" + i + "]";
                    requireNonNull(value, valuePath);
                    if (!seen.add(value)) {
                        throw semantic(valuePath, BearIrValidationException.Code.DUPLICATE, "duplicate params.values entry: " + value);
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

    private void validateEmptyEffectsPolicy(BearIr.Block block, List<OperationShape> operationShapes) {
        if (block.effects().allow() == null || !block.effects().allow().isEmpty()) {
            return;
        }
        if (isEchoSafeEmptyEffectsBlock(block, operationShapes)) {
            return;
        }
        throw semantic(
            "block.effects.allow",
            BearIrValidationException.Code.INVALID_VALUE,
            "empty effects.allow requires echo-safe block (no idempotency, no invariants, outputs must mirror input name:type pairs per operation)"
        );
    }

    private boolean isEchoSafeEmptyEffectsBlock(BearIr.Block block, List<OperationShape> operationShapes) {
        if (block.idempotency() != null) {
            return false;
        }
        if (block.invariants() != null && !block.invariants().isEmpty()) {
            return false;
        }
        for (OperationShape shape : operationShapes) {
            if (shape.operation().idempotency() != null && shape.operation().idempotency().mode() == BearIr.OperationIdempotencyMode.USE) {
                return false;
            }
            if (shape.operation().invariants() != null && !shape.operation().invariants().isEmpty()) {
                return false;
            }
            TreeSet<String> inputTuples = canonicalFieldTuples(shape.operation().contract().inputs());
            TreeSet<String> outputTuples = canonicalFieldTuples(shape.operation().contract().outputs());
            if (!inputTuples.containsAll(outputTuples)) {
                return false;
            }
        }
        return true;
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

    private record OperationShape(
        int index,
        BearIr.Operation operation,
        String path,
        Map<String, BearIr.FieldType> inputTypes,
        Map<String, BearIr.FieldType> outputTypes
    ) {
    }
}
