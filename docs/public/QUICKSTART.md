# Quickstart

This walkthrough is the shortest path to verify BEAR on the demo repo.

Prerequisites:

- `bear` is installed and available on `PATH`.
- Demo repo is present at `../bear-account-demo`.

## Try BEAR on the demo repo

1. Enter the demo repository.

```powershell
Set-Location ..\bear-account-demo
```

Expected outcome: shell is at demo repo root.

2. Implement project specs with your agent.

```text
Implement the specs.
```

Expected outcome: agent creates or updates governed implementation and IR artifacts.

3. Run the deterministic repo gate.

```powershell
bear check --all --project .
```

Expected outcome: all selected blocks are `PASS`, summary `EXIT_CODE: 0`.

Success signal:

```powershell
bear check --all --project .
```

## Related

- `INDEX.md`
- `MODEL.md`
- `commands-check.md`
- `troubleshooting.md`
- `exit-codes.md`
