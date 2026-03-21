# Quickstart

First successful local run using the companion demo repo.

Prerequisites:

- Demo repo present at `../bear-account-demo`
- Vendored CLI at `.bear/tools/bear-cli`
- `bear.blocks.yaml` in the demo repo root (required for `--all` commands)

Example sibling layout:

```text
<parent>/bear-cli
<parent>/bear-account-demo
```

## Run on the demo repo

1. Enter the demo repo.

```sh
cd ../bear-account-demo
```

2. Verify the vendored CLI.

```sh
./.bear/tools/bear-cli/bin/bear --help
# Windows: .\.bear\tools\bear-cli\bin\bear.bat --help
```

3. Let your agent update IR first if boundary authority changes, then
   implement the specs inside the generated constraints.

4. Compile deterministic generated artifacts.

```sh
./.bear/tools/bear-cli/bin/bear compile --all --project .
# Windows: .\.bear\tools\bear-cli\bin\bear.bat compile --all --project .
```

Expected outcome: all selected blocks compile and summary `EXIT_CODE: 0`.

5. Run enforcement.

```sh
./.bear/tools/bear-cli/bin/bear check --all --project .
```

Expected outcome: all selected blocks pass and summary `EXIT_CODE: 0`.

6. Run PR governance.

```sh
./.bear/tools/bear-cli/bin/bear pr-check --all --project . --base HEAD
```

Expected outcome: `pr-check: OK: NO_BOUNDARY_EXPANSION` and exit `0`.
For real PR/CI, set `--base` to the target branch or merge-base target.

7. Run the packaged CI wrapper.

```sh
./.bear/ci/bear-gates.sh --mode observe --base-sha HEAD
# Windows: .\.bear\ci\bear-gates.ps1 --mode observe --base-sha HEAD
```

Expected outcome: summary lines on stdout, `build/bear/ci/bear-ci-report.json`,
and `build/bear/ci/bear-ci-summary.md` for downstream CI audit and GitHub
step-summary routing.

## What This Does Not Show

The quickstart proves the local command path only.

The full review story lives in the companion demo repo, where BEAR is shown
in actual pull requests with:

- `PASS` for ordinary governed evolution
- `REVIEW REQUIRED` for intentional boundary expansion
- sticky PR comment plus uploaded CI artifacts in GitHub Actions

See [DEMO.md](DEMO.md) for that walkthrough.

For the packaged downstream CI pattern, allow-file approval flow, and copyable
GitHub Actions usage, continue with [CI_INTEGRATION.md](CI_INTEGRATION.md).

## If `bear.blocks.yaml` is missing

All `--all` commands require `bear.blocks.yaml`.

Minimal valid example:

```yaml
version: v0
blocks:
  - name: inventory-sync
    ir: bear-ir/inventory-sync.bear.yaml
    projectRoot: .
```

Fallback single-file path:

```sh
./.bear/tools/bear-cli/bin/bear compile bear-ir/<block>.bear.yaml --project .
./.bear/tools/bear-cli/bin/bear check bear-ir/<block>.bear.yaml --project .
./.bear/tools/bear-cli/bin/bear pr-check bear-ir/<block>.bear.yaml --project . --base HEAD
```

If something fails, go to [troubleshooting.md](troubleshooting.md).
