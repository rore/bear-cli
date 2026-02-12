package com.bear.kernel.ir;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BearIrYamlEmitter {
    private final Yaml yaml;

    public BearIrYamlEmitter() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(false);
        options.setIndent(2);
        // Keep defaults for sequence indicator indent; SnakeYAML enforces constraints
        // relative to `indent` and may throw if set inconsistently.
        options.setLineBreak(DumperOptions.LineBreak.UNIX);
        options.setExplicitStart(false);
        options.setExplicitEnd(false);

        Representer representer = new Representer(options);
        this.yaml = new Yaml(representer, options);
    }

    public String toCanonicalYaml(BearIr ir) {
        return yaml.dump(toCanonicalMap(ir));
    }

    private Map<String, Object> toCanonicalMap(BearIr ir) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", ir.version());
        root.put("block", toBlockMap(ir.block()));
        return root;
    }

    private Map<String, Object> toBlockMap(BearIr.Block block) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", block.name());
        map.put("kind", switch (block.kind()) {
            case LOGIC -> "logic";
        });
        map.put("contract", toContractMap(block.contract()));
        map.put("effects", toEffectsMap(block.effects()));

        if (block.idempotency() != null) {
            map.put("idempotency", toIdempotencyMap(block.idempotency()));
        }

        if (block.invariants() != null && !block.invariants().isEmpty()) {
            map.put("invariants", toInvariantsList(block.invariants()));
        }

        return map;
    }

    private Map<String, Object> toContractMap(BearIr.Contract contract) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("inputs", toFieldsList(contract.inputs()));
        map.put("outputs", toFieldsList(contract.outputs()));
        return map;
    }

    private List<Object> toFieldsList(List<BearIr.Field> fields) {
        List<Object> list = new ArrayList<>();
        for (BearIr.Field field : fields) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", field.name());
            map.put("type", switch (field.type()) {
                case STRING -> "string";
                case DECIMAL -> "decimal";
                case INT -> "int";
                case BOOL -> "bool";
                case ENUM -> "enum";
            });
            list.add(map);
        }
        return list;
    }

    private Map<String, Object> toEffectsMap(BearIr.Effects effects) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("allow", toAllowList(effects.allow()));
        return map;
    }

    private List<Object> toAllowList(List<BearIr.EffectPort> allow) {
        List<Object> list = new ArrayList<>();
        for (BearIr.EffectPort port : allow) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("port", port.port());
            map.put("ops", new ArrayList<>(port.ops()));
            list.add(map);
        }
        return list;
    }

    private Map<String, Object> toIdempotencyMap(BearIr.Idempotency idempotency) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", idempotency.key());
        map.put("store", toIdempotencyStoreMap(idempotency.store()));
        return map;
    }

    private Map<String, Object> toIdempotencyStoreMap(BearIr.IdempotencyStore store) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("port", store.port());
        map.put("getOp", store.getOp());
        map.put("putOp", store.putOp());
        return map;
    }

    private List<Object> toInvariantsList(List<BearIr.Invariant> invariants) {
        List<Object> list = new ArrayList<>();
        for (BearIr.Invariant invariant : invariants) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", switch (invariant.kind()) {
                case NON_NEGATIVE -> "non_negative";
            });
            map.put("field", invariant.field());
            list.add(map);
        }
        return list;
    }
}
