package com.bear.kernel.ir;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
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
            if (!"v0".equals(version)) {
                throw schema("version", BearIrValidationException.Code.INVALID_ENUM, "expected 'v0'");
            }

            Map<?, ?> block = requireMap(root, "block", "block");
            requireOnlyKeys(block, "block", Set.of("name", "kind", "contract", "effects", "idempotency", "invariants"));

            String name = requireString(block, "name", "block.name");
            BearIr.BlockKind kind = parseBlockKind(requireString(block, "kind", "block.kind"), "block.kind");

            BearIr.Contract contract = parseContract(requireMap(block, "contract", "block.contract"), "block.contract");
            BearIr.Effects effects = parseEffects(requireMap(block, "effects", "block.effects"), "block.effects");

            BearIr.Idempotency idempotency = null;
            if (block.containsKey("idempotency")) {
                idempotency = parseIdempotency(requireMap(block, "idempotency", "block.idempotency"), "block.idempotency");
            }

            List<BearIr.Invariant> invariants = null;
            if (block.containsKey("invariants")) {
                invariants = parseInvariants(requireList(block, "invariants", "block.invariants"), "block.invariants");
            }

            return new BearIr(version, new BearIr.Block(name, kind, contract, effects, idempotency, invariants));
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
        if ("non_negative".equals(raw)) {
            return BearIr.InvariantKind.NON_NEGATIVE;
        }
        throw schema(path, BearIrValidationException.Code.INVALID_ENUM, "expected 'non_negative'");
    }

    private BearIr.Contract parseContract(Map<?, ?> contract, String path) {
        requireOnlyKeys(contract, path, Set.of("inputs", "outputs"));
        List<?> inputs = requireList(contract, "inputs", path + ".inputs");
        List<?> outputs = requireList(contract, "outputs", path + ".outputs");
        return new BearIr.Contract(parseFields(inputs, path + ".inputs"), parseFields(outputs, path + ".outputs"));
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
            requireOnlyKeys(port, itemPath, Set.of("port", "ops"));
            String portName = requireString(port, "port", itemPath + ".port");
            List<?> opsRaw = requireList(port, "ops", itemPath + ".ops");
            List<String> ops = new ArrayList<>();
            for (int j = 0; j < opsRaw.size(); j++) {
                Object op = opsRaw.get(j);
                if (!(op instanceof String opName)) {
                    throw schema(itemPath + ".ops[" + j + "]", BearIrValidationException.Code.INVALID_TYPE, "expected string");
                }
                ops.add(opName);
            }
            ports.add(new BearIr.EffectPort(portName, ops));
        }
        return new BearIr.Effects(ports);
    }

    private BearIr.Idempotency parseIdempotency(Map<?, ?> idempotency, String path) {
        requireOnlyKeys(idempotency, path, Set.of("key", "store"));
        String key = requireString(idempotency, "key", path + ".key");
        Map<?, ?> store = requireMap(idempotency, "store", path + ".store");
        requireOnlyKeys(store, path + ".store", Set.of("port", "getOp", "putOp"));
        String port = requireString(store, "port", path + ".store.port");
        String getOp = requireString(store, "getOp", path + ".store.getOp");
        String putOp = requireString(store, "putOp", path + ".store.putOp");
        return new BearIr.Idempotency(key, new BearIr.IdempotencyStore(port, getOp, putOp));
    }

    private List<BearIr.Invariant> parseInvariants(List<?> items, String path) {
        List<BearIr.Invariant> invariants = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String itemPath = path + "[" + i + "]";
            Object item = items.get(i);
            if (!(item instanceof Map<?, ?> invariant)) {
                throw schema(itemPath, BearIrValidationException.Code.INVALID_TYPE, "expected mapping");
            }
            requireOnlyKeys(invariant, itemPath, Set.of("kind", "field"));
            String kindRaw = requireString(invariant, "kind", itemPath + ".kind");
            String field = requireString(invariant, "field", itemPath + ".field");
            invariants.add(new BearIr.Invariant(parseInvariantKind(kindRaw, itemPath + ".kind"), field));
        }
        return invariants;
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
