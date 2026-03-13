# Partner Model: BEAR (bear-cli)

This file tracks collaboration patterns, project preferences, and user calibration notes.
**Update this at the end of every session** — it's your working memory for how to work
effectively with this user on this project.

## User Profile

- **Role**: Technical Lead / Architect
- **Technical Depth**: High. Understands system internals, agentic architectures,
  governance systems, multi-target CLI design.
- **Values**: Deterministic behavior, explicit contracts, root cause analysis,
  comprehensive documentation, boundary governance.

---

## Project Context

- **Project**: BEAR — Block Enforceable Architectural Representation
- **Type**: Deterministic governance CLI for agentic backend development
- **Language**: Java (Gradle multi-module: `kernel/` + `app/`)
- **Key Constraints**:
  - Determinism is a product contract, not just a preference
  - Exit codes, failure envelopes, and IR schemas are frozen
  - `kernel/` is the trusted seed — no LLM/agent logic allowed
  - New targets extend through the Target seam without changing core

---

## Collaboration Protocols

### Communication Style
- Proactive: fix the underlying issue, don't just answer
- Explain "why" for architectural decisions
- No fluff — get straight to technical details
- Direct feedback with specific corrections
- Lowercase informal in chat, formal documentation expected
- Genuinely wants AI input on design decisions

### Documentation Expectations
- Comprehensive context docs targeted by audience
- Documentation is part of "done"
- Living documents updated proactively as patterns emerge
- Session-end protocol is mandatory

### Quality Standards
- Determinism is non-negotiable
- "Canonical" as target — "good enough" is insufficient for contracts
- Test integrity: update tests when behavior changes
- Documentation is part of task completion

### Task Execution
- Trusts well-structured plans (LGTM approval)
- Expects efficient execution with brief status updates
- Test failures are normal — expects methodical resolution
- Batch related edits before verification (fast-by-default)

## Key Patterns

### What Works Well
- Iterative refinement with specific feedback
- Catching contradictions early through careful spec review
- Explicit documentation of limitations and assumptions
- Focused sessions that complete discrete units of work
- "Tighten the screws" approach: fix critical issues first
- Strategic thinking about multi-target expansion
- Spec-driven development with clear acceptance criteria
- Proactive state updates without being asked

### What Doesn't Work
- Verbose explanations when code speaks for itself
- Implicit assumptions or undocumented transition rules
- Quick fixes without architectural justification
- Settling for "good enough" when contracts are involved
- Re-explaining problems already diagnosed in context docs
- Ignoring frozen contract boundaries
- Duplicating content across files instead of referencing

### When User Says...
- "LGTM" / "approved" → Plan accepted, proceed with implementation
- "wrap up" / "end session" → Execute session close protocol
- "full verify" → Run `--no-daemon` CI-parity tests
- "update state" → Update `docs/context/state.md`
- "clean the demo" → Use `scripts/clean-demo-branch.ps1`
- "update memory" → Update this file + `docs/context/state.md`

---

## BEAR-Specific Patterns

### Deterministic Contracts
- Exit codes are frozen: never add new ones without updating the contract
- Failure envelope (CODE/PATH/REMEDIATION) is frozen
- IR v1 schema is frozen: no per-target IR additions
- Generated artifacts must be reproducible across runs
- `--all` aggregation uses severity rank, not numeric max

### Kernel Purity
- `kernel/` is the trusted deterministic seed
- No LLM/agent logic in kernel
- Generic types in `com.bear.kernel.target`
- Target-specific code in `com.bear.kernel.target.<targetId>`

### Scope Discipline
- Stay within v1-preview scope
- Parked ideas in `roadmap/ideas/` are out-of-scope unless asked
- BEAR is not a behavior DSL, style engine, or runtime policy system
- New targets extend through the Target seam without changing core

### Documentation Hierarchy
- `docs/context/state.md` = project handoff (what was done, what's next)
- `.agent/Knowledge/partner_model.md` = behavioral memory (how to work together)
- These are complementary, not competing — both must be maintained
- `.kiro/steering/` files are thin bridges pointing here, not duplicates

---

## Calibration Notes

**This is a living document.** Update it at the end of every session.
Add a dated entry for any session with notable patterns, corrections, or new phrase mappings.
Don't just suggest updates — make them. Specific observations beat generic ones.

### 2026-03-12 — Initial Kiro Setup + Antigravity Integration
- Established steering documents for Kiro from repo analysis
- Adapted antigravity-kit concepts for BEAR's existing conventions
- Key insight: `.agent/` should be IDE-agnostic canonical home; `.kiro/steering/` should be thin bridges
- Key insight: devlog creation from session-end skill is NOT used (state.md serves this); partner_model updates ARE fully adopted
- Key insight: `state.md` = project handoff, `partner_model.md` = behavioral memory — complementary, not competing
- Correction received: "don't duplicate content between .agent/ and .kiro/steering/ — reference instead"
- Correction received: intelligent-routing skill should be kept (relevant to Kiro's subagent routing)

---

## Usage Notes for AI Agents

- Read this file at session start to calibrate collaboration style
- Update this file at session end — take ownership, don't just suggest
- Cross-reference with `docs/context/state.md` for project handoff context
- The two files serve different purposes: this = HOW, state.md = WHAT
- When in doubt about BEAR conventions, check `docs/context/` first
- Frozen contracts (exit codes, IR schema, failure envelope) are non-negotiable
