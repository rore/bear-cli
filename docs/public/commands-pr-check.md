# bear pr-check

## Purpose

Run deterministic PR governance checks:
- classify normalized IR deltas versus merge-base (boundary expansion signaling)
- enforce generated-port implementation containment boundaries
- provide completion counterpart to `check --all` for local agent evidence before CI enforcement

## Quick use

Canonical invocation:

```text
bear pr-check --all --project <repoRoot> --base <ref>
```

Success looks like:
- `pr-check: OK: NO_BOUNDARY_EXPANSION` and exit `0`

Main failure classes:
- boundary expansion (`exit 5`)
- boundary bypass (`exit 7`)
- validation (`exit 2`)
- usage/internal/IO/git (`64/70/74`)

## Invocation forms

```text
bear pr-check <ir-file> --project <path> --base <ref> [--index <path>] [--collect=all] [--agent]
bear pr-check --all --project <repoRoot> --base <ref> [--blocks <path>] [--only <csv>] [--strict-orphans] [--collect=all] [--agent]
```

## Missing index envelope (`--all`)

When `bear.blocks.yaml` is missing, `pr-check --all` fails with the same deterministic envelope as other `--all` commands and exits `2`:

```text
index: VALIDATION_ERROR: INDEX_REQUIRED_MISSING: bear.blocks.yaml: project=.
CODE=INDEX_REQUIRED_MISSING
PATH=bear.blocks.yaml
REMEDIATION=Create bear.blocks.yaml or run non---all command
```

## Inputs and flags

- Single mode requires `<ir-file>`, `--project`, and `--base`.
- `--index <path>` is an optional override for single mode.
- for `kind=block`, single mode resolves index path as: explicit `--index` if provided, else `<project>/bear.blocks.yaml`; then validates normalized `(ir, projectRoot)` tuple membership.
- `<ir-file>` must be repo-relative.
- `--all` mode uses index selection and optional `--blocks`, `--only`, `--strict-orphans`.
- `--collect=all` collects additional findings within lanes that already run (does not force extra lane execution).
- `--agent` emits JSON-only diagnostics to stdout (`schemaVersion=bear.nextAction.v1`).
- completion workflows should pair:
  - `bear check --all --project <repoRoot>`
  - `bear pr-check --all --project <repoRoot> --base <ref>`
- CI `pr-check` remains authoritative remote enforcement; local `pr-check` is expected for fast feedback.

## Output schema and ordering guarantees

- Delta lines to `stderr`:
  - `pr-delta: <CLASS>: <CATEGORY>: <CHANGE>: <KEY>`
  - includes `_shared` policy deltas keyed as:
    - `<projectRoot>:_shared:<groupId:artifactId>@<version>`
    - changed version form: `@<old>-><new>`
- Governance signal lines to `stdout` (legacy informational path):
  - `pr-check: GOVERNANCE: MULTI_BLOCK_PORT_IMPL_ALLOWED: <relative/path>: <implClassFqcn> -> <sortedGeneratedPackageCsv>`
- Port-impl containment lines (when violated):
  - `pr-check: BOUNDARY_BYPASS: RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: <relative/path>: KIND=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: <interfaceFqcn> -> <implClassFqcn>`
  - `pr-check: BOUNDARY_BYPASS: RULE=BLOCK_PORT_IMPL_INVALID: <relative/path>: BLOCK_PORT_IMPL_INVALID: block-port interface must not be implemented in src/main/java; only generated client allowed`
  - `pr-check: BOUNDARY_BYPASS: RULE=BLOCK_PORT_REFERENCE_FORBIDDEN: <relative/path>: BLOCK_PORT_REFERENCE_FORBIDDEN: <detail>`
  - `pr-check: BOUNDARY_BYPASS: RULE=BLOCK_PORT_INBOUND_EXECUTE_FORBIDDEN: <relative/path>: BLOCK_PORT_REFERENCE_FORBIDDEN: app wiring may not directly execute inbound target wrapper: <wrapperFqcn>.execute(...)`
  - `pr-check: BOUNDARY_BYPASS: RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: <relative/path>: KIND=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: <implClassFqcn> -> <sortedGeneratedPackageCsv>`
  - `pr-check: BOUNDARY_BYPASS: RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: <relative/path>: KIND=MARKER_MISUSED_OUTSIDE_SHARED: <implClassFqcn>`
- Boundary verdict:
  - `stderr`: `pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED`
  - `stdout`: `pr-check: OK: NO_BOUNDARY_EXPANSION`
- Deterministic sort precedence for class, category, change, key.
- Shared-policy delta classing is frozen:
  - `ADDED` / `CHANGED` => `BOUNDARY_EXPANDING`
  - `REMOVED` => `ORDINARY`
- Port-impl containment findings are deterministically sorted by path, rule, then detail.
- block-port inbound wrapper deny set is deterministically derived from resolved index graph edges (`kind=block`) and sorted by target block key + op.
- app wiring lane is path-pinned to `src/main/java/com/**`.
- generated client scan scope is `build/generated/bear/src/main/java/**`.
- user root scan scope is `src/main/java/**`.
- `pr-check --all` may include a single repo-level `REPO DELTA:` section (before `SUMMARY`) for shared-policy deltas; these lines are emitted once per project root (no per-block duplication).
- Non-zero exits append failure footer as last three `stderr` lines.

Implementation note:
- `pr-check` acquires wiring manifests using deterministic temp staging + wiring-only generation.
- It does not require full compile output to be present in project working tree.

## Exit codes emitted

- `0` no boundary-expanding deltas
- `5` boundary-expanding deltas found
- `7` structural bypass (`CODE=BOUNDARY_BYPASS`)
- `2` validation failure (including shared policy parse/schema failure: `CODE=POLICY_INVALID`, `PATH=bear-policy/_shared.policy.yaml`)
- `64` usage failure
- `70` internal failure
- `74` IO or git failure

## Deterministic failure footer

Non-zero exits append:

- `CODE=<enum>`
- `PATH=<locator>`
- `REMEDIATION=<step>`

For aggregated `--all` non-zero failures, footer code is `REPO_MULTI_BLOCK_FAILED`.

## Remediation pointers

- [troubleshooting.md#boundary_expansion](troubleshooting.md#boundary_expansion)
- [troubleshooting.md#block_port_index_required](troubleshooting.md#block_port_index_required)
- [troubleshooting.md#port_impl_outside_governed_root](troubleshooting.md#port_impl_outside_governed_root)
- [troubleshooting.md#block_port_impl_invalid](troubleshooting.md#block_port_impl_invalid)
- [troubleshooting.md#block_port_reference_forbidden-or-block_port_inbound_execute_forbidden](troubleshooting.md#block_port_reference_forbidden-or-block_port_inbound_execute_forbidden)
- [troubleshooting.md#multi_block_port_impl_forbidden](troubleshooting.md#multi_block_port_impl_forbidden)
- [troubleshooting.md#io_git](troubleshooting.md#io_git)
- [troubleshooting.md#ir_validation](troubleshooting.md#ir_validation)

## Related

- [HOW_IT_WORKS.md](HOW_IT_WORKS.md)
- [commands-check.md](commands-check.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)
- [troubleshooting.md](troubleshooting.md)




## Agent JSON mode

- `--agent` writes JSON to stdout only (no prose output mixed into stdout).
- JSON includes deterministic `problems`, `clusters`, and one `nextAction` item selected by severity/rank rules.
- top-level `extensions` is always present.
- for non-`pr-check` commands, and for `pr-check` paths where governance telemetry is unavailable, `extensions` remains `{}`.
- when governance telemetry is available, `pr-check` and `pr-check --all` add `extensions.prGovernance`.
- `extensions.prGovernance` carries `schemaVersion=bear.pr-governance.v1`, deterministic `classifications[]`, `deltas[]`, `governanceSignals[]`, and aggregate flags.
- single mode uses `scope=single`; all-mode uses `scope=all` and adds `blocks[]`.
- top-level all-mode aggregate fields (`hasDeltas`, `hasBoundaryExpansion`, `classifications[]`) summarize repo-level plus per-block evidence, while top-level `deltas[]` stays repo-level only.
- `deltaId` is canonical: `<class>|<category>|<change>|<key>`.
- `governanceSignals[].details` uses deterministic lexicographic key order; current v1 signal type is `MULTI_BLOCK_PORT_IMPL_ALLOWED`.
- stream contract: BEAR itself emits no normal prose lines to stderr on normal command completion paths.

