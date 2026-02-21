# Preview Demo Guide

This page is the operator guide for the preview demo reset.

## Goal

Show a normal developer experience:
- clone the demo repo
- ask the agent one sentence: `Implement the specs.`
- verify with one command: `bear check --all --project .`

The demo policy is strict:
- outside copied BEAR package files, demo content must not teach IR or BEAR structure.

## Demo Repo Baseline

Repository: `../bear-account-demo`

`main` branch is intentionally spec-only:
- product spec present (`doc/SPEC.md`)
- minimal build scaffolding present
- no pre-authored IR
- no pre-authored BEAR implementation output

## Scenario Branches

All scenario branches are derived from greenfield output.

1. `scenario/01-agent-greenfield-implementation`
- first complete agent-generated implementation from `main`
- done state: `bear check --all --project .` exits `0`

2. `scenario/02-feature-extension-scheduled-transfers`
- starts from scenario 01 output
- spec adds scheduled transfers with explicit trigger endpoint (`tick(now)` style)
- expected result: agent extends architecture and remains green

3. `scenario/03-boundary-expansion-flagged`
- starts from scenario 01 output
- spec introduces an intentional boundary-expanding change
- expected result: `bear pr-check --all --project . --base origin/main` exits `5`

4. `scenario/04-generated-drift-and-fix`
- starts from scenario 01 output
- introduces generated artifact drift and demonstrates deterministic repair
- expected result: check fails on drift, fix restores green

## Execution Notes

- Use isolated sessions for each scenario to preserve credibility of agent output.
- Keep scenario branches open as PRs to `main` so diffs form a browseable demo museum.
- Archive old scenario branches with tags before deletion when resetting tracks.
- Windows lock hygiene for demo runs:
  - set repo-local Gradle home before gate commands (`GRADLE_USER_HOME=<repo>/.gradle-user`)
  - if compile/check reports `WINDOWS_FILE_LOCK`, do one retry, then stop and report the blocker
  - do not rename blocks/IR files or alter ACLs as lock workarounds
