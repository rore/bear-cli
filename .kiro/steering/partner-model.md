---
inclusion: auto
description: Behavioral memory for AI assistants working on BEAR. Tracks collaboration patterns, user preferences, and calibration notes across sessions
---

# Partner Model

Behavioral memory for AI assistants working on BEAR. Tracks collaboration patterns,
user preferences, and calibration notes across sessions.

**Canonical file**: `.agent/Knowledge/partner_model.md`

Read that file at session start. Update it at session end — take ownership, don't just suggest.

## Quick Reference

- **Communication**: Direct, no fluff. Lowercase informal in chat, formal in docs.
- **Quality bar**: Determinism is non-negotiable. "Canonical" is the target.
- **Task execution**: Trusts well-structured plans (LGTM = proceed). Brief status updates.
- **Documentation**: Part of "done". Session close protocol is mandatory.

## Key Phrase Mappings

- "LGTM" / "approved" → proceed with implementation
- "wrap up" / "end session" → execute session close protocol
- "full verify" → run `--no-daemon` CI-parity tests
- "update state" → update `docs/context/state.md`
- "update memory" → update `.agent/Knowledge/partner_model.md` + `docs/context/state.md`

## BEAR Frozen Contracts

Never change without explicit approval:
- Exit codes (see `docs/public/exit-codes.md`)
- Failure envelope (CODE/PATH/REMEDIATION)
- IR v1 schema
- `--all` aggregation severity rank

## Complementary Files

| File | Purpose |
|------|---------|
| `docs/context/state.md` | Project handoff — what was done, what's next |
| `.agent/Knowledge/partner_model.md` | Behavioral memory — how to work together |
