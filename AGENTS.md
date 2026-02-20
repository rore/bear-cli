# BEAR (bear-cli) -- Agent Notes

If you are an AI coding assistant (or a human picking up the repo), start here:

1. Read `doc/PROGRAM_BOARD.md` (source of truth for milestone feature status and ordered feature queue).
2. Read `doc/STATE.md` (short session handoff).
3. Read `doc/START_HERE.md`.
4. Read `doc/IR_SPEC.md` for canonical v0 IR structure and constraints.
5. If this is a fresh non-repo session (for example ChatGPT), use `doc/PROMPT_BOOTSTRAP.md` (paste the SHORT section first).
6. If you need historical rationale, read `doc/PROJECT_LOG.md`.

Guardrails (v0):
- Keep `kernel/` deterministic and small (trusted seed). No LLM/agent logic in core.
- Stay within v0 scope defined in `doc/ARCHITECTURE.md` + `doc/ROADMAP.md`.
- Treat `doc/FUTURE.md` as explicitly out-of-scope unless the user asks.
- Prefer the two-file approach (generated skeleton + separate impl), use `bear fix` for generated-artifact repair, and deterministic enforcement via `bear check`.

Session hygiene:
- Update `doc/STATE.md` whenever work progresses (Last Updated, Current Focus, Next Concrete Task).
- Update `doc/PROGRAM_BOARD.md` when milestone feature status/queue/risk entries change.
- Update `doc/PROJECT_LOG.md` only for major architectural shifts/decisions.
- Before ending a session, follow `doc/START_HERE.md` -> "Session close protocol".

Safety guardrails:
- Read `doc/SAFETY_RULES.md` before running any delete/cleanup command.
- Never run recursive deletes outside the repository root.
- For temp cleanup, prefer `scripts/safe-clean-temp.ps1` over ad-hoc `Remove-Item`.
