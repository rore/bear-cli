# BEAR Agent Package (Portable v0.3)

This directory is the source of truth for BEAR-distributed package files copied into adopter repos.

This package is generic and domain-neutral. It provides BEAR operating contracts, IR guidance, deterministic gate usage, and failure routing without app-specific hints.

Canonical source rule:
- `docs/bear-package/.bear/` is the only canonical distributable bundle.
- root-level files in `docs/bear-package/` are packaging helpers (`README.md`, `AGENTS.md`, `AGENTS_SHIM.md`), not duplicate bundle content.

Self-contained reference rule:
- package files must reference only paths that exist inside the distributed package (`.bear/agent/**`) plus project-local BEAR artifacts/IR files.
- do not point agents to non-shipped repo docs.

Compatibility note (hard cutover):
- replaced `.bear/agent/BEAR_AGENT.md` with `.bear/agent/BOOTSTRAP.md`
- replaced `.bear/agent/WORKFLOW.md` with split docs:
  - `.bear/agent/CONTRACTS.md`
  - `.bear/agent/TROUBLESHOOTING.md`
  - `.bear/agent/REPORTING.md`
- replaced `.bear/agent/doc/IR_QUICKREF.md` + `.bear/agent/doc/IR_EXAMPLES.md` with `.bear/agent/ref/IR_REFERENCE.md`

Minimum agent context carried by the package:
- startup contract and routing (`.bear/agent/BOOTSTRAP.md`)
- normative policy/wiring contracts (`.bear/agent/CONTRACTS.md`)
- deterministic failure routing (`.bear/agent/TROUBLESHOOTING.md`)
- completion schema contract (`.bear/agent/REPORTING.md`)
- IR authority (`.bear/agent/ref/IR_REFERENCE.md`)
- index rules (`.bear/agent/ref/BLOCK_INDEX_QUICKREF.md`)
- conceptual framing (`.bear/agent/ref/BEAR_PRIMER.md`)
- required project-local inspection targets (`spec/*.bear.yaml`, `bear.blocks.yaml`, `build/generated/bear/**` when present)
- vendored CLI runtime (`.bear/tools/bear-cli/**`)

Canonical command surface expected by the package:
- `bear validate`
- `bear compile`
- `bear fix`
- `bear check`
- `bear pr-check`

## Package Contract

1. BEAR package is a copyable bundle dropped into any backend project.
2. Package content is limited to:
- BEAR operating instructions
- BEAR IR reference and examples
- canonical gate usage and failure triage
3. Package content explicitly excludes:
- project/domain specs
- scenario runbooks
- evaluation answer keys
- decomposition hints tied to one app/domain

## Package Layout (Single Folder)

Canonical layout in adopter repos:

```text
<repoRoot>/.bear/agent/
  BOOTSTRAP.md
  CONTRACTS.md
  TROUBLESHOOTING.md
  REPORTING.md
  ref/BEAR_PRIMER.md
  ref/IR_REFERENCE.md
  ref/BLOCK_INDEX_QUICKREF.md
<repoRoot>/.bear/policy/
  reflection-allowlist.txt
  hygiene-allowlist.txt
<repoRoot>/.bear/tools/bear-cli/
  bin/bear(.bat)
  lib/*.jar
```

Bootstrap entrypoint at repo root:
- `AGENTS.md` (project-owned or template) points to `.bear/agent/BOOTSTRAP.md`

Bundle source path in this repository:
- [`docs/bear-package/.bear/`](.bear/)

## Distributed File Set

Required package files:
- `.bear/agent/BOOTSTRAP.md`
- `.bear/agent/CONTRACTS.md`
- `.bear/agent/TROUBLESHOOTING.md`
- `.bear/agent/REPORTING.md`
- `.bear/agent/ref/BEAR_PRIMER.md`
- `.bear/agent/ref/IR_REFERENCE.md`
- `.bear/agent/ref/BLOCK_INDEX_QUICKREF.md`
- `.bear/policy/reflection-allowlist.txt`
- `.bear/policy/hygiene-allowlist.txt`
- `.bear/tools/bear-cli/bin/bear` / `.bear/tools/bear-cli/bin/bear.bat`
- `.bear/tools/bear-cli/lib/*.jar`
- `AGENTS_SHIM.md`

Optional package file:
- `AGENTS.md` (template only; use when project has no existing root `AGENTS.md`)

## Multi-Block Governance Requirement

When a project has multiple governed BEAR blocks:
1. `bear.blocks.yaml` is mandatory.
2. Canonical gates must run `--all` variants (`check --all` / `pr-check --all`).
3. Removing index files to bypass `--all` governance is invalid workflow.
4. Canonical agent done-gate evidence requires both:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
5. Completion report must include governance-signal disposition fields from `.bear/agent/REPORTING.md`.

## Integration Rule: Existing `AGENTS.md`

When `AGENTS.md` already exists:
1. Do not replace project-owned `AGENTS.md`.
2. Append the one-line pointer from `AGENTS_SHIM.md`.
3. Add BEAR package files under `.bear/agent/*`.
