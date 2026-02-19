# BEAR Execution Roadmap (Post-v0)

Historical note:
- File name remains `ROADMAP_V0.md` for continuity.
- Content tracks post-v0 execution milestones and preview-scope contract hardening.

Use this file for:
- milestone ordering
- concrete deliverables
- done criteria for near-term execution
- prioritized backlog after preview

Use `doc/ROADMAP.md` for:
- long-horizon strategy and phase intent

## Status Snapshot

- v0 core is complete.
- M1 workflow proof is complete.
- active milestone is M1.1 governance signal hardening.
- preview release milestone follows M1.1.

## Ordered Milestone Plan

1. `M1` Workflow Proof (completed)
Goal:
- prove isolated BEAR-aware agent workflow in the demo repo

Deliver:
- demo-local BEAR workflow assets (`BEAR_PRIMER.md`, `AGENTS.md`, `BEAR_AGENT.md`, `WORKFLOW.md`)
- canonical gate scripts and wrapper scripts
- realistic scenario branches (`scenario/greenfield-build`, `scenario/feature-extension`)
- no demo answer-key hints

Done:
- isolated agent completes one non-boundary feature and one boundary-expanding feature
- both flows terminate through the same canonical gate command

2. `M1.1` Governance Signal Hardening (active)
Goal:
- make boundary governance CI-native and ordering-independent

Deliver:
- PR/base-branch boundary diff mode (`bear pr-check` or equivalent)
- deterministic boundary delta output (ports/ops/effects/contract/invariants)
- CI-ready boundary-expansion status signal
- normalized exit-code semantics for stale/boundary paths

Done:
- PR runs classify boundary deltas deterministically
- CI can gate on explicit boundary expansion without relying on local stale-check ordering

3. `Preview Release` (target milestone)
Goal:
- ship a credible, tryable preview on a realistic multi-block banking slice

Entry criteria:
- M1 accepted
- M1.1 accepted

Preview philosophy and constraints (frozen):
- BEAR is a deterministic structural containment layer for agent-generated code.
- BEAR is a compile/CI gate, not a full harness platform.
- deterministic only; no background services.
- two-file ownership model is mandatory:
  - generated artifacts are BEAR-owned and overwriteable
  - impl code is user/agent-owned and never overwritten
- `bear check` is the canonical CI gate.
- all commands must emit stable exit codes and greppable output lines.
- preview invariant contract is defined in `doc/INVARIANT_CHARTER.md`.
- preview "must enforce" invariants are charter preview items 1-7 (with explicit preview scope caveat on undeclared-reach coverage).

Preview feature contract (must all ship with acceptance criteria):

3.1 `bear validate <ir-file>`
- strict schema + semantic validation with fail-fast behavior
- deterministic normalization + canonical YAML emission
- deterministic error categories + stable exit codes
Acceptance:
- same valid IR emits byte-identical canonical output
- invalid IR fails deterministically with stable category/code

3.2 `bear compile <ir-file> --project <path>`
- generates BEAR-owned artifacts and initial impl skeletons
- never overwrites existing impl code
- deterministic file layout/content
- deterministic stdout summary of generated files
Acceptance:
- repeated compile on unchanged IR is byte-identical in BEAR-owned tree
- existing impl files remain unchanged

3.3 Drift detection inside `bear check`
- deterministic compare of project BEAR-owned artifacts vs expected generated output
- deterministic diff summary with reason categories (`ADDED`, `REMOVED`, `CHANGED`, `MISSING_BASELINE`)
- dedicated drift exit code
Acceptance:
- drift output is deterministic and greppable
- project tests do not run when drift is present

3.4 Project test execution inside `bear check`
- project tests run only if drift passes
- deterministic mapping of test failure/timeout to output + exit code
Acceptance:
- stable output and exit semantics for test failures

3.5 PR boundary expansion signal (`bear pr-check <ir-file> --project <path> --base <ref>` or `bear check --pr`)
- concise deterministic boundary delta summary:
  - ports added/removed/changed
  - effects allowlist changes
  - contract input/output changes
- output must be minimal, stable, greppable
- boundary expansion policy has dedicated exit code
Acceptance:
- boundary expansion returns `exit 5`
- no boundary expansion returns `exit 0`

3.6 Actionable failures (hard product requirement)
For every failure mode, output must include:
- `CODE=<stable-identifier>`
- `PATH=<file-or-ir-path>`
- `REMEDIATION=<exact next step>`
Acceptance:
- all non-zero exits in `validate`, `compile`, `check`, and `pr-check` include the three fields
- remediation is short, deterministic, and immediately executable by an agent
- coverage includes non-functional failure paths (usage, IO, git/base resolution, wrapper missing, permission errors, internal error category)

3.7 Demo-grade "no undeclared reach" enforcement (required)
Canonical preview escape class:
- direct HTTP client usage bypassing declared ports

Required behavior:
- deterministic check identifies violating file/path
- deterministic failure with explicit remediation (`declare port`, `regenerate`, `use generated gateway`)
- hard to bypass accidentally in demo scenario
- runs in `bear check` after drift pass and before tests
- dedicated exit code: `6` (`UNDECLARED_REACH`)
- detection scope (preview JVM contract): fail on direct usage of these client surfaces in impl code:
  - `java.net.http.HttpClient`
  - `java.net.URL#openConnection`
  - `okhttp3.OkHttpClient`
  - `org.springframework.web.client.RestTemplate`
  - `java.net.HttpURLConnection`
- exclusions:
  - generated tree under `build/generated/bear/**`
  - test sources under `src/test/**`
  - build output directories (`build/**`, `.gradle/**`)
Acceptance:
- direct HTTP usage in impl deterministically fails with `exit 6`

3.8 Self-hosting requirement (`bear` runs `bear`)
- BEAR runs on BEAR repo with normal project workflow (no special rituals)
- canonical commands are sufficient for CI and local checks
- minimum definition for preview: in a clean clone, using standard Gradle wrapper flow, BEAR commands run deterministically without bespoke script paths
- fallback scope policy if schedule risk appears: keep non-ritual CI self-hosting in this repo as the required bar; do not expand to bootstrap-purity goals in preview
Acceptance:
- deterministic success/failure behavior in clean checkout
- no developer-only custom path required

3.9 Exit code registry (single source of truth for preview)
Registry:
- `0` pass
- `2` validation/schema/semantic failure
- `3` drift failure
- `4` project test failure (including timeout)
- `5` boundary expansion detected in `pr-check`
- `6` undeclared reach detected in `check`
- `64` usage/argument failure
- `70` internal/unexpected failure
- `74` IO/git failure

Contract:
- command docs (`spec/commands/*.md`) must map to this registry or explicitly declare scoped exceptions
- CI examples must key off these numeric codes only

3.10 Failure-envelope compliance test (preview hardening)
- add deterministic contract tests that assert every non-zero command outcome includes:
  - `CODE=...`
  - `PATH=...`
  - `REMEDIATION=...`
- include usage/IO/git/internal branches, not only validation/drift/test branches

Demo scope (must ship):
- small banking slice with 2-3 blocks (target 3 when schedule allows):
  - Accounts (persistence-backed account/balance behavior)
  - Transfers/Withdraw (idempotency + no-overdraft invariant)
  - Ledger (persist and query/verify entries)
- at least one external integration reachable only through declared generated port/op (for example fraud check)
- canonical gate command (`bin/gate.*`) as definition of done
- demo-grade undeclared-reach guard in verification path
- agent workflow packaging in repo:
  - `AGENTS.md` thin bootstrap
  - `BEAR_AGENT.md` operational rules
  - `WORKFLOW.md` human triage/runbook
  - block index mapping block -> IR path -> impl path -> tests path

Scenario proof set (must include documented runbooks):
- Scenario A: non-boundary feature change (impl/tests only, gate passes)
- Scenario B: boundary-expanding feature (IR-first, stale baseline shows boundary/drift signal, regen+impl+tests, gate passes)
- Scenario C: contract evolution across blocks (optional but recommended)

Usability target:
- Journey 0: clone and run gate to green in minutes
- Journey 1: agent completes non-boundary feature from repo context alone
- Journey 2: agent completes boundary-expanding feature with explicit BEAR signal flow

Preview non-goals:
- plugin ecosystem/templates marketplace
- multi-language targets
- runtime sandboxing
- runtime orchestration/policy gateway/eval platform ownership

Definition of Done:
- A developer/agent can implement or refactor inside a block freely.
- Any boundary expansion is surfaced deterministically in PR/CI with clear summary.
- Any undeclared reach attempt for covered preview class fails deterministically with remediation.
- Workflow remains low friction: one canonical CI command and normal self-hosting.
- CLI contracts (`validate`, `compile`, `check`, `pr-check`) are deterministic, greppable, and exit-code stable.

4. `Resource Packaging and Versioning` (future)
Goal:
- industrialize distribution of BEAR-owned agent resources when maturity justifies packaging design

Not immediate after M1:
- revisit after preview milestone feedback

Potential scope:
- versioned resource bundles
- import/export automation
- checksum/lock/provenance automation

## Priority 2 (next after preview)

Invariant charter mapping:
- this section corresponds to charter Priority 2 invariants (8-12).

1. `bear fix` for generated artifacts only
- auto-normalize/rewrite generated outputs to canonical form
- never touches impl code

2. Generated structural tests
- generate tests for dependency direction and covered undeclared-reach checks
- `bear check` expects these tests to pass

3. Minimal taste-invariants rule pack
- deterministic layout/naming invariants for BEAR-owned files
- size/structure constraints on generated zones
- forbidden dependency edges between packages/modules

4. Boundary regression suite concept
- `bear/regressions/` fixtures for known bad patterns
- encode never-again failures without building an eval platform

5. Better PR diff ergonomics
- stable summarized boundary delta format
- optional deterministic explain mode

## Priority 3 (strategic extensions)

Invariant charter mapping:
- this section corresponds to charter Priority 3 invariants (13-16).

1. Capability templates
- packs that generate ports/effects scaffolding, invariants, structural tests, standard stubs

2. Broader boundary escape coverage
- deterministic checks for DB, filesystem, and messaging direct-usage bypass paths

3. Multi-block and multi-module composition hardening
- cross-block dependency constraints
- repo-wide drift and boundary expansion reporting

4. Optional policy hooks (not a policy engine)
- run project-provided deterministic checks/scripts
- map hook failures into stable BEAR categories

## Archived v0 Baseline (completed)

v0 delivered:
- deterministic `validate`, `compile`, `check`
- generated/user-owned tree ownership model
- drift gate + project test gate
- boundary-expansion signaling in check flow
- initial demo proof loop

Reference docs for v0 contract semantics:
- `doc/ARCHITECTURE.md`
- `doc/GOVERNANCE.md`
- `doc/IR_SPEC.md`
