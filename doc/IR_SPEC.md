# BEAR IR v0 Spec (Canonical)

## Purpose
BEAR IR is a rigid, machine-checkable representation for one logic block.
It is intentionally limited and must remain deterministic to validate, normalize, and compile.

## Scope Lock
v0 supports:
- one `logic` block per IR file
- structured effect ports
- contract inputs/outputs
- idempotency by key with explicit store ops
- invariant template `non_negative(field=<outputField>)`

v0 does not support:
- capability blocks in IR
- block graphs or block-to-block composition
- behavior DSL
- requires/ensures language
- state delta modeling
- infrastructure simulation

## Model
Root object:
- `version` (string, required, only `v0`)
- `block` (object, required)

`block` fields:
- `name` (string, required)
- `kind` (enum, required, only `logic`)
- `contract` (object, required)
- `effects` (object, required)
- `idempotency` (object, optional)
- `invariants` (array, optional)

`contract` fields:
- `inputs` (array of fields, required)
- `outputs` (array of fields, required)

Field shape:
- `name` (string, required)
- `type` (enum, required, allowed values in v0: `string`, `decimal`, `int`, `bool`, `enum`)

`effects` fields:
- `allow` (array of effect ports, required; may be empty)

Effect port shape:
- `port` (string, required)
- `ops` (array of operation names, required; may be empty)

`idempotency` fields:
- `key` (string, required, must reference an input field name)
- `store` (object, required when `idempotency` exists)

`idempotency.store` fields:
- `port` (string, required, must reference declared `effects.allow[*].port`)
- `getOp` (string, required, must reference one op under the declared port)
- `putOp` (string, required, must reference one op under the declared port)

Invariant shape:
- `kind` (enum, required, only `non_negative`)
- `field` (string, required, must reference an output field name)

## Validation Rules (Strict)
- fail on unknown keys at any level
- fail on invalid enum values
- fail on invalid references
- `contract.inputs` must be non-empty
- `contract.outputs` must be non-empty
- input names must be unique
- output names must be unique
- port names must be unique
- ops must be unique within each port
- `idempotency.key` must reference an input field
- `idempotency.store.port` must reference a declared port
- `idempotency.store.getOp` and `idempotency.store.putOp` must reference declared ops under that port
- invariant `field` must reference an output field

## Deterministic Normalization
Canonical form must:
- emit root keys in order: `version`, `block`
- emit `block` keys in order: `name`, `kind`, `contract`, `effects`, `idempotency`, `invariants` (omit absent optionals)
- sort inputs by `name`
- sort outputs by `name`
- sort ports by `port`
- sort ops within each port
- sort invariants deterministically by `kind` then `field`
- omit `invariants` if it is absent or empty (`[]`)

## Canonical Demo IR (v0)
See `spec/fixtures/withdraw.bear.yaml`.
```yaml
version: v0
block:
  name: Withdraw
  kind: logic

  contract:
    inputs:
      - name: accountId
        type: string
      - name: amount
        type: decimal
      - name: currency
        type: string
      - name: txId
        type: string
    outputs:
      - name: balance
        type: decimal

  effects:
    allow:
      - port: ledger
        ops:
          - getBalance
          - setBalance
      - port: idempotency
        ops:
          - get
          - put

  idempotency:
    key: txId
    store:
      port: idempotency
      getOp: get
      putOp: put

  invariants:
    - kind: non_negative
      field: balance
```

## Notes
- This spec is structural, not behavioral.
- v0 idempotency guarantees deterministic replay behavior in the test harness, not concurrency correctness.
- Do not expand IR expressiveness in v0.
