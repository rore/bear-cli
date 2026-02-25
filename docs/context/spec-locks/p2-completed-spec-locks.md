# P2 Completed Spec Locks

Source:
- extracted from `docs/context/program-board.md` during context compaction.

## Next Feature Specs (Locked)

### 1) Multi-block port implementer guard (`P2` completed)

Goal:
- prevent structural collapse where one adapter class implements generated ports owned by multiple blocks.

Scope:
- JVM/Java.
- structural governance only (no style/location policing inside one block).

Detection contract:
- scan Java classes in `src/main/java/**`.
- for each class, collect implemented generated interfaces matching:
  - FQCN starts with `com.bear.generated.`
  - simple name ends with `Port`
- resolve owning block using wiring identity (`entrypointFqcn` package mapping), not package-prefix heuristics.
- owner resolution outcomes:
  - ambiguous owner => `MANIFEST_INVALID` (`exit=2`)
  - missing owner in current manifest scope => ignore (no fail)

Rule:
- if a class implements generated `*Port` interfaces from more than one owning block, fail unless exception marker is present.

Exception marker:
- exact line text: `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL`
- marker must be in the same file and appear within 5 non-empty lines above class declaration.
- marker is valid only under `src/main/java/blocks/_shared/**`; elsewhere it is a deterministic failure (`KIND=MARKER_MISUSED_OUTSIDE_SHARED`).

Failure envelope:
- `exit=7`
- `CODE=BOUNDARY_BYPASS`
- `RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN`
- `PATH=<repo-relative source file>`
- `DETAIL=KIND=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: <implClassFqcn> -> <sortedGeneratedPackageCsv>`
- remediation: move adapters so each class serves one owning block, or place intentional cross-block adapter under `_shared` with explicit marker.

Determinism:
- source traversal sorted by repo-relative path.
- findings sorted by `(path, rule, detail)`.

### 2) General agent done-gate hardening (`P2` completed)

Contract:
- agent completion requires both commands green:
  - `bear check --all --project <repoRoot>`
  - `bear pr-check --all --project <repoRoot> --base <ref>`
- completion reports must include both gate results.

Docs/package updates required:
- `docs/public/commands-check.md`
- `docs/public/commands-pr-check.md`
- `docs/context/user-guide.md`
- `docs/bear-package/.bear/agent/BOOTSTRAP.md`
- `docs/bear-package/.bear/agent/CONTRACTS.md`
- `docs/bear-package/.bear/agent/TROUBLESHOOTING.md`
- `docs/bear-package/.bear/agent/REPORTING.md`

### 3) Wiring drift diagnostics (`P2` completed)

Goal:
- eliminate guesswork on wiring drift failures.

Contract:
- when generated wiring drift is detected, output exact drifted wiring paths and reason class:
  - `ADDED`
  - `REMOVED`
  - `CHANGED`
  - `MISSING_BASELINE`
- keep deterministic sorted path order.
- keep one canonical remediation step (`bear fix` or compile/regenerate path) in envelope.
- do not change exit taxonomy for drift.

### 4) `_shared` allowedDeps policy (`P2` completed)

Direction lock:
- no IR schema changes in this slice.
- path-scoped shared policy is implemented at `spec/_shared.policy.yaml`.
- shared policy add/change are boundary-expanding in `pr-check`; removal is ordinary.
- `pr-check --all` renders shared-policy deltas once in `REPO DELTA:` before `SUMMARY`.
