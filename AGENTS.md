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
- Update `docs/context/program-board.md` when milestone status/queue/risk entries change.
- Update `docs/context/project-log.md` only for major architectural shifts/decisions.
- Before ending a session, follow `docs/context/start-here.md` -> `Session Close Protocol`.
- Exception: routine demo cleanup/reset operations do not require `docs/context/state.md` updates unless cleanup policy/process changed.

Safety guardrails:
- Read `docs/context/safety-rules.md` before running any delete/cleanup command.
- Never run recursive deletes outside the repository root.
- For temp cleanup, prefer `scripts/safe-clean-temp.ps1` over ad-hoc `Remove-Item`.
- For demo branch cleanup, prefer `scripts/clean-demo-branch.ps1` over ad-hoc cleanup + git commands.
