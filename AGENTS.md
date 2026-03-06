# BEAR (bear-cli) -- Agent Notes

If you are an AI coding assistant (or a human picking up the repo), start here:

1. Read `docs/context/CONTEXT_BOOTSTRAP.md`.
2. Follow its routing map to load only task-relevant context docs.
3. If this is a fresh non-repo session (for example ChatGPT), use `docs/context/prompt-bootstrap.md` (paste the SHORT section first).
4. If you need historical rationale, read `docs/context/project-log.md` and `docs/context/archive/archive-readme.md`.

Guardrails (v1 preview):
- Keep `kernel/` deterministic and small (trusted seed). No LLM/agent logic in core.
- Stay within v1-preview scope defined in `docs/context/architecture.md` + `docs/context/roadmap.md`.
- Treat `docs/context/future.md` as explicitly out-of-scope unless asked.
- Prefer the two-file approach (generated skeleton + separate impl), use `bear fix` for generated-artifact repair, and deterministic enforcement via `bear check`.

Session hygiene:
- Update `docs/context/state.md` whenever work progresses (`Last Updated`, `Current Focus`, `Next Concrete Task`, short `Session Notes`).
- Keep `docs/context/state.md` within `ContextDocsConsistencyTest` budgets; if `Session Notes` approaches/exceeds cap, move oldest notes to `docs/context/archive/archive-state-history.md` and keep only recent operational notes in `state.md`.
- Keep `docs/context/state.md` focused on BEAR development handoff only; do not use it to store local tool, sandbox, editor, or terminal-diagnostics history unless that behavior changes BEAR development workflow.
- Update `docs/context/program-board.md` when milestone status/queue/risk entries change.
- Update `docs/context/project-log.md` only for major architectural shifts/decisions.
- Before ending a session, follow `docs/context/start-here.md` -> `Session Close Protocol`.
- Exception: routine demo cleanup/reset operations do not require `docs/context/state.md` updates unless cleanup policy/process changed.

Safety guardrails:
- Read `docs/context/safety-rules.md` before running any delete/cleanup command.
- Never run recursive deletes outside the repository root.
- For temp cleanup, prefer `scripts/safe-clean-temp.ps1` over ad-hoc `Remove-Item`.
- For demo branch cleanup, prefer `scripts/clean-demo-branch.ps1` over ad-hoc cleanup + git commands.
  - Default mode resets the branch to committed HEAD while preserving committed BEAR-authored paths already on that branch.
  - Add `-IncludeGreenfieldReset` only for spec-only greenfield branches where `bear.blocks.yaml`, `spec/`, and `src/main/java/blocks/` should be cleared before restore.

## Repo Skills

The following repo-local skills can be used in this project:
- workflow-orchestration: plan-first, verification-heavy workflow adapted to BEAR context docs and guardrails. Trigger for non-trivial tasks (3+ steps), architectural/refactor work, bug investigations, or explicit user request. (file: `c:/Dev/rore/bear-cli/skills/workflow-orchestration/SKILL.md`)

Default policy:
- For non-trivial tasks (3+ steps), load and follow `workflow-orchestration` before implementation.
