# `bear compile` (v0)

## Command
`bear compile <ir-file> --project <path>`

- IR parse + semantic validation must succeed before generation.
- Exit codes use the central registry in `spec/commands/exit-codes.md`.
  - `0` success
  - `2` schema/semantic validation error
  - `64` usage error
  - `74` IO error
  - `70` internal error
- Non-zero exits append the standard failure envelope from `spec/commands/exit-codes.md`:
  - `CODE=<enum>`
  - `PATH=<locator>`
  - `REMEDIATION=<deterministic-step>`

## Ownership Model

### BEAR-owned (always regenerated)
Location:
- `<project>/build/generated/bear/src/main/java/...`
- `<project>/build/generated/bear/src/test/java/...`

Rules:
- compile is block-scoped; BEAR deletes/regenerates only owned paths for the current IR block.
- File paths and content are deterministic.
- User edits in this tree are not preserved.

### User-owned (never overwritten)
Location:
- `<project>/src/main/java/...`

Rules:
- `<BlockName>Impl.java` is created only if missing.
- Existing impl files are never modified by `bear compile`.

## Package Model
- Generated package: `com.bear.generated.<blockname-lowercase-sanitized>`
- User impl package: `blocks.<package-segment>.impl`
- User impl path: `src/main/java/blocks/<package-segment-path>/impl/<BlockName>Impl.java`

`<package-segment>` uses the same Java-safe sanitizer as generated package segments.

Name normalization rules:
- Split on non-alphanumeric boundaries and camel-case transitions.
- Class names: PascalCase.
- Members/methods: camelCase.
- Leading digits are prefixed with `_`.
- Java keywords are suffixed with `_`.

## Generated Artifacts (Per Block)

Main sources:
- `<BlockName>.java` entrypoint (generated, non-editable)
- `<BlockName>Logic.java` user logic boundary interface
- `<BlockName>Request.java` immutable input model
- `<BlockName>Result.java` immutable output model
- `<PortName>Port.java` for each declared `effects.allow[*].port`
- `BearValue.java` deterministic envelope type for port calls
- `BearInvariantViolationException.java` runtime invariant failure type

User source:
- `<BlockName>Impl.java` in `src/main/java/blocks/<package-segment-path>/impl` (create once)

Generated tests (conditional):
- `<BlockName>IdempotencyTest.java` when idempotency is declared
- `<BlockName>InvariantNonNegativeTest.java` when invariant is declared

Generated metadata:
- `<blockKey>.surface.json` at `<project>/build/generated/bear/surfaces/<blockKey>.surface.json`
  - deterministic surface manifest used by `bear check` boundary classification
  - minified canonical JSON, UTF-8, LF, trailing newline
  - fields:
    - `schemaVersion` (`v0`)
    - `surfaceVersion` (`2`)
    - `target` (`jvm`)
    - `block` (IR `block.name`)
    - `irHash` (SHA-256 of canonical normalized IR YAML bytes)
    - `generatorVersion` (`jvm-v0`)
    - `capabilities` (`name` from IR `effects.allow[*].port`, sorted `ops`)
    - `invariants` (v0: `kind=non_negative`, `field`)
- `<blockKey>.wiring.json` at `<project>/build/generated/bear/wiring/<blockKey>.wiring.json`
  - deterministic wiring manifest used by `bear check` boundary-bypass enforcement
  - minified canonical JSON, UTF-8, LF, trailing newline
  - fields:
    - `schemaVersion` (`v1`)
    - `blockKey`
    - `entrypointFqcn`
    - `logicInterfaceFqcn`
    - `implFqcn`
    - `implSourcePath`
    - `requiredEffectPorts`
    - `constructorPortParams`

## Port Method Contract
- One interface per declared port.
- One method per declared op.
- Methods sorted alphabetically by normalized op name.
- Method signature is fixed in v0:
  - `BearValue <op>(BearValue input);`
- No generics in port method signatures.

## Type Mapping (IR -> Java)
- `string` -> `String`
- `decimal` -> `BigDecimal`
- `int` -> `Integer`
- `bool` -> `Boolean`
- `enum` -> `String`

## Entrypoint Runtime Contract
- Entrypoint delegates business logic through `<BlockName>Logic`.
- If idempotency is declared:
  - invoke declared `getOp` before logic
  - on hit (`hit=true`) decode stored result and return (no exception-on-replay policy in v0)
  - on miss run logic, then call declared `putOp` with encoded result
- If `non_negative` invariant is declared:
  - enforce at runtime before returning result
  - throw `BearInvariantViolationException` on violation

## Wiring Signatures (Normative)
- Generated entrypoint constructor:
  - `public <BlockName>(<DeclaredPortInterfaces...>, <BlockName>Logic logic)`
- Generated entrypoint execution method:
  - `public <BlockName>Result execute(<BlockName>Request request)`
- Generated logic interface method:
  - `<BlockName>Result execute(<BlockName>Request request, <DeclaredPortInterfaces...>)`
- Entrypoint must not instantiate `<BlockName>Impl` directly. Logic implementation is always injected via `<BlockName>Logic`.

## Idempotency Payload Schema (Normative)
For idempotent blocks, generated runtime uses the following `BearValue` payload contract:

- `getOp` input payload:
  - `key`: string form of the declared `idempotency.key` request field
- `getOp` output payload:
  - miss: `hit` absent or not equal to `"true"`
  - hit: `hit="true"` and encoded result fields present
- `putOp` input payload:
  - `hit="true"`
  - `key`: same key used for `getOp`
  - `result.<outputFieldName>` for each declared output field

Result encoding/decoding rules:
- `key` conversion uses the same deterministic type encoding rules listed below.
- `result.<outputFieldName>` keys use canonical output field names from normalized IR.
- Fields are written in normalized output field order.
- Type encoding is deterministic string conversion:
  - `String`/`enum`: identity string
  - `BigDecimal`: `toString()`
  - `Integer`: base-10 string
  - `Boolean`: `true`/`false`
- Replay payload validity is strict in v0:
  - when `hit="true"`, every declared `result.<outputFieldName>` key must be present
  - missing required replay fields fail fast with deterministic exception:
    - `idempotency replay payload missing field: result.<outputFieldName>`

## Determinism Guarantees
- Deterministic generated file list, file paths, class names, method order, and imports.
- No timestamps/random/UUID values in generated output.
- Text format is canonical UTF-8 with LF newlines.
- Running `bear compile` twice with identical normalized IR yields byte-identical generated artifacts.

## Invariant Violation Message (Normative)
For runtime `non_negative` failures, generated code must throw `BearInvariantViolationException` with:

`block=<BlockName>, invariant=non_negative, field=<fieldName>, actual=<value>`

This message format is stable and part of compile conformance.

## Failure Atomicity (v0)
v0 does not guarantee atomic replacement of generated trees.
Partial generated output on compile failure is allowed.
