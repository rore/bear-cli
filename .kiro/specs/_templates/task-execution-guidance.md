# Task Execution Guidance Template

Add these three fields to EVERY task in `tasks.md`:

## Task Template

```markdown
- [ ] N. Task Name
  - **Context:** Read `design.md` sections: [relevant sections]. Reference [existing code] for pattern.
  - **Execution:** Implement directly. [Dependencies]. [Parallelization notes]. [When to use context-gatherer].
  - **Verification:** [What to test]. [What to verify].
  - [ ] N.1 Subtask 1
    - Details...
  - [ ] N.2 Subtask 2
    - Details...
```

## Field Explanations

### Context
- Which design.md sections to read
- Which existing code to reference as a pattern
- Any Phase A/B/C dependencies to understand

### Execution
- "Implement directly" (always - never delegate to subagents)
- Task dependencies (e.g., "Depends on task 2 completing")
- Parallelization opportunities (e.g., "Can run in parallel with tasks 3-5")
- When to use `context-gatherer` (e.g., "Use `context-gatherer` if unfamiliar with JvmTarget pattern")

### Verification
- What tests to run after completion
- What to verify (e.g., "Verify no JVM test regressions")
- Any specific validation criteria

## Session Hygiene During Task Execution

When working on spec tasks, follow BEAR session hygiene rules from AGENTS.md:

### Update docs/context/state.md
- Update after completing each major task (not every subtask)
- Update fields: `Last Updated`, `Current Focus`, `Next Concrete Task`
- Add brief note to `Session Notes` about what was completed
- Keep `Session Notes` concise - if approaching budget cap, move old notes to `docs/context/archive/archive-state-history.md`

### When to Update
- ✅ After completing a full task (e.g., Task 1: NodeTargetDetector)
- ✅ After hitting a major milestone (e.g., all tests passing)
- ✅ Before ending a work session
- ❌ Not after every subtask (too granular)
- ❌ Not for routine test runs or minor fixes

### Example state.md Update
```markdown
Last Updated: 2024-01-15
Current Focus: Phase B Node Target implementation
Next Concrete Task: Task 4 - Governed roots computation

Session Notes:
- Completed Task 1 (NodeTargetDetector) - all tests passing
- Completed Task 2 (NodeTarget skeleton) - registered in TargetRegistry
- Completed Task 3 (TypeScript artifact generation) - all 4 artifact types working
- JVM regression tests: 0 failures
```

## Why Inside Each Task?

When Kiro works on a task, it reads ONLY that task, not the entire file. Therefore:
- ❌ Instructions at the beginning/end of file are NOT seen
- ✅ Instructions inside each task ARE seen
- ✅ Each task is self-contained with all needed guidance

## Example

```markdown
- [ ] 1. NodeTargetDetector
  - **Context:** Read `design.md` sections: NodeTargetDetector, Detection algorithm. Reference `JvmTargetDetector` for pattern.
  - **Execution:** Implement directly. Can run in parallel with task 2. Use `context-gatherer` if unfamiliar with JvmTargetDetector pattern.
  - **Verification:** Run tests after completion. Verify no JVM test regressions.
  - [ ] 1.1 Create `NodeTargetDetector.java` implementing `TargetDetector`
    - File-presence detection: `package.json` (parse `type` + `packageManager`), `pnpm-lock.yaml`, `tsconfig.json`
    - Returns `SUPPORTED` / `UNSUPPORTED` (workspace) / `NONE`
```

## Common Patterns

### Independent Task
```markdown
- **Execution:** Implement directly. Independent, can run in parallel with tasks 1-4.
```

### Dependent Task
```markdown
- **Execution:** Implement directly. Depends on task 2 completing (needs NodeTarget skeleton).
```

### Task with Parallelization
```markdown
- **Execution:** Implement directly. Depends on task 2 completing. Subtasks 3.1-3.4 can run in parallel.
```

### Task Needing Context Gathering
```markdown
- **Execution:** Implement directly. Use `context-gatherer` if unfamiliar with JvmTarget pattern.
```

### Integration Task
```markdown
- **Execution:** Implement directly. Depends on tasks 1-10 completing (needs all components).
```
