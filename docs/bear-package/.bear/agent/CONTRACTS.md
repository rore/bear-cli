# CONTRACTS.md

Purpose:
- Normative BEAR contract surface.
- Canonical definitions, invariants, MUST/MUST NOT requirements.

## Normative Scope

This file is contract-only.
- Contains: definitions, MUST/MUST NOT rules, canonical paths, canonical formats, frozen semantics.
- Excludes: troubleshooting flows, failure triage playbooks, procedural step-by-step runbooks.


Automation interface contract:
1. In automation, `--agent` JSON on stdout is the authoritative control interface.
2. Procedural loop behavior belongs in `.bear/agent/REPORTING.md` and troubleshooting flow belongs in `.bear/agent/TROUBLESHOOTING.md`.

## Conflict Definition (Normative)

Conflict definition:
1. `SPEC_POLICY_CONFLICT` exists only when a spec requirement cannot be met without violating an explicit repo enforcement rule or BEAR contract rule, and the spec does not authorize changing that rule.

Positive examples:
1. Repo forbids `java.net.*` reach while spec explicitly requires an in-process HTTP server.
2. Repo forbids build-script edits while spec requires changing build scripts.

Non-examples:
1. `check`/`pr-check` failures caused by incorrect implementation.
2. Missing `bear.blocks.yaml` index in multi-block mode.
3. Placeholder stub detection failures.
4. A request to use HTTP in app-layer code, by itself, is not a conflict with lane-scoped import bans.

Rule:
1. Conflict state requires escalation; autonomous harness/policy/runtime rewiring is prohibited unless explicitly instructed.
2. Import-policy conflict exists only when the spec requires forbidden imports inside banned lanes (`impl`/`_shared/pure`) or another explicit policy-banned scope.

## Decomposition Signals (Normative)

Default decomposition:
1. Decomposition is an architectural decision based on state/effect/idempotency/lifecycle/authority signals, not endpoint count.
2. Group operations when they share the same state domain and compatible effect/idempotency/lifecycle/authority boundaries.

Explicit split signals:
1. `lifecycle_split`: independently deployable/evolving lifecycle boundaries.
2. `effects_split`: external capability boundaries (ports/ops) must be isolated.
3. `authority_split`: ownership/trust/approval authority boundaries must be isolated.
4. `state_domain_split`: state domain boundaries must be isolated.
5. `idempotency_split`: idempotency key/store shape is incompatible across candidate grouped operations.
6. `operation_multiplexer_anti_pattern`: unrelated operations are being forced through a mega router/switch contract.

Rule:
1. Add a new block only when at least one explicit split signal is present in spec evidence, or when spec explicitly requires separation.
2. Multiple external operations may be grouped when compatibility signals are `_same` and no split trigger applies.

IR v1 capability fact:
1. IR v1 supports one `logic` block per IR file with `block.operations` (multi-operation).
2. Block boundary authority remains block-level (`effects`, idempotency capability, allowed invariants).
3. Operation contracts/usages are per-operation subsets inside that block boundary.
4. Grouped decomposition is structural in v1 (not only reporting): one block can host multiple compatible operations.
5. Add/remove operation entrypoints is governance-relevant surface expansion.

## Contract Modeling Anti-Patterns (Normative)

1. MUST NOT encode unrelated externally visible operations as an action/command enum multiplexer inside one request unless the spec explicitly defines that router contract.
2. This anti-pattern rule does not imply endpoint-per-block decomposition.
3. Grouped decomposition is valid when compatibility signals are `_same` and no canonical split trigger applies.

## Canonical Wiring Recipe

Default adapter shape:
1. Keep `_shared` focused on shared state primitives and small pure utilities.
2. Prefer one adapter class per generated block package.
3. Place adapters only under governed roots:
- block root: `src/main/java/blocks/<blockKey>/...`
- shared governed root: `src/main/java/blocks/_shared/<blockKey>/...`
4. Each adapter class MUST implement generated ports from one generated block package.

Multi-block adapter opt-in:
1. Allowed only when intentional.
2. Class MUST be under `src/main/java/blocks/_shared/**`.
3. Marker MUST be exact `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL`.
4. Marker MUST appear within 5 non-empty lines above class declaration.
5. Completion report MUST include required governance-signal disposition fields.
Block-port discipline:
1. For `kind=block` dependencies, do not implement generated block-port interfaces under `src/main/java/**`; generated block clients are the only valid implementation path.
2. Do not call target block internals/wrappers directly from source block code; cross-block calls must route through generated block clients.
3. App wiring lane is `src/main/java/com/**`; direct execute of inbound target wrappers is forbidden.

## Policy Contract (`check`)

Policy files are optional:
1. `.bear/policy/reflection-allowlist.txt`
2. `.bear/policy/hygiene-allowlist.txt`

Allowlist file format (if present) MUST be:
1. UTF-8, one repo-relative path per line.
2. Forward slashes only.
3. Sorted lexicographically.
4. Unique entries.
5. No globs, no absolute paths, no trailing slash.

Rule invariants:
1. Missing policy file means empty allowlist.
2. Malformed policy file fails with `CODE=POLICY_INVALID`.
3. `--strict-hygiene` enables unexpected-path enforcement.
4. Classloading reflection in `src/main/**` is blocked unless exact-path allowlisted.
5. Governed logic -> governed impl binding via `META-INF/services` or `module-info.java provides` is blocked.

## Editable Boundaries

MUST NOT edit:
1. `build/generated/bear/**`

MUST edit implementation in user-owned paths:
1. `src/main/java/blocks/<pkg-segment>/impl/<BlockName>Impl.java`
2. `src/test/java/**`
3. Repo-owned IR/docs/scripts

Path/package contract:
1. Preferred impl path: `src/main/java/blocks/<pkg-segment>/impl/<BlockName>Impl.java`
2. Preferred package: `blocks.<pkg-segment>.impl`
3. User-owned `*Impl.java` MUST NOT be relocated under `src/main/java/com/bear/generated/**`

Forbidden autonomous infrastructure edits:
1. Do not edit `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `.bear/**`, or `bin/bear*` unless explicitly instructed.
2. Do not move impl seams to alternate roots, create duplicate shim copies in `_shared`, or override containment excludes to force checks green.

## Dependency Direction Invariants

1. `_shared` MUST NOT import or depend on app packages.
2. App packages MUST NOT implement generated `com.bear.generated.*Port` interfaces.
3. Generated port implementations MUST remain under governed roots.

## Lane Role and Purity Invariants

Path-role contract:
1. `src/main/java/blocks/**/impl/**` is logic lane only.
2. `src/main/java/blocks/**/adapter/**` is adapter lane (state/integration allowed).
3. `src/main/java/blocks/_shared/pure/**` is pure helper lane.
4. `src/main/java/blocks/_shared/state/**` is shared state lane.
5. Java files directly under `src/main/java/blocks/_shared/**` outside `pure`/`state` are invalid.

Purity invariants:
1. `impl` and `_shared/pure` MUST NOT declare mutable static shared state.
2. `impl` and `_shared/pure` MUST NOT use `synchronized`.
3. `impl` MUST NOT import/reference `blocks._shared.state.*`.

Scoped import invariants:
1. `impl` and `_shared/pure` MUST NOT import/reference `java.io.*`, `java.net.*`, `java.nio.file.*`.
2. `impl` MUST NOT import/reference `java.util.concurrent.*`.
3. These scoped import bans are path-scoped to guarded lanes and are not app-layer global bans unless another explicit repo policy states so.
4. App-layer HTTP usage is not a policy conflict unless spec/policy forces forbidden imports into banned lanes.

`_shared/pure` static-final constants:
1. Allowed by default: primitives, boxed primitives, `java.lang.String`, enum constants.
2. Additional immutable types require FQCN allowlist entry in `.bear/policy/pure-shared-immutable-types.txt`.
3. `static final` with `new ...` initializer in `_shared/pure` is forbidden unless declared/constructed type is allowlisted immutable.

Immutable allowlist format (`.bear/policy/pure-shared-immutable-types.txt`):
1. UTF-8 text file.
2. FQCN entries only (no simple names).
3. Sorted lexicographically.
4. Unique entries.
5. Blank lines and `#` comments are allowed and ignored.

Enforcement boundary:
1. Lane/package purity/import checks are structural token checks for deterministic governance.
2. These checks enforce layout/usage constraints; they do not prove full semantic correctness of IR/effects behavior.

## Reach Symmetry Deferred Note (Non-Enforced)

1. Semantic reach symmetry for import-form vs FQCN-form usage is planned.
2. This package version does not enforce that symmetry yet.
3. Allow/forbid policy for demo local server/network surfaces is deferred to a later release and must not be assumed as enforced now.

## Semantics Policy (wrapper-owned)

Selection rule:
- Enforce only semantics that are wrapper-checkable from declared inputs/outputs/ports, require no hidden context, are deterministic, and have frozen contracts.

Implications:
1. Idempotency and invariants are wrapper-owned semantics.
2. Do not push wrapper-owned semantics into impl conventions or suppressions.
3. Generated `Wrapper.of(<ports...>)` is the sanctioned default production wiring path.
4. `(ports..., Logic)` constructor remains available for tests/advanced injection.

## Multi-Block Governance Invariants

1. Multi-block state requires `bear.blocks.yaml`.
2. In multi-block state, canonical done gates are repository-level `--all` commands.
3. Removing `bear.blocks.yaml` to force per-IR fallback is invalid.
4. Governance-signal disposition requirements are defined in `.bear/agent/REPORTING.md`.

