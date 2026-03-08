---
id: future-target-adaptable-cli-dotnet
title: Target-adaptable CLI and initial .NET target
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## High-Level Outcome

- CLI core stays target-agnostic and deterministic.
- Add `DotnetTarget` behind the existing target seam.
- Prove that BEAR's governance model can extend beyond JVM without collapsing into weaker heuristic guarantees.

## Normative Constraints

- Preserve deterministic CLI behavior and deterministic failure envelope.
- Preserve IR as the boundary source of truth.
- Preserve the governance model: `pr-check` signals boundary expansion deterministically; `check` enforces drift plus covered gates.
- No generated-artifact editing outside BEAR-owned roots.
- Keep the target selection surface unchanged until an actual second target implementation requires it.

## Likely First Slice

- C# only
- SDK-style `.csproj`
- project/package governance through `.csproj` and lock file
- deterministic generation and drift
- deterministic project verification through locked `dotnet` command
- covered direct power-surface scanning for a narrow first set

## Non-Goals

- no broad `.NET` ecosystem support in the first slice
- no mixed-language or multi-target framework support
- no runtime policy or sandbox claims
- no broad MSBuild extensibility story in the first slice

## Definition of Done for the Initiative

1. JVM behavior remains unchanged and fully green.
2. `.NET` can compile/generate deterministically and participate in drift, check, and pr-check flows.
3. Project/package governance and project verification are deterministic and explicit.
4. Core CLI orchestration stays free of scattered JVM or `.NET` conditionals.

## Notes

Related containment/profile spec: `roadmap/ideas/future-dotnet-containment-profile.md`
Positioning note: `.NET` is likely a stronger second-target candidate than broad Node support if the product goal is honest multi-language governance rather than sheer ecosystem breadth.
