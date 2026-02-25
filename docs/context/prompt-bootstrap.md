# BEAR Project Bootstrap (For Non-Codex Sessions)

Use this when starting a chat session without repo file access (for example ChatGPT).
Paste the SHORT block first. Paste LONG only if needed.

---

# SHORT BOOTSTRAP (Paste First)

We are building BEAR (Block Enforcement & Representation), a deterministic boundary-governance layer for backend blocks in agentic development.

Core purpose:
- agent-generated code is non-deterministic
- boundary expansion can happen silently
- production systems need deterministic independent enforcement gates

What BEAR does:
- takes BEAR IR (`version: v1`)
- deterministically validates and normalizes IR
- deterministically compiles BEAR-owned artifacts
- enforces via deterministic gates (`check`, `pr-check`)

Current scope (preview lock):
- JVM/Java target path is primary
- IR contract and semantics are canonical in `docs/context/ir-spec.md`
- governance classification contract is canonical in `docs/context/governance.md`

Current guarantees (preview):
- deterministic structural boundary contracts
- deterministic generated-artifact drift checks
- deterministic governance signaling and failure envelopes

Current non-goals (preview):
- business-policy inference beyond declared contracts
- generic runtime policy engine behavior
- full formal behavioral verification

Session placeholders:
- Current phase: [UPDATE]
- Session goal: [STATE ONE TASK]
- Done criteria: [STATE 1-2 CHECKS]

Session constraints:
- stay within current preview scope
- keep contracts deterministic
- keep governance semantics aligned with `docs/context/governance.md`

Reference split:
- `docs/context/architecture.md`: scope guarantees and non-guarantees
- `docs/context/roadmap.md`: milestone definitions and done criteria
- `docs/context/program-board.md`: live status and queue (repo sessions)
- `docs/context/ir-spec.md`: canonical IR contract

---

# LONG BOOTSTRAP (If More Context Is Needed)

BEAR is not:
- a full verifier
- a behavior DSL
- infrastructure simulation
- a replacement for developers

BEAR IR canonical model (preview):
- root uses `version: v1`
- strict `block` contract/effects/idempotency/invariants semantics
- deterministic normalization and strict validation
- wrapper-owned enforceable semantics only

Agent-default workflow:
1. understand task intent
2. discover current IR/index state
3. apply IR-first changes when boundaries shift
4. run deterministic gates
5. report explicit gate evidence and governance outcome

Canonical done-gate expectation:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
