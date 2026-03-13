---
inclusion: manual
---

# Session End Protocol

Use when: user says "wrap up", "end session", "update state", "update memory",
approaching context limits, or at any natural break point.

**Full skill**: `.agent/skills/session-end/SKILL.md`

## Two Files, Two Purposes

| File | Purpose |
|------|---------|
| `docs/context/state.md` | Project handoff — what was done, what's next |
| `.agent/Knowledge/partner_model.md` | Behavioral memory — how to work together |

Both must be updated every session.

## BEAR Session Close Checklist

1. **Update `docs/context/state.md`** — Last Updated, Current Focus, Next Concrete Task, Session Notes
   - Keep Session Notes within `ContextDocsConsistencyTest` budget
   - Move oldest notes to `docs/context/archive/archive-state-history.md` when approaching cap
   - Write as if a fresh agent reads this tomorrow with no memory

2. **Update roadmap** (only if status/ordering changed)
   - `roadmap/board.md`, `roadmap/scope.md`
   - Relevant `roadmap/features/*.md` or `roadmap/ideas/*.md`

3. **Update canonical docs** (only when semantics changed)
   - `docs/context/ir-spec.md`, `docs/context/architecture.md`, `docs/context/governance.md`
   - `docs/context/project-log.md` — major architectural decisions only

4. **Update partner model** — `.agent/Knowledge/partner_model.md`
   - Add dated entry to Calibration Notes
   - Update What Works / What Doesn't / When User Says... if patterns changed
   - Take ownership — make the updates, don't just suggest them

5. **Run docs guard**
   ```bash
   ./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest
   ```

## Notes

- Do NOT create devlog files — `state.md` serves that purpose for BEAR
- Routine demo cleanup does NOT require state updates
- For significant decisions, use Considered/Rejected/Chosen pattern in relevant docs
