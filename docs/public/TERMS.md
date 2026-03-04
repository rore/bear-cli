# Terms

This page defines the minimal vocabulary used in BEAR docs and outputs.

## Core concepts

- `block`: one governed unit of backend logic.
- `IR`: a small YAML contract (for example `spec/<block>.bear.yaml`) that declares a block's API (inputs/outputs) and boundary authority.
- `operation`: one entrypoint inside a block (declared under `block.operations`).

## Boundary vocabulary

- `effect` / `effects.allow`: the block's declared capability boundary.
- `port`: a named dependency surface inside `effects.allow` or `uses.allow`.
- `uses.allow`: per-operation subset of the block boundary.
  - Rule: every `uses.allow` entry must be subset-or-equal to `effects.allow`.

Port entry kinds:
- Important: this is a `port.kind` distinction. `block.kind` is unchanged and remains `logic` in v1.
- `kind=external`: capability to an external dependency.
  - Uses `ops` (for example `routeStore.getRoute`, `routeStore.putRoute`).
- `kind=block`: dependency on another BEAR block.
  - Uses `targetBlock` + `targetOps` (operation names on the target block).
  - Cross-block calls are routed through generated block-port clients; direct target internals/wrapper bypass is policy-failing.

## Generated surface

- `wrapper`: generated code that forms the governed integration surface and owns wrapper-enforceable semantics (for example idempotency/invariant checks).
- `ports (generated)`: generated interfaces that represent the allowed boundary; implementations belong under governed source roots.

## Governance signals

- `boundary expansion`: a change that widens declared boundary authority (new ports/ops or targetOps, new operations, idempotency/invariant changes, etc.). Detected by `bear pr-check`.
- `boundary bypass`: code shape that reaches around the governed surface (for example implementing generated ports outside governed roots or direct cross-block wrapper bypass). Detected by `bear check` / `bear pr-check`.
- `drift`: generated artifacts are stale/missing/edited compared to what BEAR would deterministically produce from IR.

## Example (conceptual)

```yaml
block:
  operations:
    - name: PlanDelivery
      uses:
        allow:
          - port: routeStore
            kind: external
            ops: [getRoute, putRoute]
          - port: etaService
            kind: block
            targetOps: [EstimateEta]
  effects:
    allow:
      - port: routeStore
        kind: external
        ops: [getRoute, putRoute]
      - port: etaService
        kind: block
        targetBlock: shipping-eta
        targetOps: [EstimateEta]
```

In this example:
- the block has external reach to `routeStore` and a cross-block dependency on `shipping-eta`
- `PlanDelivery` uses only a subset of declared capabilities

## Where the full details live

- Agent IR reference: [IR_REFERENCE.md](../bear-package/.bear/agent/ref/IR_REFERENCE.md)
- Canonical IR spec (bear-cli maintainer): [docs/context/ir-spec.md](../context/ir-spec.md)

