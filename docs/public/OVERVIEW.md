# Overview (Proof of Concept)

BEAR is a proof-of-concept reference implementation exploring a specific hypothesis:

> In an agentic development world, teams building higher-sensitivity services may need strict, deterministic enforcement so an agent cannot expand external power or bypass architecture constraints silently.

BEAR tries to make that practical by moving trust from "agent reasoning" to deterministic, machine-checkable gates.

## What BEAR does

New to BEAR vocabulary (effects/ports/ops)? See [TERMS.md](TERMS.md).

- **Agent updates**: the agent changes code and (when needed) a small YAML IR contract.
- **BEAR generates**: deterministic wrappers/ports from the IR (the governed integration surface).
- **BEAR enforces**: local/CI gates detect drift and covered boundary-bypass patterns.
- **BEAR signals governance**: `pr-check` classifies boundary-expanding changes vs ordinary refactors.

The goal is fast iteration inside a declared boundary, with explicit review signals when that boundary changes.

## What BEAR is (and isn't)

BEAR is:
- a deterministic CLI that produces CI-friendly signals (exit codes + stable `CODE/PATH/REMEDIATION` footer)
- a compiler-style generator for a narrow, enforceable semantic slice (wrapper-owned where possible)

BEAR is not:
- a full business correctness verifier
- a runtime sandbox / IAM system
- an agent orchestrator

## Who edits IR?

In the intended workflow, **developers do not hand-author IR in routine usage**.
The agent updates IR when it needs new boundary authority (new ports/ops, invariants, idempotency usage, etc.).
Humans primarily review the resulting governance signals.

If you want the details anyway, see:
- IR reference (agent package): [IR_REFERENCE.md](../bear-package/.bear/agent/ref/IR_REFERENCE.md)
- Canonical IR spec (bear-cli maintainer): [docs/context/ir-spec.md](../context/ir-spec.md)

## Where to go next

- First run: [QUICKSTART.md](QUICKSTART.md)
- What to look for in PRs/CI: [PR_REVIEW.md](PR_REVIEW.md)
- What BEAR enforces vs only alerts on: [ENFORCEMENT.md](ENFORCEMENT.md)
- Debug a failure by `CODE`: [troubleshooting.md](troubleshooting.md)
