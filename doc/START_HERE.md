# Start Here (bear-cli)

This file is the navigation map.
Use it to find the right source of truth quickly.
These docs are primarily for in-repo sessions (Codex/agent with repo access).

## Read next (in order)

1. `doc/STATE.md` -- current focus and next steps (keep updated).
2. `doc/ARCHITECTURE.md` -- project intent, v0 guarantees/non-guarantees, scope lock.
3. `doc/NORTH_STAR.md` -- broader motivation and long-horizon success criteria.
4. `doc/GOVERNANCE.md` -- normative IR diff classification (`ordinary` vs `boundary-expanding`) and review policy.
5. `doc/IR_SPEC.md` -- canonical v0 IR schema, validation, normalization, and demo IR shape.
6. `doc/ROADMAP_V0.md` -- concrete v0 execution plan and phase checkpoints.
7. `doc/ROADMAP.md` -- broader target roadmap beyond immediate v0 execution.
8. `doc/PROJECT_LOG.md` -- historical rationale and major decisions.
9. `doc/FUTURE.md` -- deferred ideas (explicitly not v0).
10. `doc/PROMPT_BOOTSTRAP.md` -- copy/paste seed for a fresh AI session.

## What each file is for

- `doc/STATE.md`: operational tracker; update every working session.
- `doc/ARCHITECTURE.md`: conceptual contract for v0.
- `doc/NORTH_STAR.md`: broader BEAR motivation and long-term success litmus.
- `doc/GOVERNANCE.md`: normative governance contract for classifying IR changes and signaling boundary expansion.
- `doc/IR_SPEC.md`: schema contract; if fields conflict elsewhere, this wins for IR shape.
- `doc/ROADMAP_V0.md`: implementation order for current v0 delivery.
- `doc/ROADMAP.md`: broader target direction and longer-horizon phases.
- `doc/PROMPT_BOOTSTRAP.md`: transport context into a new chat, not a full architecture doc.
  - Use this for non-repo sessions (for example ChatGPT without workspace file access).

## Repo layout

- `kernel/` -- trusted deterministic seed: BEAR IR parsing/validation/normalization + target abstractions
- `app/` -- CLI wrapper (commands like `bear validate`, `bear compile`, `bear check`)

## Session sequence (recommended)

1. Read `doc/STATE.md` first.
2. Read `doc/IR_SPEC.md` before touching parser/validator/generator code.
3. Read `doc/ROADMAP.md` before choosing implementation scope.
4. If this is a new AI chat, paste `doc/PROMPT_BOOTSTRAP.md` SHORT block.

## Session close protocol

Run this when major progress is made or before closing a session.

1. Update `doc/STATE.md`:
   - `Last Updated`
   - `Current Focus`
   - `Next Concrete Task`
   - short `Session Notes` bullets
2. Update canonical docs only if semantics changed:
   - `doc/IR_SPEC.md` for IR/schema/rules
   - `doc/ARCHITECTURE.md` for guarantees/non-guarantees/scope
   - `doc/ROADMAP.md` for phase/scope execution plan
3. Update `doc/PROJECT_LOG.md` only for meaningful architectural decisions.
4. Keep handoff docs aligned if needed:
   - `doc/START_HERE.md` for navigation/process changes
   - `doc/PROMPT_BOOTSTRAP.md` for external non-repo session bootstrap changes
5. End-of-session summary should always include:
   - what changed
   - open decisions/risks
   - next single task
