# Start Here (bear-cli)

This is the navigation map for active work.

## Read next (in order)

1. `docs/context/program-board.md` -- canonical milestone feature status and ordered feature queue.
2. `docs/context/state.md` -- short handoff for the current working session.
3. `docs/context/roadmap.md` -- milestone definitions, done criteria, and post-preview priorities.
4. `docs/context/architecture.md` -- v0 guarantees/non-guarantees and scope lock.
5. `docs/context/invariant-charter.md` -- normative invariant catalog (`ENFORCED`/`PARTIAL`/`PLANNED`).
6. `docs/context/governance.md` -- normative IR diff classification and review policy.
7. `docs/context/ir-spec.md` -- canonical v0 IR schema and normalization rules.
8. `spec/repo/block-index.md` -- canonical `bear.blocks.yaml` contract for `--all` enforcement.
9. `docs/context/user-guide.md` -- operator-facing command and failure-envelope guide.
10. `docs/context/archive/demo-preview-demo.md` -- preview demo operator guide and scenario branch map.
11. `docs/bear-package/` -- distributed workflow source texts (`AGENTS.md`, `BEAR_AGENT.md`, `WORKFLOW.md`, `BEAR_PRIMER.md`).
12. `docs/context/project-log.md` -- architectural rationale history.
13. `docs/context/north-star.md` -- long-horizon vision.
14. `docs/context/future.md` -- explicitly parked ideas.
15. `docs/context/prompt-bootstrap.md` -- non-repo chat bootstrap seed.
16. `docs/context/archive/archive-readme.md` -- archived-file index.

## Source Ownership

- `docs/context/program-board.md`: only source for active milestone status and queue ordering.
- `docs/context/roadmap.md`: only source for roadmap definitions and done criteria.
- `docs/context/state.md`: only source for short session handoff.
- `docs/context/archive/*`: historical references only, never active planning inputs.

Interpretation guardrail:
- "What are the milestone features?" -> `docs/context/roadmap.md`.
- "Where are we standing on Preview features?" -> `docs/context/program-board.md` -> `Preview Feature Status (Roadmap Contract)`.
- "What should I do in this session?" -> `docs/context/state.md`.

## Repo Layout

- `kernel/` -- deterministic seed: IR parse/validate/normalize and target abstractions
- `app/` -- CLI wrapper (`validate`, `compile`, `check`, `pr-check`)

## Session Sequence (recommended)

1. Read `docs/context/program-board.md` and `docs/context/state.md`.
2. Read `docs/context/ir-spec.md` before parser/validator/generator edits.
3. Read `docs/context/roadmap.md` before choosing scope.
4. If this is a fresh non-repo chat, paste `docs/context/prompt-bootstrap.md` SHORT block.

## Session Close Protocol

1. Update `docs/context/state.md`:
   - `Last Updated`
   - `Current Focus`
   - `Next Concrete Task`
   - short session notes
2. Update `docs/context/program-board.md` if feature status, risks, or queue ordering changed.
3. Update canonical docs only when semantics changed:
   - `docs/context/ir-spec.md`
   - `docs/context/architecture.md`
   - `docs/context/governance.md`
   - `docs/context/roadmap.md`
4. Update `docs/context/project-log.md` only for major architectural decisions.


