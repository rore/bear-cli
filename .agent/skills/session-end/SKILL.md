---
name: session-end
description: Creates development log entries and updates knowledge files when wrapping up work sessions. Use when user indicates session is ending, mentions "update memory bank", approaches context limits, requests documentation, says "before we end", "let's document this", or explicitly asks to wrap up the session.
---

# Session End Documentation

**CRITICAL**: You have no memory between chat contexts. Document comprehensively - future sessions depend entirely on what you write here. More detail is better than less.

## When to Use This Skill

Use this skill when:
- User indicates session is ending
- User mentions "update memory bank" or "document this"
- Approaching context limits
- User requests documentation
- User says "before we end" or "let's wrap up"
- Explicitly asked to end the session

## Workflow

### 1. Run Helper Script

**IMPORTANT**: DO NOT invent timestamps. Always use the script output.

```bash
./.agent/skills/session-end/scripts/devlog_helper.sh
```

The script will output:
- Current timestamp (ISO format)
- Week/year information
- Path for new session file
- Previous session to read for context
- Same-day sessions (if any)
- Git branch and recent commits

### 2. Load Context

**ALWAYS** perform these steps:

1. **Read PREVIOUS_SESSION_PATH** (unless this is the first session)
   - Understand what was done last time
   - Check for open questions or remaining work
   - Maintain continuity

2. **Read SAME_DAY_SESSIONS** if multiple sessions today
   - Provides dense context
   - Shows progression through the day
   - Helps maintain consistent terminology

3. **Load intervening sessions** if continuing work on same topic/issue:
   ```bash
   grep -l 'topic-name' docs/devlogs/YYYY_wWW/*.md
   ```
   - Load ALL sessions between first and last occurrence chronologically
   - This captures architectural dependencies from intervening work

### 3. Review Entire Conversation

Identify and document:
- **Work completed** - What was built or fixed
- **Decisions made** - Including alternatives considered and rejected
- **Mistakes and corrections** - Valuable learning
- **Open questions** - What still needs answering
- **Mysteries** - Unexplained behaviors or patterns

### 4. Create Log Entry

Create a new file at `SESSION_FILE_PATH` from the script output.

**Filename format**: `YYYY-MM-DD_HHMM_description.md`
- Example: `2026-01-20_1430_agent-consolidation.md`

See [devlog_template.md](references/devlog_template.md) for detailed section guidance.

**Required sections**:
- Overview
- Context
- Implementation (or Problem Analysis)
- Testing
- Design Decisions (Considered/Rejected/Chosen pattern)
- Key Learnings
- Files Modified

**Conditional sections** (include when relevant):
- Remaining Work
- Open Questions
- Mysteries
- Commits
- References

### 5. Update Knowledge Files

**CRITICAL**: Take ownership of knowledge files - update them proactively. Don't just suggest changes, MAKE them.

**Update partner_model.md** (your working file - no announcements needed):
- Add new calibration notes with date and session context
- Document collaboration patterns observed this session
- Update "What Works Well" / "What Doesn't Work"
- Add "When User Says..." patterns discovered
- Document how user corrects mistakes (valuable for learning style)
- Maintain this actively after EVERY session

Location: `.agent/Knowledge/partner_model.md`

**Update BEAR project state** (project handoff, not behavioral memory):
- `docs/context/state.md` — Last Updated, Current Focus, Next Concrete Task, Session Notes
- `roadmap/board.md` + `roadmap/scope.md` — if roadmap status changed
- `roadmap/features/*.md` or `roadmap/ideas/*.md` — if item status changed
- `docs/context/project-log.md` — major architectural decisions only

---

## File Organization

```
docs/devlogs/
└── YYYY_wWW/                              # Week directory (auto-created)
    └── YYYY-MM-DD_HHMM_description.md     # Per-session file
```

**Important**:
- Each session gets its own file
- Never append to existing files
- Week directories are auto-created
- Use descriptive filenames (3-5 words)

---

## Key Reminders

### Session vs Task
Sessions end for various reasons, not just task completion:
- Task completed
- Context limits approaching
- Natural break points
- User needs to step away
- Multiple topics touched in one session

Document the work completed in the session, even if the overall task is incomplete.

### Timestamp Usage
- Use ONLY script-generated timestamps
- Never invent or calculate timestamps yourself

### Handover Mindset
Write as if a completely fresh AI agent will read this tomorrow with:
- No memory of this session
- No context about the project
- Only what you write to guide them

### Document Everything
- **Mistakes are valuable** - They prevent future errors
- **Mysteries matter** - Unexplained behaviors may be important
- **Line numbers** - Include for significant code changes
- **Why not just what** - Document reasoning behind decisions
- **Intervening sessions** - Load ALL sessions between first/last when continuing work

### Design Decisions Format
Always use the "Considered/Rejected/Chosen" pattern:

```markdown
**Considered**: Alternative approach A
**Rejected**: Reason it didn't work or wasn't suitable
**Chosen**: The approach we took and why
```

This prevents future sessions from reconsidering failed approaches.

---

## BEAR-Specific

### Two Complementary Files
Session end serves two distinct purposes:

| File | Purpose | When to Update |
|------|---------|----------------|
| `docs/context/state.md` | Project handoff — what was done, what's next | Every session |
| `.agent/Knowledge/partner_model.md` | Behavioral memory — how to work together | Every session |

Both must be updated. Neither replaces the other.

### BEAR Session Close Protocol
Follow `docs/context/start-here.md` → Session Close Protocol for the full checklist.
Key steps: update `state.md`, update roadmap files if changed, run docs guard test.

### No Devlogs
Do NOT create files in `docs/devlogs/`. Do NOT run `devlog_helper.sh`.
`docs/context/state.md` serves the project-handoff purpose for BEAR.

---

*This skill is critical for maintaining continuity across AI sessions.*
*Take the time to document comprehensively - future you will thank you.*