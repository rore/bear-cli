# BEAR (bear-cli) -- Agent Notes

If you are an AI coding assistant (or a human picking up the repo), start here:

1. Read `docs/context/program-board.md` (source of truth for milestone feature status and ordered feature queue).
2. Read `docs/context/state.md` (short session handoff).
3. Read `docs/context/start-here.md`.
4. Read `docs/context/ir-spec.md` for canonical v1 IR structure and constraints.
5. If this is a fresh non-repo session (for example ChatGPT), use `docs/context/prompt-bootstrap.md` (paste the SHORT section first).
6. If you need historical rationale, read `docs/context/project-log.md`.

Guardrails (v1 preview):
- Keep `kernel/` deterministic and small (trusted seed). No LLM/agent logic in core.
- Stay within v1-preview scope defined in `docs/context/architecture.md` + `docs/context/roadmap.md`.
- Treat `docs/context/future.md` as explicitly out-of-scope unless the user asks.
- Prefer the two-file approach (generated skeleton + separate impl), use `bear fix` for generated-artifact repair, and deterministic enforcement via `bear check`.

Session hygiene:
- Update `docs/context/state.md` whenever work progresses (Last Updated, Current Focus, Next Concrete Task).
- Update `docs/context/program-board.md` when milestone feature status/queue/risk entries change.
- Update `docs/context/project-log.md` only for major architectural shifts/decisions.
- Before ending a session, follow `docs/context/start-here.md` -> "Session close protocol".
- Exception: routine demo cleanup/reset operations do not require `docs/context/state.md` updates unless cleanup policy/process changed.

Safety guardrails:
- Read `docs/context/safety-rules.md` before running any delete/cleanup command.
- Never run recursive deletes outside the repository root.
- For temp cleanup, prefer `scripts/safe-clean-temp.ps1` over ad-hoc `Remove-Item`.
- For demo branch cleanup, prefer `scripts/clean-demo-branch.ps1` over ad-hoc cleanup + git commands.
