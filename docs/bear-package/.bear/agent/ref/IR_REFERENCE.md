# IR_REFERENCE.md

Purpose:
- Single authoritative BEAR IR reference.
- Consolidates schema quickref and minimal examples.

Authority rule:
- This file is the canonical IR contract for the package.
- Do not reverse engineer binaries to infer missing IR behavior.
- If a shape/rule is not declared here, do not assume it.

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

## Key v1.2 Enforcement Notes

- Idempotency and invariants are wrapper-owned semantics.
- Idempotent logic signatures exclude idempotency port.
- IR-declared semantics must be enforceable by target; otherwise `check` fails.

Selection rule:
- enforce only semantics that are wrapper-enforceable from declared inputs/outputs/ports
- require no hidden context unless explicitly declared
- require deterministic target implementation
- require frozen, testable contracts

## Minimal Examples

### Example A: Minimal Single Block

`spec/process-task.bear.yaml`

```yaml
version: v1
block:
  name: ProcessTask
  kind: logic
  contract:
    inputs:
      - name: requestId
        type: string
      - name: workloadUnits
        type: decimal
    outputs:
      - name: processed
        type: bool
      - name: remainingQuota
        type: decimal
  effects:
    allow:
      - port: quotaStore
        ops: [read, write]
      - port: idempotency
        ops: [get, put]
  idempotency:
    key: requestId
    store:
      port: idempotency
      getOp: get
      putOp: put
  invariants:
    - kind: non_negative
      scope: result
      field: remainingQuota
      params: {}
```

### Example B: Minimal Multi-Block (Indexed)

`bear.blocks.yaml`

```yaml
version: v0
blocks:
  - name: execution-core
    ir: spec/execution-core.bear.yaml
    projectRoot: .
  - name: activity-log
    ir: spec/activity-log.bear.yaml
    projectRoot: .
```

## Commands

For each changed IR:
1. `bear validate <ir-file>`
2. `bear compile <ir-file> --project <repoRoot>`
- or `bear compile --all --project <repoRoot>` when index-managed multi-block
3. `bear fix <ir-file> --project <repoRoot>` (or `fix --all`)
4. `bear check <ir-file> --project <repoRoot> [--strict-hygiene]` (or `check --all [--strict-hygiene]`)

Policy files used by `check`:
1. `.bear/policy/reflection-allowlist.txt`
2. `.bear/policy/hygiene-allowlist.txt`
