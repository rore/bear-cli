# Overview (Proof of Concept)

BEAR is a proof-of-concept reference implementation testing a practical idea:

> In agentic backend development, teams working on higher-sensitivity services may need deterministic enforcement so agents cannot expand boundary authority silently.

The goal is to move trust from intent to machine-checkable gates and explicit governance signals.

## What BEAR does

- **Agent updates IR first** when boundary authority must change.
- **BEAR compiles** deterministic wrappers, ports, and manifests from that IR.
- **Agent updates code inside those constraints** instead of widening structure implicitly.
- **BEAR checks** repo state for drift and covered boundary bypasses.
- **BEAR governs PR deltas** with explicit boundary-expansion classification.

New to vocabulary? See [TERMS.md](TERMS.md).

## What BEAR is and is not

BEAR is:
- a deterministic governance CLI for backend boundaries
- part of the agent working loop for immediate corrective feedback
- a CI-friendly signal producer (`exit` + stable footer contract) for PR and automation review

BEAR is not:
- a business-rule correctness engine
- a runtime sandbox/IAM framework
- an agent orchestrator

## Who edits IR?

In the intended workflow, developers do not hand-author IR routinely.
The agent updates IR as needed, runs BEAR while it works, and developers review the resulting governance signals.

## Read next

- [QUICKSTART.md](QUICKSTART.md)
- [DEMO.md](DEMO.md) for the live showcase repo and PR examples
- [PR_REVIEW.md](PR_REVIEW.md)
- [CI_INTEGRATION.md](CI_INTEGRATION.md) for the packaged downstream CI pattern and GitHub Actions usage
- [FOUNDATIONS.md](FOUNDATIONS.md) for full mechanics

