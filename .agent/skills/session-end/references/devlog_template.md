# Dev Log Entry Template

Comprehensive template for session documentation. Use this as a guide, not a straitjacket.

## Header Format

```
## YYYY-MM-DDThh:mm+zzzz - Brief Title
```

Example: `## 2026-01-04T14:30+0000 - Initial .agents Setup`

---

## Required Sections (Every Entry)

### Overview
High-level summary of what was accomplished. 2-3 sentences capturing the essence.

**Example**:
> Set up comprehensive .agents directory structure with agent instructions, skill guides, knowledge base, and reference materials. Created session-end workflow with devlog helper script. Established foundation for persistent AI agent memory across sessions.

### Context
Why this work? What's the background? Reference previous sessions, user requests, or issues.

**Include**:
- What triggered this work
- Connection to previous sessions
- Relevant background
- User's original request or problem

**Example**:
> User requested setup of .agents directory similar to their Atlomy-Atlas project. Goal was to provide AI assistants with persistent memory, project conventions, and comprehensive documentation across sessions.

### Problem Analysis OR Implementation Approach

**For bug fixes**: Root cause discovery, investigation process
**For features**: Architectural decisions, approach taken
**For refactoring**: Current state issues, target state benefits

Include "Considered/Rejected/Chosen" patterns for significant decisions:
```markdown
**Considered**: Alternative A - minimal docs to start
**Rejected**: Defeats purpose of comprehensive instructions
**Chosen**: Full template structure with placeholders for customization
```

### Implementation OR Solution

What code changes were made? What was built? Be specific about:
- File changes with line numbers when relevant
- Key code patterns or algorithms
- Build issues encountered and fixed
- Configuration changes
- Dependencies added or updated

**Example**:
> Created 14 new files in .agents/ directory:
> - AGENT.md (main instructions)
> - USAGE.md (quick start)
> - skills/ with 6 guides (development, testing, git, deployment, session-end)
> - knowledge/ with 3 files (overview, architecture, partner_model)
> - references/ with 3 files (coding standards, commands, devlog template)
> - devlog_helper.sh (executable script)

### Testing

How was it validated?
- Commands run and their output
- Edge cases tested
- What worked, what didn't
- Manual testing performed

**Example**:
> Verified directory structure with `ls -laR .agents/`. Tested devlog_helper.sh script execution. Confirmed all markdown files are properly formatted and cross-referenced.

### Design Decisions

Critical section! Document "why X not Y" thinking:
- **Considered**: Alternative approaches
- **Rejected**: Why they didn't work
- **Chosen**: Rationale for the approach taken

Use this format for every significant decision made during the session.

**Example**:
```markdown
**Considered**: Copy Atlomy files directly
**Rejected**: Too project-specific for reuse
**Chosen**: Adapted templates with generic placeholders

**Considered**: Minimal structure to iterate later
**Rejected**: Incomplete structure doesn't guide usage
**Chosen**: Complete structure showing what to fill in
```

### Key Learnings

What did we discover?
- Mistakes made and how corrected
- Unexpected behaviors or patterns
- Things that surprised us
- Better ways to approach similar problems in future
- Gotchas to remember

**Example**:
> - Session-end skill requires both helper script AND template reference
> - Three-tier knowledge system (critical/detailed/reference) matches how developers naturally seek information
> - Generic templates more valuable than project-specific examples for reusability

### Files Modified

Complete list with paths and type of change:
```
.agents/README.md (created)
.agents/AGENT.md (created)
.agents/skills/session-end.md (created)
src/components/Button.tsx (modified, lines 45-67)
package.json (modified, added dependency)
```

---

## Conditional Sections (Include When Relevant)

### Remaining Work
Unfinished tasks, next steps, follow-up items. Critical for session handoff.

**Example**:
> - Customize AGENT.md with actual tech stack
> - Fill in project_overview.md with project vision
> - Create first devlog entry to test workflow
> - Update architecture.md as decisions are made

### Open Questions
Things we need to figure out but haven't yet. Include context for why they matter.

**Example**:
> - Should we use TypeScript strict mode? (affects development workflow)
> - Which testing framework? (Jest vs Vitest - need to decide based on project needs)
> - Deployment target? (affects build configuration)

### Mysteries and Uncertainties
Things we don't understand. Be explicit about unknowns - they may be important.

**Example**:
> - Unclear why webpack is bundling certain files twice (may impact performance)
> - Test occasionally fails on CI but not locally (flaky test or environment issue?)
> - API sometimes returns 429 without obvious rate limit hit (need investigation)

### Commits
Include commit hashes and messages if committed, or note if uncommitted with rationale.

**Example**:
```
abc123f feat: initialize .agents directory structure
def456a docs: add comprehensive agent documentation
```

Or:
```
Uncommitted - work in progress, waiting for user review before committing
```

### References
Links to related documentation, previous sessions, external resources.

**Example**:
> - Related session: 2026-01-03_1200_project-setup.md
> - Documentation: https://example.com/docs
> - Issue: #123 on GitHub
> - Similar pattern: .agents/knowledge/architecture.md

---

## Writing Guidelines

### 1. Document Comprehensively
Future sessions have no memory of this work. Include everything needed to understand:
- What was done
- Why it was done this way
- What was tried and rejected
- What remains to be done

### 2. Include Line Numbers
For significant code changes, include line numbers:
```
Modified src/auth/login.ts lines 34-56 to implement JWT validation
```

### 3. Document Mistakes
Mistakes are valuable learning for future sessions:
```
Initially tried to use X but encountered error Y. Switched to Z which worked because [reason].
```

### 4. Add Sections Freely
If a section helps clarify the work, add it:
- Performance Considerations
- Security Implications
- Migration Strategy
- Rollback Plan

### 5. Don't Omit Details
Nothing is obvious to a fresh context. Explain:
- Why decisions were made
- What alternatives existed
- What constraints influenced choices
- What assumptions were made

### 6. Use Examples
Concrete examples are more valuable than abstract descriptions:
```markdown
# ❌ Vague
Fixed the authentication bug

# ✅ Specific
Fixed authentication bug where JWT tokens expired after 1 hour instead of 24 hours due to incorrect `expiresIn` value in auth/jwt.ts line 45
```

### 7. Link Related Work
Reference related sessions, issues, documentation:
```markdown
This builds on the work from 2026-01-03_1200_auth-setup.md where we initially configured the auth system.
```

---

## Template Quick Reference

```markdown
## YYYY-MM-DDThh:mm+zzzz - Brief Title

### Overview
[2-3 sentence summary]

### Context
[Why this work, background, previous sessions]

### Implementation
[What was built, files changed, code written]

### Testing
[How it was validated]

### Design Decisions
**Considered**: [Option A]
**Rejected**: [Why not]
**Chosen**: [What we did and why]

### Key Learnings
[Discoveries, mistakes, surprises]

### Files Modified
[Complete list with paths]

### Remaining Work
[Next steps, unfinished tasks]

### Open Questions
[Things to figure out]
```

---

*This template ensures comprehensive documentation for session handoffs.*
*Adapt as needed - clarity and completeness matter more than format.*
