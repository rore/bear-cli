# BEAR Agent Package (Portable v0.1)

`doc/bear-package/` is the source of truth for BEAR-distributed agent package files copied into adopter repos.

This package is generic and domain-neutral. It provides BEAR operating rules, IR guidance, and deterministic gate usage. It must not include app-specific solution hints.

Canonical command surface expected by the package:
- `bear validate`
- `bear compile`
- `bear fix`
- `bear check`
- `bear pr-check`

Containment note (v1 preview):
- if IR declares `block.impl.allowedDeps`, Java+Gradle projects must apply generated containment entrypoint:
  - `build/generated/bear/gradle/bear-containment.gradle`
- `bear check` verifies containment marker/hash and does not invoke Gradle automatically.

## Package Contract

1. BEAR package is a copyable bundle dropped into any backend project.
2. Package content is limited to:
- BEAR operating instructions
- BEAR IR quick reference
- minimal generic IR examples
- canonical gate usage and failure triage
3. Package content explicitly excludes:
- project/domain specs
- scenario runbooks
- evaluation answer keys
- decomposition hints tied to one app/domain

## Distributed File Set

Required package files:
- `BEAR_AGENT.md`
- `WORKFLOW.md`
- `doc/BEAR_PRIMER.md`
- `doc/IR_QUICKREF.md`
- `doc/IR_EXAMPLES.md`
- `doc/BLOCK_INDEX_QUICKREF.md`
- `AGENTS_SHIM.md`

Optional package file:
- `AGENTS.md` (template only; use when project has no existing root `AGENTS.md`)

Wrapper scripts are policy-dependent:
- `bin/bear-all.*` and `bin/pr-gate.*` may be shipped by adopter/project policy
- when present, agents should use them as canonical done gates

## Multi-Block Governance Requirement

When a project has multiple governed BEAR blocks:
1. `bear.blocks.yaml` is mandatory.
2. Canonical gates must run `--all` variants (`check --all` / `pr-check --all`).
3. Removing index files to bypass `--all` governance is invalid workflow.

## Integration Rule: Existing `AGENTS.md`

Many projects already own a root `AGENTS.md`.

When `AGENTS.md` already exists:
1. Do not replace project-owned `AGENTS.md`.
2. Append the one-line pointer from `AGENTS_SHIM.md`.
3. Add BEAR package files (`BEAR_AGENT.md`, `WORKFLOW.md`, and `doc/*` package docs).

