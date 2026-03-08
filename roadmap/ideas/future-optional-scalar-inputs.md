---
id: future-optional-scalar-inputs
title: Optional scalar inputs in BEAR IR
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Goal

Support truly optional scalar input fields in BEAR IR and generated contracts, so agents do not need sentinel values or custom encoding to represent missing vs present.

## Problem

In the feature-extension demo, the optional transaction `note` field had to be modeled as a normal string input and then manually encoded or decoded in user code. That is awkward and leaks transport workarounds into domain logic.

## Desired Outcome

BEAR IR can express optional scalar inputs such as:
- optional string
- optional int
- optional enum

Generated contracts preserve presence versus absence cleanly.

## Scope

- IR schema
- validator
- code generation and runtime data model
- generated request and accessor classes
- docs and examples
- focused tests

## Non-Goals

- No full collection or union type system.
- No broad redesign of BEAR values.
- No change to existing required-field semantics.

## Minimal Design Target

Allow an input field to be marked optional in IR via a minimal explicit attribute such as:
- `required: false`

or an equivalent narrow syntax.

Generated Java behavior should preserve absence distinctly:
- request objects can represent absence separately from an empty or placeholder value
- block-to-block calls preserve absence without sentinel strings
- ports and wrappers do not force fake placeholder values

## Acceptance Criteria

1. IR validator accepts optional scalar inputs.
2. Generated code supports reading presence and absence explicitly.
3. The transaction-note scenario can be modeled without Base64 or sentinel tricks.
4. Existing required-field behavior remains unchanged.
5. Tests cover validate, compile, and runtime behavior for optional string input.
