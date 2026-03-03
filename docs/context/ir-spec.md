# BEAR IR v1 Spec (Canonical)

## Purpose
BEAR IR is a strict, machine-checkable contract for one logic block.
`v1` now supports multiple operations in that block while preserving strict block boundary authority.

## Scope Lock (v1)
v1 supports:
- one `logic` block per IR file
- `block.operations` (non-empty) with per-operation contracts
- block-authoritative effect boundary (`block.effects.allow`)
- operation usage subsets (`operations[].uses.allow`)
- block idempotency store capability + operation-level idempotency use mode
- block allowed invariants + operation invariant subset checks
- optional implementation dependency allow-list (`impl.allowedDeps`)

v1 does not support:
- per-operation impl bindings
- block graphs/composition DSL
- behavior DSL (`requires/ensures`)
- transaction semantics / cross-port atomicity

## Semantics Decision Rule (Canonical)
BEAR enforces a semantic only if all are true:
- it is wrapper-enforceable from declared IR inputs/outputs/ports
- it requires no hidden context unless explicitly declared
- it is deterministic for the active target
- it has a frozen, testable contract

## Model
Root object:
- `version` (required, must be `v1`)
- `block` (required)

`block` required fields:
- `name`
- `kind` (`logic`)
- `operations` (non-empty)
- `effects.allow`

`block` optional fields:
- `idempotency.store`
- `invariants`
- `impl`

`operation` required fields:
- `name`
- `contract.inputs`
- `contract.outputs`
- `uses.allow`

`operation` optional fields:
- `idempotency` (`mode: use|none`)
- `invariants`

Forbidden:
- per-operation `impl`

Field type enum:
- `string`, `decimal`, `int`, `bool`, `enum`

## Boundary Authority Semantics
1. `block.effects.allow` is the authoritative capability boundary.
2. `operations[].uses.allow` must be subset-or-equal to block effects.
3. `block.idempotency.store` declares idempotency capability.
4. `operations[].idempotency` decides per-operation use (`use|none`).
5. `block.invariants` is the allowed invariant set.
6. `operations[].invariants` must be a subset of block allowed invariants.

## Idempotency Schema
Block-level capability:
```yaml
idempotency:
  store:
    port: idempotency
    getOp: get
    putOp: put
```

Operation-level usage:
```yaml
idempotency:
  mode: use
  key: requestId
```
or
```yaml
idempotency:
  mode: use
  keyFromInputs: [walletId, requestId]
```
or
```yaml
idempotency:
  mode: none
```

Validation:
- `mode=use` requires block idempotency store
- `mode=use` requires exactly one of `key` / `keyFromInputs`
- key fields must exist in that operation inputs
- operation `uses` must include block idempotency `store.getOp` and `store.putOp`
- `mode=none` forbids key fields

## Invariant Schema
`block.invariants` and `operations[].invariants` use full inline rules:
- `kind`
- `scope` (canonical `result`)
- `field`
- optional `params`

Kinds:
- `non_negative`
- `non_empty`
- `equals`
- `one_of`

Subset validation uses deterministic invariant fingerprints:
- canonical key parts: `kind`, `scope`, `field`, canonical params
- operation invariant fingerprints must be subset of block allowed fingerprints

## Validation Rules (Strict)
- fail on unknown keys at every level
- operation names unique + trimmed
- input/output uniqueness is per operation only
- effect ports unique; ops unique per port
- operation uses must reference declared block effects
- if block invariants absent/empty, operation invariants must be empty
- empty effects policy is per operation echo-safe check

## Deterministic Normalization
Canonical form:
- root key order: `version`, `block`
- block key order: `name`, `kind`, `operations`, `effects`, `idempotency`, `invariants`, `impl`
- operations sorted by operation name
- field sorting and uniqueness are per operation
- ports sorted by `port`; ops sorted within each port
- `impl.allowedDeps` sorted by `maven`

## Wrapper-Owned Semantics
Generated wrappers own semantic enforcement per operation:
- idempotency get/replay/put
- invariant checks on fresh and replay-decoded results

Generator contract:
- shared `<Block>Logic` + shared `<Block>Impl` stub
- per-operation request/result/wrapper:
  - `<Block>_<Operation>Request`
  - `<Block>_<Operation>Result`
  - `<Block>_<Operation>` with typed `execute`
- idempotency key includes operation identity segment

## Canonical Example (Excerpt)
```yaml
version: v1
block:
  name: WalletRead
  kind: logic
  operations:
    - name: GetWalletBalance
      contract:
        inputs:
          - name: walletId
            type: string
        outputs:
          - name: balanceCents
            type: int
      uses:
        allow:
          - port: walletStore
            ops: [get]
      idempotency:
        mode: none
      invariants:
        - kind: non_negative
          field: balanceCents
    - name: GetWalletStatement
      contract:
        inputs:
          - name: walletId
            type: string
          - name: since
            type: string
        outputs:
          - name: statementId
            type: string
      uses:
        allow:
          - port: statementStore
            ops: [listSince]
      idempotency:
        mode: none
  effects:
    allow:
      - port: walletStore
        ops: [get]
      - port: statementStore
        ops: [listSince]
  invariants:
    - kind: non_negative
      field: balanceCents
```

## Notes
- IR defines semantics; targets must enforce them or `check` fails deterministically.
- v1 does not prove cross-port atomicity.
