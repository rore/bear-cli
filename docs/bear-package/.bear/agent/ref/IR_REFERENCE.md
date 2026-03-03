# IR_REFERENCE.md

Purpose:
- Single authoritative BEAR IR reference.
- Canonical schema and rule quick reference.

Authority rule:
- If a shape/rule is not declared here, do not assume it.
- IR file = one block; block may contain multiple operations.

## Required Root Shape

```yaml
version: v1
block:
  ...
```

Required root keys:
- `version` (`v1`)
- `block`

## `block` Object

Required:
- `name`
- `kind` (`logic`)
- `operations` (non-empty)
- `effects`

Optional:
- `idempotency` (store capability only)
- `invariants` (allowed set)
- `impl`

## Operation Object

Required:
- `name`
- `contract.inputs`
- `contract.outputs`
- `uses.allow`

Optional:
- `idempotency` (`mode: use|none`)
- `invariants`

Forbidden:
- per-operation `impl`

Rules:
- operation names unique and trimmed
- field uniqueness is per operation only
- `uses.allow` must be subset of `block.effects.allow`

## Contract Fields

```yaml
contract:
  inputs:
    - name: requestId
      type: string
  outputs:
    - name: accepted
      type: bool
```

Types:
- `string`
- `decimal`
- `int`
- `bool`
- `enum`

## Effects Boundary

```yaml
effects:
  allow:
    - port: stateStore
      ops: [get, put]
```

Rules:
- block effects are authoritative boundary superset
- unique ports
- unique ops per port
- empty `allow` valid only for per-operation echo-safe blocks

## Idempotency

Block capability:
```yaml
idempotency:
  store:
    port: idempotency
    getOp: get
    putOp: put
```

Operation usage:
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

Rules:
- `mode=use` requires block idempotency store
- `mode=use` requires exactly one of `key` / `keyFromInputs`
- key fields must exist in that operation inputs
- operation `uses` must include idempotency `getOp` and `putOp`
- `mode=none` forbids key fields

## Invariants

Block-level `invariants` define allowed rules; operation invariants choose subset.

```yaml
invariants:
  - kind: non_negative
    scope: result
    field: balanceCents
    params: {}
```

Kinds:
- `non_negative`
- `non_empty`
- `equals`
- `one_of`

Rules:
- `scope` must be `result`
- `field` must reference operation outputs
- operation invariants must be subset of block allowed set by deterministic fingerprint

## Impl Allowed Deps (Optional)

```yaml
impl:
  allowedDeps:
    - maven: com.fasterxml.jackson.core:jackson-databind
      version: 2.17.2
```

Rules:
- exact `groupId:artifactId`
- pinned exact version
- duplicate GA invalid

## Generator Contract

Generated artifacts:
- shared: `BearValue`, `<Block>Logic`, ports, `<Block>Impl` stub
- per operation:
  - `<Block>_<Operation>Request`
  - `<Block>_<Operation>Result`
  - `<Block>_<Operation>` wrapper

Wrapper behavior:
- idempotency and invariants are wrapper-owned
- idempotency key includes operation identity segment

## Minimal Example

```yaml
version: v1
block:
  name: Withdraw
  kind: logic
  operations:
    - name: ExecuteWithdraw
      contract:
        inputs:
          - name: txId
            type: string
        outputs:
          - name: balance
            type: decimal
      uses:
        allow:
          - port: ledger
            ops: [getBalance, setBalance]
          - port: idempotency
            ops: [get, put]
      idempotency:
        mode: use
        key: txId
      invariants:
        - kind: non_negative
          field: balance
  effects:
    allow:
      - port: ledger
        ops: [getBalance, setBalance]
      - port: idempotency
        ops: [get, put]
  idempotency:
    store:
      port: idempotency
      getOp: get
      putOp: put
  invariants:
    - kind: non_negative
      field: balance
```

## Commands

For each changed IR:
1. `bear validate <ir-file>`
2. `bear compile <ir-file> --project <repoRoot>` or `bear compile --all --project <repoRoot>`
3. `bear fix <ir-file> --project <repoRoot>` (or `fix --all`)
4. `bear check <ir-file> --project <repoRoot> [--strict-hygiene]` (or `check --all`)
