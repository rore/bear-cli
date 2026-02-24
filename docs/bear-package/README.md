# BEAR Agent Package (Portable v0.2)

This directory is the source of truth for BEAR-distributed package files copied into adopter repos.

This package is generic and domain-neutral. It provides BEAR operating rules, IR guidance, and deterministic gate usage. It must not include app-specific solution hints.

Canonical source rule:
- `docs/bear-package/.bear/` is the only canonical distributable bundle.
- root-level files in `docs/bear-package/` are packaging helpers (`README.md`, `AGENTS.md`, `AGENTS_SHIM.md`), not duplicate bundle content.

Self-contained reference rule:
- package files must reference only paths that exist inside the distributed package (`.bear/agent/**`) plus project-local BEAR artifacts/IR files.
- do not point agents to repo docs that are not shipped in the package.

Minimum agent context carried by the package:
- startup contract (`.bear/agent/BEAR_AGENT.md`)
- deterministic operating loop (`.bear/agent/WORKFLOW.md`)
- IR schema/rules (`.bear/agent/doc/IR_QUICKREF.md`)
- IR minimal patterns (`.bear/agent/doc/IR_EXAMPLES.md`)
- index rules (`.bear/agent/doc/BLOCK_INDEX_QUICKREF.md`)
- conceptual framing (`.bear/agent/doc/BEAR_PRIMER.md`)
- required project-local inspection targets (`spec/*.bear.yaml`, `bear.blocks.yaml`, `build/generated/bear/**` when present)
- vendored CLI runtime (`.bear/tools/bear-cli/**`)

Canonical command surface expected by the package:
- `bear validate`
- `bear compile`
- `bear fix`
- `bear check`
- `bear pr-check`

Runtime distribution note:
- package includes a vendored CLI runtime under `.bear/tools/bear-cli/`
- adopters copy the full `.bear/` bundle from this directory into `<repoRoot>/.bear/`
- commands are invoked via vendored path (for example `.bear/tools/bear-cli/bin/bear(.bat) ...`)
- generated runtime support classes are canonical under `build/generated/bear/src/main/java/com/bear/generated/runtime` (legacy `build/generated/bear/runtime/**` is unsupported)
- generated logic wrappers expose `Wrapper.of(<ports...>)` as the sanctioned default wiring path in user production code
- governed logic interface -> governed impl bindings in `META-INF/services` / `module-info.java` are blocked by `bear check` seam rules
- governed impl containment is always on: execute-body logic must stay inside manifest `governedSourceRoots`
- `governedSourceRoots` is deterministic: block root first, reserved `src/main/java/blocks/_shared` second
- `pr-check` also enforces generated-port adapter containment: implementations of `com.bear.generated.*Port` must live under governed roots (block root or `_shared`)
- `check`/`pr-check` also enforce multi-block adapter isolation:
  - one class implementing generated ports from multiple generated block packages fails by default
  - explicit opt-in exists only under `_shared` with exact marker `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL` within 5 non-empty lines above class declaration
  - when opt-in is valid, `pr-check` may emit informational governance signal `MULTI_BLOCK_PORT_IMPL_ALLOWED` (non-failing) for explicit review

Containment note (v1 preview):
- if IR declares `block.impl.allowedDeps`, Java+Gradle projects must apply generated containment entrypoint:
  - `build/generated/bear/gradle/bear-containment.gradle`
- `bear check` verifies containment only when selected blocks in that `projectRoot` include at least one `impl.allowedDeps` block.
- when verification is active, `bear check` requires:
  - aggregate marker `build/bear/containment/applied.marker` with matching `hash=` and canonical `blocks=` set.
  - per-block marker `build/bear/containment/<blockKey>.applied.marker` for each required block key (`block=` + `hash=` must match).
- `bear check` does not invoke Gradle automatically.

Semantic context included in package docs:
- enforcement-by-construction (wrapper-owned idempotency + invariants)
- explicit selection rule (enforceability + determinism)
- explicit non-goals (no business-rule inference, no transaction framework semantics)

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

## Package Layout (Single Folder)

Canonical layout in adopter repos:

```
<repoRoot>/.bear/agent/
  BEAR_AGENT.md
  WORKFLOW.md
  doc/BEAR_PRIMER.md
  doc/IR_QUICKREF.md
  doc/IR_EXAMPLES.md
  doc/BLOCK_INDEX_QUICKREF.md
<repoRoot>/.bear/policy/
  reflection-allowlist.txt
  hygiene-allowlist.txt
<repoRoot>/.bear/tools/bear-cli/
  bin/bear(.bat)
  lib/*.jar
```

Bootstrap entrypoint at repo root:
- `AGENTS.md` (project-owned or template) points to `.bear/agent/BEAR_AGENT.md`

Bundle source path in this repository:
- [`docs/bear-package/.bear/`](.bear/)

## Distributed File Set

Required package files:
- `.bear/agent/BEAR_AGENT.md`
- `.bear/agent/WORKFLOW.md`
- `.bear/agent/doc/BEAR_PRIMER.md`
- `.bear/agent/doc/IR_QUICKREF.md`
- `.bear/agent/doc/IR_EXAMPLES.md`
- `.bear/agent/doc/BLOCK_INDEX_QUICKREF.md`
- `.bear/policy/reflection-allowlist.txt`
- `.bear/policy/hygiene-allowlist.txt`
- `.bear/tools/bear-cli/bin/bear` / `.bear/tools/bear-cli/bin/bear.bat`
- `.bear/tools/bear-cli/lib/*.jar`
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
4. Canonical agent done-gate evidence requires both:
   - `bear check --all --project <repoRoot>`
   - `bear pr-check --all --project <repoRoot> --base <ref>`

## Integration Rule: Existing `AGENTS.md`

Many projects already own a root `AGENTS.md`.

When `AGENTS.md` already exists:
1. Do not replace project-owned `AGENTS.md`.
2. Append the one-line pointer from `AGENTS_SHIM.md`.
3. Add BEAR package files under `.bear/agent/*`.

