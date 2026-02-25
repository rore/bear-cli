# Context Bootstrap (bear-cli)

Purpose:
- Small always-load context contract for repo sessions.
- Route readers to the right canonical doc with minimal context cost.

## If You Remember Nothing Else

1. Use this file as the first read in repo sessions.
2. `docs/context/state.md` is current-window handoff only.
3. `docs/context/program-board.md` is live status + queue.
4. `docs/context/roadmap.md` is milestone definitions/done criteria.
5. `docs/context/ir-spec.md` is canonical IR contract.
6. `docs/context/governance.md` is canonical diff-class policy.
7. Completion evidence expects both local gates:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`

## Routing Map

Always load:
1. `docs/context/CONTEXT_BOOTSTRAP.md`

Load by intent:
1. Session execution now:
- `docs/context/state.md`
2. Milestone status, queue, open risks:
- `docs/context/program-board.md`
3. Milestone definitions and done criteria:
- `docs/context/roadmap.md`
4. IR schema, validation, normalization, semantic constraints:
- `docs/context/ir-spec.md`
5. Governance classification policy:
- `docs/context/governance.md`
6. Architecture guarantees and scope lock:
- `docs/context/architecture.md`
7. Operator command and failure handling:
- `docs/context/user-guide.md`
8. Safety rules before cleanup/delete operations:
- `docs/context/safety-rules.md`
9. Demo simulation protocol and grading rubric:
- `docs/context/demo-agent-simulation.md`
10. Non-repo chat bootstrap prompt:
- `docs/context/prompt-bootstrap.md`
11. Historical rationale:
- `docs/context/project-log.md`
- `docs/context/archive/archive-readme.md`

## Source Ownership

1. `docs/context/state.md`:
- short current-window handoff only
2. `docs/context/program-board.md`:
- active milestone status, ordered queue, open risks
3. `docs/context/roadmap.md`:
- milestone feature contracts and done criteria
4. `docs/context/ir-spec.md`:
- canonical v1 IR model and normalization
5. `docs/context/archive/*`:
- historical records only, never primary planning input

## Session Update Contract

When work progresses:
1. Update `docs/context/state.md`:
- `Last Updated`
- `Current Focus`
- `Next Concrete Task`
- short `Session Notes`
2. Update `docs/context/program-board.md` if queue/status/risk changed.
3. Update canonical docs only when semantics changed.
4. Put long narrative/history in archive docs, not `state.md`.

## Session Close Contract

Before ending a session:
1. Follow `docs/context/start-here.md` -> `Session Close Protocol`.
2. Ensure `state.md` remains concise.
3. If context was compacted, add a dated archive snapshot entry.

## No-Loss Context Rule

1. Context compaction must preserve topics via mapping in:
- `docs/context/context-coverage-map.md`
2. No section is removed unless mapped to:
- a canonical active doc, or
- an archive destination.
