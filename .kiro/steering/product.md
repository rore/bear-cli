---
inclusion: auto
description: BEAR product overview - deterministic governance CLI and CI gate for agentic backend development
---

# BEAR Product Overview

BEAR (Block Enforceable Architectural Representation) is a deterministic governance CLI and CI gate for agentic backend development.

## Core Purpose

BEAR makes structural boundary changes explicit and visible in CI when agents generate code. It governs:
- Dependency boundaries and authority surfaces
- Port-based block interactions
- Structural drift detection
- Boundary expansion in PRs

## Key Concepts

- **Block**: A governed backend unit with declared operations, effects, and ports
- **BEAR IR**: YAML contract defining block boundaries (typically `bear.blocks.yaml`)
- **Ports**: Declared interfaces for cross-boundary interaction
- **Deterministic gates**: `validate`, `compile`, `fix`, `check`, `pr-check`

## Workflow

1. Agent updates BEAR IR when boundary authority must change
2. `bear compile` generates structural constraints from IR
3. Agent implements code inside those constraints
4. `check` and `pr-check` surface drift, bypass, and boundary expansion

## What BEAR Is Not

- Not a business-rules engine or runtime framework
- Not an agent orchestrator
- Not a code style enforcer
- Not a replacement for application tests
