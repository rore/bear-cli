# BEAR Project Log

This document captures the reasoning, debates, and architectural decisions behind BEAR so that context is not lost across sessions.

It reflects the state of thinking at the beginning of the project.

---

## 1. Origin of the Idea

BEAR emerged from questioning the evolution of software abstractions in the age of LLMs.

Observation:
- Past abstraction jumps (assembly -> C, C -> OOP, etc.) introduced higher-level structure while preserving deterministic enforcement.
- LLM-driven development changes *how* code is written, not necessarily system structure.
- LLMs are non-deterministic.
- Production systems require deterministic guarantees.

Core tension identified:
If agents generate code non-deterministically, what enforces system correctness and architectural boundaries?

BEAR is the proposed answer: a deterministic constraint layer between intent and implementation.

---

## 2. What BEAR Is (Conceptually)

BEAR is not:
- A vibe coding tool
- A replacement for developers
- A code generation platform
- An embedded AI system
- A spec-driven development replacement

BEAR is:
- A deterministic intermediate representation (IR) layer
- A constraint compiler
- An enforcement boundary for backend systems
- A CI gate that makes architectural guarantees executable

LLMs generate logic.
BEAR enforces boundaries.

---

## 3. Core Architectural Principles

### 3.1 Deterministic Core

BEAR core must:
- Contain no LLM logic
- Produce deterministic output
- Be reproducible in CI
- Be agent-agnostic

Reason:
Enterprise viability and long-term trust.

---

### 3.2 Agent-Agnostic Design

BEAR must work:
- With Copilot
- With Codex
- With no AI at all

Agent instructions are external (separate repo or repo-local files).
BEAR core cannot depend on any specific agent ecosystem.

---

### 3.3 Two-File Enforcement Model

Generated artifacts:
- Skeleton class (non-editable)
- Port interfaces derived from allowed effects
- Deterministic tests

Editable:
- Implementation file only

Reason:
Prevents drift.
Prevents agents from modifying enforcement artifacts.
Ensures regeneration safety.

---

### 3.4 BEAR as a Cage, Not a Generator

BEAR does not attempt to outcompete LLMs at writing code.

It:
- Defines allowed effects.
- Defines invariants.
- Defines idempotency rules.
- Generates enforcement tests.

Agents are free inside the cage.
They are not free outside it.

---

## 4. v0 Scope Lock

We explicitly constrained v0 to avoid over-architecture.

v0 includes:
- JVM target only
- Java demo
- Bank account domain
- Withdraw block
- Enforcement of:
  - effects.allow (capability allowlist)
  - idempotency by key
  - non_negative(field) invariant

v0 excludes:
- Spec -> IR automation
- Capability blocks in IR
- Block graph/composition modeling
- Behavior DSL
- requires/ensures language
- State delta modeling
- Infrastructure simulation
- Cross-service modeling
- Multi-language targets
- Plugin architecture
- Rich invariant catalog
- Embedded LLM logic
- UI support
- Enterprise policy layers

Reason:
The only proof needed is:
"Naive implementation fails. Correct implementation passes."

---

## 5. Demo Philosophy

We are not trying to prove:
"AI is bad at coding."

We are trying to prove:
"Systems degrade under change, and deterministic enforcement prevents drift."

The demo should show:
1. Correct initial implementation.
2. A realistic change request (e.g., optimization, refactor).
3. Regression introduced.
4. BEAR blocks regression.
5. Correct fix passes.

BEAR proves value under iteration, not under first implementation.

---

## 6. Early Self-Hosting Decision

Self-hosting remains a future direction and is not part of v0 delivery.

Constraint:
- Kernel remains trusted seed.
- Any future self-hosting must start with pure deterministic logic blocks.

First candidate:
NormalizeIr block.

NormalizeIr:
- Pure deterministic transformation.
- No effects.
- Canonicalizes BEAR IR.
- Easy to test with golden tests.
- Good bootstrapping proof.

Kernel still handles:
- CLI orchestration
- IO
- Target dispatch

Reason:
Demonstrate conceptual power without circular fragility.

---

## 7. Effects Model

effects.allow defines allowed capability operations.

These are:
- Explicit.
- Declarative.
- Enforced via generated structured port interfaces (`port` + `ops[]`), not free strings.

This is not full static analysis.
It is structural boundary enforcement.

Future expansion possible, but v0 is allowlist-only.

---

## 8. Idempotency Enforcement

Idempotency is first-class in BEAR IR.

Declared as:
- idempotency key field.

Generated test must:
- Invoke operation twice with same key.
- Ensure state not double-applied.

Purpose:
Address a real production failure class.
Commonly missed in agent-written code unless explicitly enforced.

---

## 9. Invariant Enforcement

v0 invariant:
- non_negative(field)

Generated test asserts:
- Field never negative.

Future invariant catalog deferred.

---

## 10. Risk Awareness

Identified risks:

- Overbuilding architecture before proof.
- Embedding LLM logic inside BEAR.
- Losing focus on demo.
- Turning BEAR into a development platform too early.
- Expanding scope beyond enforceable guarantees.

Mitigation:
Strict roadmap.
Strict v0 definition.
Explicit FUTURE.md parking lot.

---

## 11. BEAR in the Broader Ecosystem

Concern raised:
If agentic engineering dominates, does BEAR become irrelevant?

Conclusion:
No -- BEAR becomes more relevant.

As autonomy increases:
- Drift increases.
- Velocity increases.
- Regression risk increases.

Deterministic enforcement becomes more important.

BEAR is an aid for serious backend developers, not for vibe coders.

---

## 12. Long-Term Vision (Non-v0)

BEAR may evolve into:
- A stable IR layer
- A contract compiler
- A deterministic boundary for AI-generated systems
- Possibly a multi-target system

But this is intentionally deferred.

v0 must remain small and sharp.

---

## 13. Current Mental Model

Intent / Spec
    ->
BEAR IR (deterministic representation)
    ->
Generated skeleton + ports + tests
    ->
Agent / human implementation
    ->
bear check (deterministic enforcement)

---

## 14. Clarification Update (2026-02-12)

v0 framing was tightened to remove ambiguity:
- BEAR is a deterministic constraint compiler.
- BEAR v0 is structural enforcement plus deterministic guardrails.
- v0 explicitly documents both guarantees and non-guarantees.
- IR remains intentionally limited; no behavior DSL or broader behavioral semantics.
- Canonical demo IR includes root `version: v0`, invariant `kind`, and idempotency `store.port/getOp/putOp`.

Out-of-scope for v0 remains explicit:
- capability blocks in IR
- block graph/composition modeling
- requires/ensures language
- state delta modeling
- infrastructure simulation

---

This document preserves the reasoning that led to BEAR's design.
It should be updated only when a major architectural shift occurs.
