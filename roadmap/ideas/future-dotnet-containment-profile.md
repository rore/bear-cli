---
id: future-dotnet-containment-profile
title: Honest .NET containment profile
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Goal

Define the smallest .NET and C# target profile BEAR could support without overstating determinism, containment, or project-verification guarantees.

This document is intentionally narrower than a full multi-target implementation:
- no CLI behavior changes
- no `.bear/target.id`
- no target detection/pinning changes
- no IR schema changes
- no attempt to support the full .NET ecosystem

## Recommendation Summary

Recommendation:
- treat `.NET` as the strongest non-JVM target candidate for BEAR
- if BEAR pursues a second real target, prefer a narrow `.NET` profile before broad Node support
- the honest first slice is:
  - C# only
  - SDK-style `.csproj` only
  - `PackageReference` only
  - single target framework only
  - one BEAR block per project in the first slice
  - deterministic NuGet lock file enabled
  - deterministic project verification via `dotnet test` or a narrower locked build/test profile

Why:
- `.NET` exposes explicit project/package/reference structure that maps naturally to BEAR governance
- the ecosystem is statically legible enough to support stronger containment claims than Node
- it offers a real "beyond Java" proof without collapsing into runtime or resolver heuristics

## Supported .NET Profile

Profile name:
- `dotnet-csharp-sdk-single-project-v1`

Required repo/project shape:
- SDK-style `.csproj`
- `PackageReference`-based dependency model only
- one target framework only (for example `net8.0` or `net9.0`)
- lock file enabled and checked in (`packages.lock.json`)
- optional `.sln`, but BEAR governs at the project level in the first slice
- one BEAR block per project for the first slice

Required language/runtime profile:
- C# only
- no F#
- no VB
- no legacy `packages.config`
- no multi-targeting (`TargetFrameworks`)
- no source generators or build hooks that BEAR would need to interpret for containment

Likely supported project types in the first slice:
- class library
- worker/service-style backend project
- narrow ASP.NET backend profile only if it does not weaken the containment story

Unsupported project features in the first slice:
- multi-target frameworks
- mixed-language solutions
- complex solution-level reference graphs where one BEAR block spans many projects
- broad MSBuild customization intended to rewrite reference resolution
- shared project (`.shproj`) setups

## Governed Roots

User-authored governed roots:
- `src/blocks/<blockKey>/**`
- optional `src/blocks/_shared/**`

Generated BEAR-owned roots:
- `build/generated/bear/**`

Root treatment:
- one block's governed implementation remains inside its own block root
- `_shared` is the only shared user-authored governed root in the first slice
- generated code remains BEAR-owned and drift-checked
- tests may remain outside governed roots in the first slice, unless later target work chooses to govern test adapters explicitly

## Containment Model

For `.NET`, containment can likely mean more than in Node, but still less than a full runtime policy model.

It does not mean:
- runtime sandboxing
- runtime prevention of network/filesystem/process access
- proof that no power flows through every transitive dependency path

It can likely mean:
- deterministic generated ownership
- deterministic project/package/reference governance
- deterministic governed-root containment
- deterministic direct external-power scanning for a covered first set
- deterministic project verification through the standard toolchain

### 1. Project/package containment

Definition:
- the project file is the first-slice dependency boundary
- `PackageReference` and `ProjectReference` are the main declared dependency surfaces

Deterministic first-slice contract:
- package additions/changes are review-visible governance changes
- project-reference additions/changes are review-visible governance changes
- first-slice BEAR can likely support stronger containment around references than Node because the build model is explicit

### 2. Governed-root containment

Definition:
- governed implementation code must stay inside its block root or `_shared`
- direct usage paths that escape those roots into nongoverned app code can be treated as boundary bypass

### 3. Runtime/external-power containment

Definition:
- BEAR may statically flag selected direct .NET power surfaces in governed roots, but it does not become a runtime sandbox

Likely first covered surfaces to evaluate:
- `System.Net.Http`
- sockets/network surfaces
- filesystem (`System.IO`)
- process execution (`System.Diagnostics.Process`)
- messaging or DB libraries only when directly identifiable in a deterministic first slice

### 4. Generated/owned artifact containment

Definition:
- same BEAR two-file ownership model as JVM
- generated code under BEAR-owned roots is drift-checked and repaired deterministically

## Dependency Governance Model

### Scope decision

Use project/package-level dependency governance first.

Likely first-slice surfaces:
- `PackageReference`
- `ProjectReference`
- lock-file deltas in `packages.lock.json`
- possibly central package management files such as `Directory.Packages.props` only if the supported profile chooses to allow them

Open question to lock before implementation:
- whether the first slice should support Central Package Management at all, or require dependencies to stay local to the governed project file for simplicity

### `impl.allowedDeps`

Open question:
- `.NET` may be able to support a more honest equivalent than Node, because package references are explicit in project files
- but this still needs a real design decision: whether BEAR treats package references as repo/project-level governance only, or can meaningfully express block-level dependency allowance in the `.NET` target

Current recommendation:
- start with project-level dependency governance only
- defer any true `.NET` analogue to JVM `impl.allowedDeps` until the first target slice proves the basic containment model

## Project Verification Model

### Candidate verification contract

Likely first-slice contract:
- `dotnet test --no-restore`

Alternative narrow contract if needed:
- `dotnet build --no-restore` first, with test support deferred

Recommendation:
- prefer `dotnet test` if the supported profile stays narrow enough, because it better matches BEAR's serious backend workflow than a build-only slice

### Failure mapping

Likely mapping:
- `exit 0`: verification passed
- `exit 4`: deterministic project verification failure (build/test reported user-code failures)
- `exit 64`: unsupported profile or usage/configuration shape
- `exit 74`: tooling/bootstrap/environment failure

## Capability Matrix (Initial Expectation)

| Area | Expected status | Honest first-slice meaning |
| --- | --- | --- |
| Deterministic generated ownership under `build/generated/bear/**` | `ENFORCED` | Same BEAR ownership model as JVM. |
| Two-file preservation for user impl files | `ENFORCED` | Generated/user-owned split remains stable. |
| Governed-root containment inside one project | `ENFORCED` | Block code stays in its governed roots. |
| Project/package governance via `.csproj` references | `ENFORCED` | Dependency/reference expansion is explicit and review-visible. |
| Lock-file governance via `packages.lock.json` | `ENFORCED` | Resolved dependency graph changes are review-visible. |
| Covered direct .NET power-surface scanning | `PARTIAL` | Stronger than Node likely, but only for a covered first set. |
| True runtime containment | `NOT_SUPPORTED` | BEAR remains build/test governance, not runtime enforcement. |
| Full ecosystem support across all .NET project types | `NOT_SUPPORTED` | First slice must stay narrow. |
| Broad MSBuild customization support | `NOT_SUPPORTED` | Too much indirection for a first honest slice. |

## Main Open Questions

1. What is the cleanest deterministic inclusion mechanism for generated BEAR-owned code in SDK-style projects?
2. Should the first slice support only local `PackageReference`, or also central package management (`Directory.Packages.props`)?
3. Is one block per project the right first containment model, or is there a narrower/better mapping?
4. Is `dotnet test` stable enough as the first verification contract, or should BEAR start with build-only verification?
5. What is the smallest direct power-surface set that is useful and deterministic enough to enforce?

## Recommendation

### Should BEAR pursue `.NET`?

Recommendation:
- yes, as a serious future target candidate
- if the goal is to prove BEAR is broader than JVM without weakening the honesty of the product, `.NET` is likely a better second target than broad Node support

Why:
- it offers stronger static/project structure
- it better matches BEAR's backend/governance thesis
- it likely supports a stronger containment story than Node

### Smallest honest future slice

If `.NET` is pursued later, the first honest slice should likely include only:
- deterministic generation + drift
- governed roots in a narrow one-block-per-project profile
- project/package governance via `.csproj`
- lock-file governance via `packages.lock.json`
- deterministic project verification via locked `dotnet` command
- a small covered direct power-surface scanner set

### Explicit deferrals

Defer all of these:
- broad ASP.NET or mixed project-type support
- multi-target frameworks
- mixed-language solutions
- advanced MSBuild customization support
- runtime policy/sandbox claims
- any broad `.NET` ecosystem promise beyond the supported profile

## External Grounding

The profile above should be grounded by current official docs such as:
- `PackageReference` and NuGet project references
- `packages.lock.json` lock-file behavior
- `dotnet test`
- Central Package Management (`Directory.Packages.props`)
- MSBuild `ProjectReference` behavior

Those should be treated as primary sources when this parked idea is revisited for implementation planning.
