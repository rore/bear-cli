# Terms

This page defines the minimum vocabulary needed to read BEAR output and docs.

## Core

- `block`: one governed backend unit.
- `operation`: one entrypoint inside a block.
- `IR`: YAML boundary declaration for a block (`bear-ir/<block>.bear.yaml`).

## Boundary

- `effects.allow`: block-level capability boundary.
- `uses.allow`: per-operation subset of `effects.allow`.
- `port`: named dependency surface in `effects.allow` / `uses.allow`.
- `ops`: allowed actions on an external port.

Port kinds:
- `kind=external`: uses `ops`.
- `kind=block`: cross-block call contract.
  - in `effects.allow`: requires `targetBlock` + `targetOps`
  - in `uses.allow`: `targetBlock` is forbidden; optional `targetOps` narrows scope

Important distinction:
- `port.kind` can be `external` or `block`.
- `block.kind` remains `logic` in v1.

## Ownership

- `governedSourceRoots`: BEAR-owned source roots used for containment and bypass checks.
- `wrapper`: generated integration surface BEAR enforces.

## Signals

- `drift`: generated artifacts are stale/missing/edited.
- `boundary bypass`: code shape reaches around governed surfaces (`exit 7`).
- `boundary expansion`: IR delta widens declared authority (`pr-check` exit `5`).

## Deep References

- Agent IR reference: [IR_REFERENCE.md](../bear-package/.bear/agent/ref/IR_REFERENCE.md)
- Canonical IR spec (maintainer): [docs/context/ir-spec.md](../context/ir-spec.md)
