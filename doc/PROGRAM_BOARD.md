# BEAR Program Board

## Last Updated

2026-02-19

## Current Milestone

`Preview Release` (active)

## Milestone Pipeline

`v0 complete -> M1 complete -> M1.1 complete -> Preview Release active -> P2 -> P3`

## Interpretation Guardrails

- This file tracks milestone closure state and queue order.
- This file is not the canonical milestone feature-definition document.
- For "what features are in Preview?", use `doc/ROADMAP.md` -> `Preview Release` -> `Preview contract (must ship)`.

## Milestone Closure Checklist (Active)

Preview Release closure checks:

1. `bear validate` contract is frozen and deterministic.  
   Status: `DONE`  
   Evidence: `spec/commands/validate.md`, `app/src/test/java/com/bear/app/BearCliTest.java`
2. `bear compile` ownership and deterministic regeneration contract is frozen.  
   Status: `DONE`  
   Evidence: `spec/commands/compile.md`, `app/src/test/java/com/bear/app/BearCliTest.java`
3. `bear check` drift gate + deterministic diff categories are frozen.  
   Status: `DONE`  
   Evidence: `spec/commands/check.md`, `app/src/test/java/com/bear/app/BearCliTest.java`
4. `bear check` test-stage semantics and lock classification are frozen.  
   Status: `DONE`  
   Evidence: `spec/commands/check.md`, `doc/releases/PREVIEW_CHECKPOINT_2026-02-19.md`
5. `bear pr-check` deterministic boundary expansion verdict is frozen (`0` / `5`).  
   Status: `DONE`  
   Evidence: `spec/commands/pr-check.md`, `app/src/test/java/com/bear/app/BearCliTest.java`
6. Failure envelope (`CODE/PATH/REMEDIATION`) is standardized on non-zero exits.  
   Status: `DONE`  
   Evidence: `spec/commands/exit-codes.md`, `app/src/test/java/com/bear/app/BearCliTest.java`
7. Preview undeclared-reach enforcement is active for covered JVM surfaces.  
   Status: `DONE`  
   Evidence: `spec/commands/check.md`, `doc/INVARIANT_CHARTER.md`
8. Single preview exit-code registry is documented and command contracts align.  
   Status: `DONE`  
   Evidence: `spec/commands/exit-codes.md`, `spec/commands/check.md`, `spec/commands/pr-check.md`
9. User/operator guidance is published (including lock troubleshooting).  
   Status: `DONE`  
   Evidence: `doc/USER_GUIDE.md`, `doc/releases/PREVIEW_CHECKPOINT_2026-02-19.md`
10. Self-hosting acceptance evidence (clean-clone style non-ritual proof) is explicitly captured.  
    Status: `OPEN`  
    Evidence: `PENDING`
11. Preview demo includes at least one external integration reachable only via declared generated port/op.  
    Status: `OPEN`  
    Evidence: `PENDING`
12. Preview closure note includes explicit pass/fail mapping for all checklist items.  
    Status: `OPEN`  
    Evidence: `PENDING`

## Evidence Ledger

- Preview checkpoint: `doc/releases/PREVIEW_CHECKPOINT_2026-02-19.md`
- Exit/failure envelope registry: `spec/commands/exit-codes.md`
- `check` contract: `spec/commands/check.md`
- `pr-check` contract: `spec/commands/pr-check.md`
- Invariant status source: `doc/INVARIANT_CHARTER.md`
- M1/M1.1 runbooks and evidence framing: `doc/m1-eval/RUN_MILESTONE.md`, `doc/m1-eval/RUN_MULTI_BLOCK.md`

## Ready Queue (Ordered, Execution Work Items)

1. `doc/backlog/P1_PREVIEW_CLOSURE_GAPS.md`
2. `doc/backlog/P2_BEAR_FIX_GENERATED_ONLY.md`

## Backlog Buckets (P1/P2/P3)

- `P1`
  - `doc/backlog/P1_PREVIEW_CLOSURE_GAPS.md`
- `P2`
  - `doc/backlog/P2_BEAR_FIX_GENERATED_ONLY.md`
- `P3`
  - none queued yet

## Open Risks / Decisions

- Decision required: whether preview scope keeps mandatory external integration in demo, or formally scopes it out with an explicit release note.
- Risk: self-hosting requirement is stated in roadmap, but acceptance evidence is not currently centralized in release artifacts.
- Risk: historical references still pointing to `doc/ROADMAP_V0.md` can reintroduce drift if not cleaned.
