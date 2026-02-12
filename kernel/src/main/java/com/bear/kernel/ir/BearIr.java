package com.bear.kernel.ir;

import java.util.List;

public record BearIr(String version, BearIr.Block block) {
    public enum BlockKind {
        LOGIC
    }

    public enum FieldType {
        STRING,
        DECIMAL,
        INT,
        BOOL,
        ENUM
    }

    public enum InvariantKind {
        NON_NEGATIVE
    }

    public record Block(
        String name,
        BlockKind kind,
        Contract contract,
        Effects effects,
        Idempotency idempotency,
        List<Invariant> invariants
    ) {
    }

    public record Contract(List<Field> inputs, List<Field> outputs) {
    }

    public record Field(String name, FieldType type) {
    }

    public record Effects(List<EffectPort> allow) {
    }

    public record EffectPort(String port, List<String> ops) {
    }

    public record Idempotency(String key, IdempotencyStore store) {
    }

    public record IdempotencyStore(String port, String getOp, String putOp) {
    }

    public record Invariant(InvariantKind kind, String field) {
    }
}
