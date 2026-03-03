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
        NON_NEGATIVE,
        NON_EMPTY,
        EQUALS,
        ONE_OF
    }

    public enum InvariantScope {
        RESULT
    }

    public enum OperationIdempotencyMode {
        USE,
        NONE
    }

    public record Block(
        String name,
        BlockKind kind,
        List<Operation> operations,
        Effects effects,
        Impl impl,
        BlockIdempotency idempotency,
        List<Invariant> invariants
    ) {
    }

    public record Operation(
        String name,
        Contract contract,
        Effects uses,
        OperationIdempotency idempotency,
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

    public record BlockIdempotency(IdempotencyStore store) {
    }

    public record OperationIdempotency(OperationIdempotencyMode mode, String key, List<String> keyFromInputs) {
    }

    public record IdempotencyStore(String port, String getOp, String putOp) {
    }

    public record Invariant(InvariantKind kind, InvariantScope scope, String field, InvariantParams params) {
    }

    public record InvariantParams(String value, List<String> values) {
    }

    public record Impl(List<AllowedDep> allowedDeps) {
    }

    public record AllowedDep(String maven, String version) {
    }
}

