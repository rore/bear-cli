# Public Docs

## Guide (Start Here)

1. [HOW_IT_WORKS.md](HOW_IT_WORKS.md) — what BEAR validates, the workflow, vocabulary, and architecture.
2. [QUICKSTART.md](QUICKSTART.md) — first successful local run.
3. [DEMO.md](DEMO.md) — what the live demo repo and showcase PRs prove.
4. [PR_REVIEW.md](PR_REVIEW.md) — how to interpret `check`/`pr-check` outcomes in PR/CI.

If you are adopting BEAR in a real CI workflow, continue with [CI_INTEGRATION.md](CI_INTEGRATION.md) after QUICKSTART or DEMO.

## Guide (Optional)

- [ENFORCEMENT.md](ENFORCEMENT.md) — guarantees and non-goals (`ENFORCED` vs `PARTIAL`).
- [INSTALL.md](INSTALL.md) — install the BEAR bundle into another repo.
- [CI_INTEGRATION.md](CI_INTEGRATION.md) — downstream CI wrapper usage, allow-file matching, and report contract.
- [troubleshooting.md](troubleshooting.md) — fix failures by `CODE=...`.

## Reference (Automation / Stability Contracts)

- [CONTRACTS.md](CONTRACTS.md) — gateway to command contracts, output format, exit codes, and versioning.

## If You Are Modifying bear-cli

Start with [docs/context/CONTEXT_BOOTSTRAP.md](../context/CONTEXT_BOOTSTRAP.md) (repo-maintainer routing and guardrails).
