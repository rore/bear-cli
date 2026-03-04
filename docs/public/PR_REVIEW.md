# PR / CI Review Guide

BEAR is designed so humans can review agent changes via deterministic signals, without needing to understand every implementation detail.

Most PR findings become obvious once you know which "zone" a path belongs to.
Figure: what BEAR governs vs what sits outside, and the two common failure shapes (undeclared reach, bypass into governed code).
Legend: indigo = governed roots, slate = generated boundary glue, green = adapters, red = a violation.

```mermaid
%% id: bear-zones-v1
flowchart LR
  GOV[Governed code\n(source roots)]:::groupGov
  GEN[Generated boundary glue\n(wiring, ports, wrappers)]:::groupGen
  APP[App / adapters\n(infra, frameworks)]:::groupApp

  GOV -->|compile uses IR| GEN
  APP -->|implements ports| GEN
  GOV -->|calls only declared ports| GEN

  X((Violation)):::bad
  GOV -->|undeclared reach\n(import/call outside contract)| X
  APP -->|bypass into governed\n(reflection/classloading seam)| X

  classDef groupGov fill:#EEF2FF,stroke:#6366F1,color:#0B1220;
  classDef groupGen fill:#F1F5F9,stroke:#64748B,color:#0B1220;
  classDef groupApp fill:#ECFDF5,stroke:#10B981,color:#0B1220;
  classDef bad fill:#FEE2E2,stroke:#EF4444,color:#0B1220;
```
In a PR or CI job, the canonical pair is:

```text
bear check --all --project <repoRoot>
bear pr-check --all --project <repoRoot> --base <ref>
```

`check` answers: "is the repo consistent with the declared boundary and generated artifacts, and do tests pass?"

`pr-check` answers: "did the declared boundary expand compared to base?"

## How to interpret `pr-check`

### `exit 0`: no boundary expansion detected
You should still review implementation as usual, but BEAR is not warning about increased boundary authority.

### `exit 5`: boundary expansion detected
This means the IR delta widened boundary authority (for example, new effect ports/ops, new operation entrypoints, new/relaxed invariants, idempotency usage changes).

You will see `pr-delta:` lines, then a boundary verdict:

```text
pr-delta: BOUNDARY_EXPANDING: ...
pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED
CODE=BOUNDARY_EXPANSION
PATH=<ir-file>
REMEDIATION=Review boundary-expanding deltas and route through explicit boundary review.
```

Action:
- treat it as an explicit governance event
- accept (with intent) or revert

### `exit 7`: boundary bypass detected
This is not "a policy disagreement"; it is a structural bypass signal.

Example bypass line:

```text
pr-check: BOUNDARY_BYPASS: RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: <relative/path>: KIND=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: <interfaceFqcn> -> <implClassFqcn>
```

Action:
- fix the code shape (move/split adapters, remove forbidden seams)

## Where the details live

- Exact output shapes and ordering guarantees: [output-format.md](output-format.md)
- Exit code registry: [exit-codes.md](exit-codes.md)
- Full `pr-check` contract: [commands-pr-check.md](commands-pr-check.md)
- Governance policy (normative, maintainer doc): [docs/context/governance.md](../context/governance.md)


