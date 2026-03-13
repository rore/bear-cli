# Spec Authoring Guide

This guide explains how to structure specs for optimal execution by Kiro.

## Spec Structure

Every spec should have three files:
1. `requirements.md` - What needs to be built (acceptance criteria)
2. `design.md` - How it will be built (architecture, components, data models)
3. `tasks.md` - Step-by-step implementation tasks

## Tasks.md Structure

### Critical Rule: Instructions Inside Each Task

When Kiro works on a task, it reads ONLY that task, not the entire file. Therefore:
- ❌ Instructions at the beginning/end of file are NOT seen
- ✅ Instructions inside each task ARE seen
- ✅ Each task must be self-contained with all needed guidance

### Required Fields for Every Task

Every task must have these three fields:

1. **Context:** Which design.md sections to read, which existing code to reference
2. **Execution:** Dependencies, parallelization, when to use context-gatherer
3. **Verification:** What to test and verify after completion

### Example Task Structure

```markdown
- [ ] 1. Component Name
  - **Context:** Read `design.md` sections: ComponentName, Algorithm. Reference `ExistingComponent` for pattern.
  - **Execution:** Implement directly. Can run in parallel with task 2. Use `context-gatherer` if unfamiliar with ExistingComponent pattern.
  - **Verification:** Run tests after completion. Verify no regressions.
  - [ ] 1.1 Create `ComponentName.java` implementing `Interface`
    - Specific behavior 1
    - Specific behavior 2
  - [ ] 1.2 Write `ComponentNameTest.java`
    - Test case 1
    - Test case 2
```

## Design.md Structure

### Implementation Sequence Section

Keep the Implementation Sequence section simple - detailed execution guidance goes in tasks.md:

```markdown
## Implementation Sequence

1. Component A + tests
2. Component B + tests  
3. Integration + end-to-end tests
```

No need for execution strategy here - that's embedded in each task in tasks.md.

## Kiro's Native Subagents

Available via `invokeSubAgent` tool:

### context-gatherer
- **Purpose:** Explores unfamiliar codebases, identifies relevant files
- **When to use:** Starting work on unfamiliar code area, investigating bugs across multiple files
- **When NOT to use:** When you already know which files to modify
- **Usage:** Use ONCE per query at the beginning, then work with gathered context

### general-task-execution
- **Purpose:** Delegates well-defined subtasks with isolated context
- **When to use:** Independent work streams that benefit from isolation
- **When NOT to use:** For spec task execution (implement directly instead)

### custom-agent-creator
- **Purpose:** Creates new custom agents for recurring patterns
- **When to use:** User explicitly asks to create a custom agent
- **When NOT to use:** For general task execution

## Common Mistakes to Avoid

1. **Don't delegate spec tasks to subagents** - Kiro should implement directly
2. **Don't use non-existent subagents** - Only `context-gatherer`, `general-task-execution`, `custom-agent-creator` exist
3. **Don't overuse context-gatherer** - Use once at start, not repeatedly
4. **Don't forget task dependencies** - Clearly document what depends on what
5. **Don't skip verification strategy** - Always include test and regression verification steps

## Task Granularity

Good task granularity:
- Each task is 1-3 hours of work
- Each task has clear acceptance criteria
- Each task includes its own tests
- Tasks can be verified independently

Bad task granularity:
- "Implement entire feature" (too large)
- "Add semicolon to line 42" (too small)
- "Fix all bugs" (not specific)

## Example Task Structure

```markdown
- [ ] 1. Component Name
  - **Context:** Read `design.md` sections: ComponentName, Algorithm. Reference `ExistingComponent` for pattern.
  - **Execution:** Implement directly. Can run in parallel with task 2. Use `context-gatherer` if unfamiliar with ExistingComponent pattern.
  - **Verification:** Run tests after completion. Verify no regressions.
  - [ ] 1.1 Create `ComponentName.java` implementing `Interface`
    - Specific behavior 1
    - Specific behavior 2
    - Error handling for case X
  - [ ] 1.2 Write `ComponentNameTest.java`
    - Test case 1: expected behavior
    - Test case 2: edge case
    - Test case 3: error condition
  - [ ] 1.3 Write `ComponentProperties.java` (jqwik)
    - Property 1: invariant description
    - Property 2: relationship description
```

## Verification Checklist

Before marking a spec complete:
- [ ] All three files (requirements, design, tasks) exist
- [ ] Each task has Context/Execution/Verification fields
- [ ] Task dependencies documented in Execution field
- [ ] Parallel execution opportunities identified in Execution field
- [ ] No references to non-existent subagents
- [ ] Clear guidance on when to use context-gatherer
- [ ] Session hygiene guidance included (state.md updates)

## Session Hygiene During Spec Execution

Remind implementers to follow BEAR session hygiene (from AGENTS.md):

### Update docs/context/state.md
- Update after completing each major task (not every subtask)
- Update: `Last Updated`, `Current Focus`, `Next Concrete Task`, brief `Session Notes`
- Keep `Session Notes` concise - move old notes to archive if approaching budget

### When to Update
- ✅ After completing a full task
- ✅ After hitting a major milestone
- ✅ Before ending a work session
- ❌ Not after every subtask
- ❌ Not for routine test runs

### Add to Task Verification Field
Include reminder in verification field of major tasks:
```markdown
- **Verification:** Run tests. Verify no regressions. Update docs/context/state.md.
```
