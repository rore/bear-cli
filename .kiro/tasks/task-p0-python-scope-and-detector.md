# Task P0: Python Scope and Detector Planning

## Phase Reference
Phase P: Python Core Validation (Planning Only)

## Spec Reference
`.kiro/specs/phase-p-python-core-validation.md`

## Prerequisites
None (independent planning task, can run at any point)

## Purpose

Lock Python scope decisions in spec documents before implementation begins. This task produces
no code -- only reviewed and approved spec content. It ensures Python design choices are stable
before Node implementation finalizes patterns that Python must follow.

## Inputs
- `roadmap/ideas/future-python-implementation-context.md` (complete Python context)
- `roadmap/ideas/future-python-containment-profile.md` (containment profile)
- `roadmap/ideas/future-multi-target-spec-design.md` (Python Target Spec sections)
- `.kiro/specs/phase-a-architecture-prerequisites.md` (architecture types Python reuses)
- `.kiro/specs/phase-b-node-target-scan-only.md` (Node patterns Python follows)

## Scope Decisions to Review and Lock

1. **AST-first analysis**: Confirm Python `ast` module is the primary enforcement mechanism.
   No regex-only analysis for governance-grade claims. Document what AST covers (import
   extraction, alias tracking, dynamic import detection, eval/exec/compile detection) and
   what it cannot cover (aliased calls, string-based code generation).

2. **Strict vs relaxed profiles**: Confirm two-profile model (`python/service` strict default,
   `python/service-relaxed` opt-in). Document profile selection mechanism (`.bear/profile.id`
   or auto-derived) and behavioral differences (third-party import handling).

3. **Function/class locator expectations**: Confirm `CanonicalLocator` mapping for Python AST
   node types (`FUNCTION`, `CLASS`, `METHOD`, `MODULE`). Document lambda fallback naming
   (`<anonymous@module:startLine>`). Confirm decorators do not change symbol identity.

4. **eval/exec/compile handling**: Confirm `PARTIAL` confidence for direct call patterns.
   Document known limitations (aliased calls, string-based code generation). Do not claim
   complete coverage.

5. **Detector shape (PythonTargetDetector)**: Confirm required signals (`pyproject.toml`,
   lock file, mypy config, `src/blocks/`). Confirm `UNSUPPORTED` cases (workspaces, flat
   layout, namespace packages). Confirm `NONE` cases (`setuptools`-only, `conda`, `pip`-only,
   `pipenv`). Document first-slice scope boundaries explicitly.

## Outputs
- Reviewed `.kiro/specs/phase-p-python-core-validation.md` with all scope decisions locked
- No code files produced

## Acceptance Criteria
- All five scope decisions are documented in the phase spec
- Spec content is consistent with roadmap reference documents
- No implementation code is produced (planning only)
- Python locator expectations are compatible with `CanonicalLocator` from Phase A
- Detector shape is compatible with `TargetDetector` interface from Phase A
- Profile model is compatible with `GovernanceProfile` from Phase A

## Estimated Effort
1 hour (review and documentation only)
