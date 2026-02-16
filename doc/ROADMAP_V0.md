# BEAR Execution Roadmap (Post-v0)

Historical note:
- File name remains `ROADMAP_V0.md` for continuity.
- Content now tracks post-v0 execution milestones.

Use this file for:
- milestone ordering
- concrete deliverables
- done criteria for near-term execution

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

2. `M1.1` Governance Signal Hardening (next)
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

CLI scope (must ship):
- stable `validate`, `compile`, `check` contracts with documented deterministic exit codes
- deterministic drift/baseline behavior and actionable recovery guidance
- unmissable, greppable boundary-expansion signaling
- clean single-file operation semantics (repo scripts can iterate deterministically over many IR files)

Demo scope (must ship):
- small banking slice with at least 3 blocks:
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
- full static architecture enforcement beyond current demo-grade hardening

Definition of Done:
- CLI:
  - deterministic validate/compile/check contracts and documented exit codes
  - check includes project test gate after drift pass
  - boundary expansion surfaced clearly and consistently
- Demo:
  - 3+ blocks, persistence, and one external integration via declared port/op
  - canonical gate scripts and agent workflow docs in-repo
  - documented non-boundary + boundary-expanding scenario runbooks
  - undeclared-reach guard enabled in verification
- Usability:
  - new user can run green quickly
  - agent can complete core non-boundary and boundary-expanding journeys from repo alone

4. `Resource Packaging and Versioning` (future)
Goal:
- industrialize distribution of BEAR-owned agent resources when maturity justifies packaging design

Not immediate after M1:
- revisit after preview milestone feedback

Potential scope:
- versioned resource bundles
- import/export automation
- checksum/lock/provenance automation

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
