# Foundations

This repository is a proof-of-concept reference implementation.
It is not a production-ready framework; it exists to validate and demonstrate deterministic boundary governance for agentic backend development.


For the fastest onboarding use [QUICKSTART.md](QUICKSTART.md).
For a one-page summary of the hypothesis and workflow, see [OVERVIEW.md](OVERVIEW.md).

## Why BEAR exists

BEAR exists to make AI-assisted and agentic backend development safer without killing delivery speed.
In agent-heavy workflows, code can evolve quickly, but boundary expansion and generated-artifact drift can become hard to see.
BEAR addresses this with deterministic contracts and deterministic gates.

## Core philosophy

- Keep implementation velocity high inside declared boundaries.
- Make boundary power changes explicit and reviewable.
- Prefer deterministic contract enforcement over process or prompt discipline.
- Produce CI-friendly, machine-parseable outputs and stable exit semantics.
- Enforce only declared semantics that are supportable by target wrappers/ports.

## BEAR IR fundamentals

BEAR IR is a constrained YAML contract for one governed block.
It declares block boundary authority and one or more typed operations, then drives deterministic generation and checks.

Typical shape:

```yaml
version: v1
block:
  name: InventorySync
  kind: logic
  operations:
    - name: ApplyRestock
      contract:
        inputs:
          - name: restockId
            type: string
        outputs:
          - name: availableUnits
            type: int
      uses:
        allow:
          - port: inventoryStore
            ops: [getUnits, setUnits]
          - port: idempotency
            ops: [get, put]
      idempotency:
        mode: use
        key: restockId
  effects:
    allow:
      - port: inventoryStore
        ops: [getUnits, setUnits]
      - port: idempotency
        ops: [get, put]
  idempotency:
    store:
      port: idempotency
      getOp: get
      putOp: put
```

Key idea:

- IR is the declared boundary contract.
- `compile` materializes deterministic generated artifacts from that contract.
- `check` enforces consistency and policy against that contract.
- `pr-check` classifies contract deltas against base branch for governance.
- invariant/idempotency semantics in scope are enforced in BEAR-owned wrappers (not by impl conventions).

## Agent workflow and developer visibility

BEAR is designed for agent-driven workflows.
In the intended model, the agent updates IR when it needs new boundary authority; developers mostly review governance signals.

Typical agent loop:

1. update IR and implementation from project specs
2. run `bear validate`
3. run `bear compile` or `bear fix`
4. run `bear check`
5. run `bear pr-check` against base for governance classification

Developer-facing visibility:

- PR signal: `pr-check` classifies boundary-expanding vs ordinary changes.
- CI signal: deterministic exit codes and failure footer let teams enforce stable merge gates.
- Local triage signal: consistent output ordering and path normalization make failures actionable quickly.

## Preview scope mindset

Preview focuses on structural contract enforcement, deterministic diagnostics, and boundary governance.
It is intentionally not a business-rules engine and does not claim full runtime semantics.
Enforcement coverage is intentionally bounded to supported targets/surfaces in Preview.

## CLI architecture at a glance

BEAR CLI is split into two modules:

- `kernel/`: deterministic trusted seed for IR parsing, validation, normalization, and target abstractions.
- `app/`: CLI orchestration (`validate`, `compile`, `fix`, `check`, `unblock`, `pr-check`) and contract rendering.

## Related

- [OVERVIEW.md](OVERVIEW.md)
- [MODEL.md](MODEL.md)
- [ENFORCEMENT.md](ENFORCEMENT.md)
- [PR_REVIEW.md](PR_REVIEW.md)
- [CONTRACTS.md](CONTRACTS.md)

