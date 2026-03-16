# Public Docs

These docs are split into two lanes: a default guide path that starts with the agent working loop and then moves into PR/CI review, and a reference path for automation/parsers.

## Guide (Start Here: 4 pages)

1. [OVERVIEW.md](OVERVIEW.md) - what BEAR is trying to validate and why it exists.
2. [QUICKSTART.md](QUICKSTART.md) - first successful local run.
3. [DEMO.md](DEMO.md) - what the live demo repo and showcase PRs are proving.
4. [PR_REVIEW.md](PR_REVIEW.md) - how to interpret `check`/`pr-check` outcomes in PR/CI.

If you are adopting BEAR in a real CI workflow, continue with [CI_INTEGRATION.md](CI_INTEGRATION.md) after QUICKSTART or DEMO.

## Guide (Optional)

- [FOUNDATIONS.md](FOUNDATIONS.md) - how BEAR works end-to-end (workflow, ownership, architecture).
- [TERMS.md](TERMS.md) - minimum vocabulary needed to read BEAR output.
- [ENFORCEMENT.md](ENFORCEMENT.md) - guarantees and non-goals (`ENFORCED` vs `PARTIAL`).
- [INSTALL.md](INSTALL.md) - install the BEAR bundle into another repo.
- [CI_INTEGRATION.md](CI_INTEGRATION.md) - downstream CI wrapper usage, allow-file matching, and report contract.
- [troubleshooting.md](troubleshooting.md) - fix failures by `CODE=...`.
- [VISION.md](VISION.md) - directional ideas (non-committed).

## Reference (Automation / Stability Contracts)

- [CONTRACTS.md](CONTRACTS.md) - gateway to command contracts, output format, exit codes, and versioning.

## If You Are Modifying bear-cli

Start with [docs/context/CONTEXT_BOOTSTRAP.md](../context/CONTEXT_BOOTSTRAP.md) (repo-maintainer routing and guardrails).

