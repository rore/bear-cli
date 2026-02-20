# IR_QUICKREF.md

Purpose:
- Fast, authoritative reference for writing valid BEAR IR in v1.

Use this file plus `doc/IR_EXAMPLES.md` as the IR source of truth.
Do not reverse engineer CLI binaries to discover schema.

## Required Root Shape

```yaml
version: v1
block:
  ...
```

Required keys:
- `version` (must be `v1`)
- `block`

## `block` Object

Required keys:
- `name` (string)
- `kind` (must be `logic` in v1 preview)
- `contract`
- `effects`

Optional keys:
- `idempotency`
- `invariants`
- `impl`

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

Allowed field types in v1 preview:
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
- v1 preview supports `kind: non_negative`
- `field` must reference an output field name

## Impl allowed dependencies (Optional)

```yaml
impl:
  allowedDeps:
    - maven: com.fasterxml.jackson.core:jackson-databind
      version: 2.17.2
```

Rules:
- `maven` must be exact `groupId:artifactId` (no wildcards)
- `version` must be pinned (no ranges/wildcards)
- duplicate `groupId:artifactId` entries are invalid
- add/version-change is boundary-expanding in `bear pr-check`

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

If request needs a new pure library in impl logic:
- add/update `impl.allowedDeps`

## Commands (Deterministic Order)

For each changed IR:
1. `bear validate <ir-file>`
2. `bear compile <ir-file> --project <repoRoot>`
3. `bear fix <ir-file> --project <repoRoot>` (or `bear fix --all --project <repoRoot>` when indexed) when generated artifacts need deterministic repair
4. `bear check <ir-file> --project <repoRoot>` or `bear check --all --project <repoRoot>`

If IR declares `impl.allowedDeps` on Java+Gradle projects:
5. ensure project applies `build/generated/bear/gradle/bear-containment.gradle`
6. run Gradle build/test once so `build/bear/containment/applied.marker` is written
7. rerun `bear check`

