# Demo

The companion demo repo shows BEAR in actual pull requests — not only as local
commands. For how the workflow fits together, see [HOW_IT_WORKS.md](HOW_IT_WORKS.md).

BEAR ships with a companion demo repo:

- Demo repo: [bear-account-demo](https://github.com/rore/bear-account-demo)
- CLI repo: [bear-cli](https://github.com/rore/bear-cli)

The demo emphasizes the PR/CI review half of the model; the local inner-loop path is covered in QUICKSTART.

## What The Demo Shows

The live demo uses three showcase pull requests:

1. Greenfield baseline review
   - branch shape: new governed baseline compared to a spec-only main branch
   - expected BEAR CI decision: `REVIEW REQUIRED`
   - why: the PR introduces the first governed BEAR surface, so boundary expansion is intentional and visible

2. Ordinary feature extension
   - branch shape: normal product evolution on top of an existing governed baseline
   - expected BEAR CI decision: `PASS`
   - why: the feature extends behavior without widening governed architectural authority in a meaningful way

3. Intentional expansion on existing code
   - branch shape: new capability that adds a new governed surface on top of the baseline
   - expected BEAR CI decision: `REVIEW REQUIRED`
   - why: this is not a broken repo, but it does widen authority and should be reviewed as an explicit governance event

Taken together, the three PRs show the intended BEAR review split:

- clean evolution -> `PASS`
- intentional governance expansion -> `REVIEW REQUIRED`
- real drift/bypass/internal problems -> `FAIL`

## What GitHub Should Look Like

The demo repo runs the packaged wrapper in `observe` mode and publishes:

- a normal GitHub Actions check
- a sticky PR comment with the BEAR decision
- a markdown step summary
- uploaded artifacts:
  - `build/bear/ci/bear-ci-report.json`
  - `build/bear/ci/bear-ci-summary.md`

That means a PR can stay mergeable while still clearly showing a governance review requirement.

Example sticky comment shape:

```markdown
## BEAR CI

- Decision: `REVIEW REQUIRED`
- Mode: `observe`
- Base SHA: `<sha>`

<details><summary>BEAR summary</summary>

# BEAR CI Governance

- Mode: observe
- Decision: review-required

## PR Check

- Exit: 5
- Code: BOUNDARY_EXPANSION

</details>
```

Important:

- `PASS` means BEAR saw no blocking problem and no governance-review event.
- `REVIEW REQUIRED` means the PR is not structurally broken, but BEAR detected intentional boundary expansion that should be reviewed.
- `FAIL` means BEAR found a real blocking problem such as drift, bypass, validation failure, or wrapper/runtime failure.

## How The Demo Uses CI

The demo uses the packaged downstream CI wrapper under `.bear/ci/`:

- `.bear/ci/bear-gates.sh`
- `.bear/ci/bear-gates.ps1`

The workflow pattern is:

1. checkout with full history
2. set up Java
3. run `bear compile --all --project .`
4. run `.bear/ci/bear-gates.sh --mode observe`
5. publish the markdown summary to the PR and upload CI artifacts

In CI, the pull request target branch defines the governance comparison base.

That matters in the demo:

- greenfield review compares against `main`
- feature/expansion scenarios compare against `baseline/greenfield-output`

## Start Points

If you want to reproduce the local command path first, start with [QUICKSTART.md](QUICKSTART.md).

If you want the packaged CI and PR-review model used by the demo, continue with:

- [CI_INTEGRATION.md](CI_INTEGRATION.md)
- [PR_REVIEW.md](PR_REVIEW.md)
