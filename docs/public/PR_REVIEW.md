# PR / CI Review Guide

BEAR is designed so humans can review agent changes via deterministic signals, without needing to understand every implementation detail.


```mermaid
%%{init: {"theme":"base","themeVariables":{
  "fontFamily":"ui-sans-serif, system-ui",
  "lineColor":"#64748B",
  "textColor":"#E2E8F0",
  "background":"#0F172A",
  "primaryColor":"#111827",
  "primaryBorderColor":"#475569",
  "primaryTextColor":"#E2E8F0",
  "secondaryColor":"#0B1220",
  "secondaryBorderColor":"#334155",
  "tertiaryColor":"#0B1220",
  "tertiaryBorderColor":"#334155",
  "noteBkgColor":"#1E293B",
  "noteBorderColor":"#475569",
  "noteTextColor":"#E2E8F0"
}}}%%
flowchart LR
  GOV[Governed source roots]:::groupGov
  GEN[Generated artifacts]:::groupGen
  APP[App / adapters]:::groupApp

  GOV -->|compile uses IR| GEN
  APP -->|implements ports| GEN
  GOV -->|calls only declared ports| GEN

  B1[blocks/blockA/...]:::gov --> GOV
  B2[blocks/blockB/...]:::gov --> GOV
  SH[blocks/_shared/...]:::gov --> GOV

  W[wiring files]:::gen --> GEN
  P[ports + wrappers]:::gen --> GEN

  A1[framework + infra]:::app --> APP
  A2[external clients]:::app --> APP

  X((Violation)):::bad
  GOV -->|undeclared reach| X
  APP -->|bypass into governed| X

  classDef groupGov fill:#111827,stroke:#818CF8,color:#E2E8F0;
  classDef groupGen fill:#111827,stroke:#64748B,color:#E2E8F0;
  classDef groupApp fill:#111827,stroke:#10B981,color:#E2E8F0;

  classDef gov fill:#0B1220,stroke:#818CF8,color:#E2E8F0;
  classDef gen fill:#0B1220,stroke:#64748B,color:#E2E8F0;
  classDef app fill:#0B1220,stroke:#10B981,color:#E2E8F0;

  classDef bad fill:#3F0A0A,stroke:#F87171,color:#E2E8F0;
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


