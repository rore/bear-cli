# Foundations

This repository is a proof-of-concept reference implementation.
It validates deterministic boundary governance for agentic backend development.

For onboarding, use [OVERVIEW.md](OVERVIEW.md), [QUICKSTART.md](QUICKSTART.md), and [PR_REVIEW.md](PR_REVIEW.md).

## Why BEAR exists

Agent-generated code can move fast and hide architectural drift.
BEAR exists to make boundary authority changes explicit, enforceable, visible to the agent while it works, and reviewable in PRs and CI.

## How It Works

- **Declare boundary in IR.** IR defines block operations and allowed effects. `port.kind` can be `external` (`ops`) or `block` (`targetBlock` + `targetOps`). `block.kind` remains `logic` in v1.
- **Generate governed surface.** `bear compile` (or `bear fix`) emits deterministic wrappers/ports/manifests. Generated manifests include governed ownership roots (`governedSourceRoots`) used by enforcement.
- **Run deterministic gates.** `bear check` gives immediate inner-loop feedback on drift, covered bypass/reach rules, containment lanes, and project tests. `bear pr-check` compares against base and classifies boundary-expanding deltas for PR/CI governance.

## Intended Workflow Loop

Agent loop:
1. Update IR first when domain intent changes boundary authority.
2. Run `bear validate`.
3. Run `bear compile` or `bear fix` to materialize the governed structural constraints.
4. Update implementation inside those constraints.
5. Run `bear check --collect=all --agent` until `status=ok`.
6. Run `bear pr-check --base <ref> --collect=all --agent` for governance classification.

Developer role:
- review explicit boundary signals in PR/CI
- accept or reject boundary expansion intentionally

Who edits IR:
- in normal flow, the agent updates IR
- developers are not expected to hand-author IR routinely

## Vocabulary Highlights

- `drift`: generated artifacts no longer match IR-derived output.
- `boundary bypass`: structural reach-around of governed surfaces (fails with `CODE=BOUNDARY_BYPASS`).
- `boundary expansion`: widened declared authority in IR diff (fails `pr-check` with exit `5`).
- `unblock`: clears advisory blocked marker after lock/bootstrap IO failures.

See [TERMS.md](TERMS.md) for concise definitions.

## Architecture

BEAR CLI has two modules:
- `kernel/`: deterministic IR and target core
- `app/`: CLI orchestration and contract rendering

## Preview Scope

Preview focuses on deterministic contracts, governance signaling, and bounded enforcement coverage.
It is intentionally not full behavioral verification or runtime policy enforcement.

## Related

- [OVERVIEW.md](OVERVIEW.md)
- [TERMS.md](TERMS.md)
- [ENFORCEMENT.md](ENFORCEMENT.md)
- [PR_REVIEW.md](PR_REVIEW.md)
- [CONTRACTS.md](CONTRACTS.md)

