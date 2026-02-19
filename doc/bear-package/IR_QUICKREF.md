# IR_QUICKREF.md

Purpose:
- Fast, authoritative reference for writing valid BEAR IR in v0.

Use this file plus `doc/IR_EXAMPLES.md` as the IR source of truth.
Do not reverse engineer CLI binaries to discover schema.

## Required Root Shape

```yaml
version: v0
block:
  ...
```

Required keys:
- `version` (must be `v0`)
- `block`

## `block` Object

Required keys:
- `name` (string)
- `kind` (must be `logic` in v0)
- `contract`
- `effects`

Optional keys:
- `idempotency`
- `invariants`

## Contract Shape

```yaml
contract:
  inputs:
    - name: requestId
      type: string
  outputs:
    - name: accepted
      type: bool
```

Rules:
- `inputs` must be non-empty
- `outputs` must be non-empty
- input names must be unique
- output names must be unique

Allowed field types in v0:
- `string`
- `decimal`
- `int`
- `bool`
- `enum`

## Effects Shape

```yaml
effects:
  allow:
    - port: stateStore
      ops: [get, put]
```

Rules:
- `allow` is required (may be empty)
- port names must be unique
- ops must be unique within each port

## Idempotency Shape (Optional)

```yaml
idempotency:
  key: requestId
  store:
    port: idempotency
    getOp: get
    putOp: put
```

Rules:
- `key` must reference an input field name
- `store.port` must reference a declared effect port
- `store.getOp` and `store.putOp` must reference declared ops under that port

## Invariants Shape (Optional)

```yaml
invariants:
  - kind: non_negative
    field: remainingQuota
```

Rules:
- v0 only supports `kind: non_negative`
- `field` must reference an output field name

## Common Validation Failures

- unknown keys at any level
- invalid enum values (`kind`, field `type`, invariant `kind`)
- duplicate input/output/port/op names
- broken references in idempotency/invariants
- empty `inputs` or `outputs`

## Intent -> IR Change Map

If request changes external reach:
- update `effects.allow`

If request changes request/response shape:
- update `contract.inputs`/`contract.outputs`

If request adds replay-safe behavior:
- add/update `idempotency`

If request adds structural output guarantees:
- add/update `invariants`

## Commands (Deterministic Order)

For each changed IR:
1. `bear validate <ir-file>`
2. `bear compile <ir-file> --project <repoRoot>`
3. `bear check <ir-file> --project <repoRoot>` or `bear check --all --project <repoRoot>`
