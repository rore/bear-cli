# Start Here (bear-cli)

This is the navigation map for active work.

## Read next (in order)

1. `doc/PROGRAM_BOARD.md` -- canonical milestone feature status and ordered feature queue.
2. `doc/STATE.md` -- short handoff for the current working session.
3. `doc/ROADMAP.md` -- milestone definitions, done criteria, and post-preview priorities.
4. `doc/ARCHITECTURE.md` -- v0 guarantees/non-guarantees and scope lock.
5. `doc/INVARIANT_CHARTER.md` -- normative invariant catalog (`ENFORCED`/`PARTIAL`/`PLANNED`).
6. `doc/GOVERNANCE.md` -- normative IR diff classification and review policy.
7. `doc/IR_SPEC.md` -- canonical v0 IR schema and normalization rules.
8. `spec/repo/block-index.md` -- canonical `bear.blocks.yaml` contract for `--all` enforcement.
9. `doc/USER_GUIDE.md` -- operator-facing command and failure-envelope guide.
10. `doc/demo/PREVIEW_DEMO.md` -- preview demo operator guide and scenario branch map.
11. `doc/bear-package/` -- distributed workflow source texts (`AGENTS.md`, `BEAR_AGENT.md`, `WORKFLOW.md`, `BEAR_PRIMER.md`).
12. `doc/PROJECT_LOG.md` -- architectural rationale history.
13. `doc/NORTH_STAR.md` -- long-horizon vision.
14. `doc/FUTURE.md` -- explicitly parked ideas.
15. `doc/PROMPT_BOOTSTRAP.md` -- non-repo chat bootstrap seed.
16. `doc/archive/README.md` -- archived-file index.

## Source Ownership

- `doc/PROGRAM_BOARD.md`: only source for active milestone status and queue ordering.
- `doc/ROADMAP.md`: only source for roadmap definitions and done criteria.
- `doc/STATE.md`: only source for short session handoff.
- `doc/archive/*`: historical references only, never active planning inputs.

Interpretation guardrail:
- "What are the milestone features?" -> `doc/ROADMAP.md`.
- "Where are we standing on Preview features?" -> `doc/PROGRAM_BOARD.md` -> `Preview Feature Status (Roadmap Contract)`.
- "What should I do in this session?" -> `doc/STATE.md`.

## Repo Layout

- `kernel/` -- deterministic seed: IR parse/validate/normalize and target abstractions
- `app/` -- CLI wrapper (`validate`, `compile`, `check`, `pr-check`)

## Session Sequence (recommended)

1. Read `doc/PROGRAM_BOARD.md` and `doc/STATE.md`.
2. Read `doc/IR_SPEC.md` before parser/validator/generator edits.
3. Read `doc/ROADMAP.md` before choosing scope.
4. If this is a fresh non-repo chat, paste `doc/PROMPT_BOOTSTRAP.md` SHORT block.

## Session Close Protocol

1. Update `doc/STATE.md`:
   - `Last Updated`
   - `Current Focus`
   - `Next Concrete Task`
   - short session notes
2. Update `doc/PROGRAM_BOARD.md` if feature status, risks, or queue ordering changed.
3. Update canonical docs only when semantics changed:
   - `doc/IR_SPEC.md`
   - `doc/ARCHITECTURE.md`
   - `doc/GOVERNANCE.md`
   - `doc/ROADMAP.md`
4. Update `doc/PROJECT_LOG.md` only for major architectural decisions.
