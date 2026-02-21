# IR_QUICKREF.md

Purpose:
- Fast, authoritative reference for valid BEAR IR v1.2.

## Required Root Shape

```yaml
version: v1
block:
  ...
```

Required keys:
- `version` (`v1`)
- `block`

## `block` Object

Required:
- `name`
- `kind` (`logic`)
- `contract`
- `effects`

Optional:
- `idempotency`
- `invariants`
- `impl`

## Contract

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
- `inputs` non-empty
- `outputs` non-empty
- unique input/output names

Types:
- `string`
- `decimal`
- `int`
- `bool`
- `enum`

## Effects

```yaml
effects:
  allow:
    - port: stateStore
      ops: [get, put]
```

Rules:
- `allow` required (may be empty)
- unique ports
- unique ops per port

## Idempotency (Optional)

Single-field key:
```yaml
idempotency:
  key: requestId
  store:
    port: idempotency
    getOp: get
    putOp: put
```

Composite key:
```yaml
idempotency:
  keyFromInputs: [walletId, requestId]
  store:
    port: idempotency
    getOp: get
    putOp: put
```

Rules:
- exactly one of `key` / `keyFromInputs`
- `keyFromInputs` non-empty, ordered, unique values
- referenced fields must exist in `contract.inputs`
- `store` references must exist in declared effects

## Invariants (Optional)

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
- `field` must exist in outputs
- kind/type compatibility is enforced
- kind-specific params are strict

## Impl allowed deps (Optional)

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

## Key v1.2 Enforcement Notes
- Idempotency and invariants are wrapper-owned semantics.
- Idempotent logic signatures exclude idempotency port.
- IR-declared semantics must be enforceable by target; otherwise `check` fails.

Selection rule (why these semantics):
- BEAR enforces semantics only when they are deterministic and wrapper-enforceable from declared inputs/outputs/ports.
- Idempotency qualifies because key material, effect boundary, and replay payload are all explicitly declared.
- Invariants are limited to structural output checks for the same reason.
- Semantics requiring hidden context are out of scope.

Canonical details:
- BEAR package canonical rule (self-contained):
  - enforce only semantics that are wrapper-enforceable from declared inputs/outputs/ports
  - require no hidden context unless explicitly declared
  - require deterministic target implementation
  - require frozen, testable contracts

## Commands
For each changed IR:
1. `bear validate <ir-file>`
2. `bear compile <ir-file> --project <repoRoot>`
3. `bear fix <ir-file> --project <repoRoot>` (or `fix --all`)
4. `bear check <ir-file> --project <repoRoot>` (or `check --all`)
