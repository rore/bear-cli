package com.bear.kernel.ir;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BearIrParser {
    private final Yaml yaml;

    public BearIrParser() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        this.yaml = new Yaml(new SafeConstructor(options));
    }

    public BearIr parse(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Object parsed;
            try {
                parsed = loadSingleDocument(in);
            } catch (YAMLException e) {
                throw schema("root", BearIrValidationException.Code.INVALID_YAML, "invalid YAML");
            }

            if (parsed == null) {
                parsed = Map.of();
            }
            if (!(parsed instanceof Map<?, ?> rawRoot)) {
                throw schema("root", BearIrValidationException.Code.INVALID_TYPE, "root must be a YAML mapping");
            }

            Map<?, ?> root = rawRoot;
            requireOnlyKeys(root, "root", Set.of("version", "block"));

            String version = requireString(root, "version", "version");
            if (!"v1".equals(version)) {
                throw schema("version", BearIrValidationException.Code.INVALID_ENUM, "expected 'v1'");
            }

            Map<?, ?> block = requireMap(root, "block", "block");
            requireOnlyKeys(block, "block", Set.of("name", "kind", "operations", "effects", "impl", "idempotency", "invariants"));

            String name = requireString(block, "name", "block.name");
            BearIr.BlockKind kind = parseBlockKind(requireString(block, "kind", "block.kind"), "block.kind");

            List<BearIr.Operation> operations = parseOperations(
                requireList(block, "operations", "block.operations"),
                "block.operations"
            );
            BearIr.Effects effects = parseEffects(requireMap(block, "effects", "block.effects"), "block.effects");
            BearIr.Impl impl = parseImpl(block.containsKey("impl") ? requireMap(block, "impl", "block.impl") : Map.of(), "block.impl");

            BearIr.BlockIdempotency idempotency = null;
            if (block.containsKey("idempotency")) {
                idempotency = parseBlockIdempotency(requireMap(block, "idempotency", "block.idempotency"), "block.idempotency");
            }

            List<BearIr.Invariant> invariants = null;
            if (block.containsKey("invariants")) {
                invariants = parseInvariants(requireList(block, "invariants", "block.invariants"), "block.invariants");
            }

            return new BearIr(version, new BearIr.Block(name, kind, operations, effects, impl, idempotency, invariants));
        }
    }

    private Object loadSingleDocument(InputStream in) {
        Object first = null;
        int count = 0;
        for (Object doc : yaml.loadAll(in)) {
            if (count == 0) {
                first = doc;
            }
            count++;
            if (count > 1) {
                throw schema("root", BearIrValidationException.Code.MULTI_DOCUMENT, "multiple YAML documents are not allowed");
            }
        }
        return first;
    }

    private BearIr.BlockKind parseBlockKind(String raw, String path) {
        if ("logic".equals(raw)) {
            return BearIr.BlockKind.LOGIC;
        }
        throw schema(path, BearIrValidationException.Code.INVALID_ENUM, "expected 'logic'");
    }

    private BearIr.EffectPortKind parseEffectPortKind(String raw, String path) {
        return switch (raw) {
            case "external" -> BearIr.EffectPortKind.EXTERNAL;
            case "block" -> BearIr.EffectPortKind.BLOCK;
            default -> throw schema(path, BearIrValidationException.Code.INVALID_ENUM, "expected 'external' or 'block'");
        };
    }

    private BearIr.FieldType parseFieldType(String raw, String path) {
        return switch (raw) {
            case "string" -> BearIr.FieldType.STRING;
            case "decimal" -> BearIr.FieldType.DECIMAL;
            case "int" -> BearIr.FieldType.INT;
            case "bool" -> BearIr.FieldType.BOOL;
            case "enum" -> BearIr.FieldType.ENUM;
            default -> throw schema(path, BearIrValidationException.Code.INVALID_ENUM, "invalid type: " + raw);
        };
    }

    private BearIr.InvariantKind parseInvariantKind(String raw, String path) {
        return switch (raw) {
            case "non_negative" -> BearIr.InvariantKind.NON_NEGATIVE;
            case "non_empty" -> BearIr.InvariantKind.NON_EMPTY;
            case "equals" -> BearIr.InvariantKind.EQUALS;
            case "one_of" -> BearIr.InvariantKind.ONE_OF;
            default -> throw schema(path, BearIrValidationException.Code.INVALID_ENUM, "invalid invariant kind: " + raw);
        };
    }

    private BearIr.InvariantScope parseInvariantScope(String raw, String path) {
        if ("result".equals(raw)) {
            return BearIr.InvariantScope.RESULT;
        }
        throw schema(path, BearIrValidationException.Code.INVALID_ENUM, "expected 'result'");
    }

    private BearIr.OperationIdempotencyMode parseOperationIdempotencyMode(String raw, String path) {
        return switch (raw) {
            case "use" -> BearIr.OperationIdempotencyMode.USE;
            case "none" -> BearIr.OperationIdempotencyMode.NONE;
            default -> throw schema(path, BearIrValidationException.Code.INVALID_ENUM, "expected 'use' or 'none'");
        };
    }

    private BearIr.Contract parseContract(Map<?, ?> contract, String path) {
        requireOnlyKeys(contract, path, Set.of("inputs", "outputs"));
        List<?> inputs = requireList(contract, "inputs", path + ".inputs");
        List<?> outputs = requireList(contract, "outputs", path + ".outputs");
        return new BearIr.Contract(parseFields(inputs, path + ".inputs"), parseFields(outputs, path + ".outputs"));
    }

    private List<BearIr.Operation> parseOperations(List<?> items, String path) {
        ArrayList<BearIr.Operation> operations = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String itemPath = path + "[" + i + "]";
            Object item = items.get(i);
            if (!(item instanceof Map<?, ?> op)) {
                throw schema(itemPath, BearIrValidationException.Code.INVALID_TYPE, "expected mapping");
            }
            requireOnlyKeys(op, itemPath, Set.of("name", "contract", "uses", "idempotency", "invariants"));
            String name = requireString(op, "name", itemPath + ".name");
            BearIr.Contract contract = parseContract(
                requireMap(op, "contract", itemPath + ".contract"),
                itemPath + ".contract"
            );
            BearIr.Effects uses = parseEffects(requireMap(op, "uses", itemPath + ".uses"), itemPath + ".uses");
            BearIr.OperationIdempotency idempotency = null;
            if (op.containsKey("idempotency")) {
                idempotency = parseOperationIdempotency(
                    requireMap(op, "idempotency", itemPath + ".idempotency"),
                    itemPath + ".idempotency"
                );
            }
            List<BearIr.Invariant> invariants = null;
            if (op.containsKey("invariants")) {
                invariants = parseInvariants(requireList(op, "invariants", itemPath + ".invariants"), itemPath + ".invariants");
            }
            operations.add(new BearIr.Operation(name, contract, uses, idempotency, invariants));
        }
        return operations;
    }

    private List<BearIr.Field> parseFields(List<?> items, String path) {
        List<BearIr.Field> fields = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String itemPath = path + "[" + i + "]";
            Object item = items.get(i);
            if (!(item instanceof Map<?, ?> field)) {
                throw schema(itemPath, BearIrValidationException.Code.INVALID_TYPE, "expected mapping");
            }
            requireOnlyKeys(field, itemPath, Set.of("name", "type"));
            String name = requireString(field, "name", itemPath + ".name");
            String rawType = requireString(field, "type", itemPath + ".type");
            fields.add(new BearIr.Field(name, parseFieldType(rawType, itemPath + ".type")));
        }
        return fields;
    }

    private BearIr.Effects parseEffects(Map<?, ?> effects, String path) {
        requireOnlyKeys(effects, path, Set.of("allow"));
        List<?> allow = requireList(effects, "allow", path + ".allow");
        List<BearIr.EffectPort> ports = new ArrayList<>();
        for (int i = 0; i < allow.size(); i++) {
            String itemPath = path + ".allow[" + i + "]";
            Object item = allow.get(i);
            if (!(item instanceof Map<?, ?> port)) {
                throw schema(itemPath, BearIrValidationException.Code.INVALID_TYPE, "expected mapping");
            }
            requireOnlyKeys(port, itemPath, Set.of("port", "kind", "ops", "targetBlock", "targetOps"));
            String portName = requireString(port, "port", itemPath + ".port");
            BearIr.EffectPortKind kind = port.containsKey("kind")
                ? parseEffectPortKind(requireString(port, "kind", itemPath + ".kind"), itemPath + ".kind")
                : BearIr.EffectPortKind.EXTERNAL;

            List<String> ops = null;
            if (port.containsKey("ops")) {
                ops = parseStringList(requireList(port, "ops", itemPath + ".ops"), itemPath + ".ops");
            }
            String targetBlock = null;
            if (port.containsKey("targetBlock")) {
                targetBlock = requireString(port, "targetBlock", itemPath + ".targetBlock");
            }
            List<String> targetOps = null;
            if (port.containsKey("targetOps")) {
                targetOps = parseStringList(requireList(port, "targetOps", itemPath + ".targetOps"), itemPath + ".targetOps");
            }
            ports.add(new BearIr.EffectPort(portName, kind, ops, targetBlock, targetOps));
        }
        return new BearIr.Effects(ports);
    }

    private BearIr.BlockIdempotency parseBlockIdempotency(Map<?, ?> idempotency, String path) {
        requireOnlyKeys(idempotency, path, Set.of("store"));
        Map<?, ?> store = requireMap(idempotency, "store", path + ".store");
        requireOnlyKeys(store, path + ".store", Set.of("port", "getOp", "putOp"));
        String port = requireString(store, "port", path + ".store.port");
        String getOp = requireString(store, "getOp", path + ".store.getOp");
        String putOp = requireString(store, "putOp", path + ".store.putOp");
        return new BearIr.BlockIdempotency(new BearIr.IdempotencyStore(port, getOp, putOp));
    }

    private BearIr.OperationIdempotency parseOperationIdempotency(Map<?, ?> idempotency, String path) {
        requireOnlyKeys(idempotency, path, Set.of("mode", "key", "keyFromInputs"));
        BearIr.OperationIdempotencyMode mode = parseOperationIdempotencyMode(
            requireString(idempotency, "mode", path + ".mode"),
            path + ".mode"
        );
        String key = null;
        if (idempotency.containsKey("key")) {
            key = requireString(idempotency, "key", path + ".key");
        }
        List<String> keyFromInputs = null;
        if (idempotency.containsKey("keyFromInputs")) {
            keyFromInputs = parseStringList(
                requireList(idempotency, "keyFromInputs", path + ".keyFromInputs"),
                path + ".keyFromInputs"
            );
        }
        return new BearIr.OperationIdempotency(mode, key, keyFromInputs);
    }

    private List<BearIr.Invariant> parseInvariants(List<?> items, String path) {
        List<BearIr.Invariant> invariants = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String itemPath = path + "[" + i + "]";
            Object item = items.get(i);
            if (!(item instanceof Map<?, ?> invariant)) {
                throw schema(itemPath, BearIrValidationException.Code.INVALID_TYPE, "expected mapping");
            }
            requireOnlyKeys(invariant, itemPath, Set.of("kind", "scope", "field", "params"));
            String kindRaw = requireString(invariant, "kind", itemPath + ".kind");
            String scopeRaw = invariant.containsKey("scope")
                ? requireString(invariant, "scope", itemPath + ".scope")
                : "result";
            String field = requireString(invariant, "field", itemPath + ".field");
            BearIr.InvariantParams params = parseInvariantParams(
                invariant.containsKey("params")
                    ? requireMap(invariant, "params", itemPath + ".params")
                    : Map.of(),
                itemPath + ".params"
            );
            invariants.add(new BearIr.Invariant(
                parseInvariantKind(kindRaw, itemPath + ".kind"),
                parseInvariantScope(scopeRaw, itemPath + ".scope"),
                field,
                params
            ));
        }
        return invariants;
    }

    private BearIr.InvariantParams parseInvariantParams(Map<?, ?> params, String path) {
        requireOnlyKeys(params, path, Set.of("value", "values"));
        String value = null;
        if (params.containsKey("value")) {
            value = requireString(params, "value", path + ".value");
        }
        List<String> values = List.of();
        if (params.containsKey("values")) {
            values = parseStringList(requireList(params, "values", path + ".values"), path + ".values");
        }
        return new BearIr.InvariantParams(value, values);
    }

    private BearIr.Impl parseImpl(Map<?, ?> impl, String path) {
        requireOnlyKeys(impl, path, Set.of("allowedDeps"));
        List<BearIr.AllowedDep> allowedDeps = new ArrayList<>();
        if (impl.containsKey("allowedDeps")) {
            List<?> items = requireList(impl, "allowedDeps", path + ".allowedDeps");
            for (int i = 0; i < items.size(); i++) {
                String itemPath = path + ".allowedDeps[" + i + "]";
                Object item = items.get(i);
                if (!(item instanceof Map<?, ?> dep)) {
                    throw schema(itemPath, BearIrValidationException.Code.INVALID_TYPE, "expected mapping");
                }
                requireOnlyKeys(dep, itemPath, Set.of("maven", "version"));
                String maven = requireString(dep, "maven", itemPath + ".maven");
                String version = requireString(dep, "version", itemPath + ".version");
                allowedDeps.add(new BearIr.AllowedDep(maven, version));
            }
        }
        return new BearIr.Impl(allowedDeps);
    }

    private void requireOnlyKeys(Map<?, ?> map, String path, Set<String> allowed) {
        for (Object key : map.keySet()) {
            String keyName = String.valueOf(key);
            if (!allowed.contains(keyName)) {
                throw schema(path, BearIrValidationException.Code.UNKNOWN_KEY, "unknown key: " + keyName);
            }
        }
    }

    private Map<?, ?> requireMap(Map<?, ?> map, String key, String path) {
        if (!map.containsKey(key)) {
            throw schema(path, BearIrValidationException.Code.MISSING_FIELD, "missing required field");
        }
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?> child)) {
            throw schema(path, BearIrValidationException.Code.INVALID_TYPE, "expected mapping");
        }
        return child;
    }

    private List<?> requireList(Map<?, ?> map, String key, String path) {
        if (!map.containsKey(key)) {
            throw schema(path, BearIrValidationException.Code.MISSING_FIELD, "missing required field");
        }
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            throw schema(path, BearIrValidationException.Code.INVALID_TYPE, "expected list");
        }
        return list;
    }

    private List<String> parseStringList(List<?> list, String path) {
        ArrayList<String> values = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof String text)) {
                throw schema(path + "[" + i + "]", BearIrValidationException.Code.INVALID_TYPE, "expected string");
            }
            values.add(text);
        }
        return List.copyOf(values);
    }

    private String requireString(Map<?, ?> map, String key, String path) {
        if (!map.containsKey(key)) {
            throw schema(path, BearIrValidationException.Code.MISSING_FIELD, "missing required field");
        }
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            throw schema(path, BearIrValidationException.Code.INVALID_TYPE, "expected string");
        }
        return text;
    }

    private BearIrValidationException schema(String path, BearIrValidationException.Code code, String message) {
        return new BearIrValidationException(BearIrValidationException.Category.SCHEMA, path, code, message);
    }
}
