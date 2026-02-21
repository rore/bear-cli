# `bear compile` (v1.2)

## Command
`bear compile <ir-file> --project <path>`

## Semantic Enforcement Intent (v1.2)
`compile` generates wrapper-owned semantic enforcement only for semantics that are deterministically enforceable from declared IR boundary data.

In scope:
- idempotency wrapper flow (`get`/replay/`put`)
- output-level invariant checks

Out of scope:
- business policy inference
- semantics requiring undeclared context
- cross-port transaction guarantees

Canonical selection rule:
- `doc/IR_SPEC.md` -> `Semantics Decision Rule (Canonical)`

## Block Identity Resolution (v1.2 Lock+)
`compile` resolves the per-block identity (`blockKey`) deterministically before generation.

Resolution source:
1. If `bear.blocks.yaml` is discoverable by walking upward from `<project>`, attempt index tuple match.
2. Tuple match key:
   - `ir`: normalized repo-relative path string equality
   - `projectRoot`: normalized absolute path equality (index `projectRoot` resolved from repo root)
3. Outcomes:
   - `0` matches: single-IR fallback mode (`blockKey` from IR `block.name` canonicalization)
   - `1` match: index mode (`blockKey` from index entry `name` canonicalization)
   - `>1` matches: deterministic validation failure (`AMBIGUOUS_INDEX_ENTRIES`)

Canonical block-key normalization (frozen):
1. Insert space on camel transition: `([a-z0-9])([A-Z]) -> $1 $2`
2. Replace non-alphanumeric runs with one space: `[^A-Za-z0-9]+ -> " "`
3. Trim
4. Split on whitespace
5. Lowercase each token
6. Join with `-`
7. If no tokens, use `block`

Index/IR mismatch rule:
- when index mode is selected, compare canonical(index `name`) and canonical(IR `block.name`)
- mismatch fails deterministically at `block.name` before generation
- remediation is to align IR `block.name` with index identity intent

Generator contract:
- `blockKey` is resolved by CLI flow and injected into target compile
- generator must not re-derive `blockKey` from IR internally

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
- project-global runtime exception:
  - `build/generated/bear/runtime/src/main/java/com/bear/generated/runtime/BearInvariantViolationException.java`
  - generated once per project (write-if-diff)

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
    - `schemaVersion` (`v1`)
    - `surfaceVersion` (`3`)
    - `target` (`jvm`)
    - `block` (IR `block.name`)
    - `irHash` (SHA-256 of canonical normalized IR YAML bytes)
    - `generatorVersion` (`jvm-v1`)
    - `capabilities` (`name` from IR `effects.allow[*].port`, sorted `ops`)
    - `invariants` (v1.2: declared invariant rules as emitted from IR canonical form)
- `<blockKey>.wiring.json` at `<project>/build/generated/bear/wiring/<blockKey>.wiring.json`
  - deterministic wiring manifest used by `bear check` boundary-bypass enforcement
  - minified canonical JSON, UTF-8, LF, trailing newline
  - `<blockKey>` is the resolved compile identity (index-authoritative when tuple-resolved; IR fallback otherwise)
  - fields:
    - `schemaVersion` (`v2`)
    - `blockKey`
    - `entrypointFqcn`
    - `logicInterfaceFqcn`
    - `implFqcn`
    - `implSourcePath`
    - `requiredEffectPorts`
    - `constructorPortParams`
    - `logicRequiredPorts`
    - `wrapperOwnedSemanticPorts`
    - `wrapperOwnedSemanticChecks`

## Port Method Contract
- One interface per declared port.
- One method per declared op.
- Methods sorted alphabetically by normalized op name.
- Method signature is fixed in v1.2:
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
  - `<BlockName>Result execute(<BlockName>Request request, <LogicRequiredPortInterfaces...>)`
  - wrapper-owned semantic ports (for example idempotency store) are excluded from logic signatures
- Entrypoint must not instantiate `<BlockName>Impl` directly. Logic implementation is always injected via `<BlockName>Logic`.

## Idempotency Payload Schema (Normative)
For idempotent blocks, generated runtime uses the following `BearValue` payload contract:

- `getOp` input payload:
  - `key`: wrapper-computed storage key
    - format:
      - `<blockKey>|<fieldName>=<len>#<escapedValue>|...`
    - key-field source:
      - `idempotency.key` (single-field)
      - `idempotency.keyFromInputs` (ordered list, IR order)
    - `<len>` is UTF-8 byte length of escaped value
- `getOp` output payload:
  - miss: `hit` absent or not equal to `"true"`
  - hit: `hit="true"` and encoded result fields present
- `putOp` input payload:
  - `hit="true"`
  - `key`: same key used for `getOp`
  - `result.<outputFieldName>` for each declared output field

Result encoding/decoding rules:
- `key` conversion uses deterministic typed serialization.
- `result.<outputFieldName>` keys use canonical output field names from normalized IR.
- Fields are written in normalized output field order.
- Type encoding is deterministic string conversion:
  - `String`/`enum`: identity string
  - `BigDecimal`: `toString()`
  - `Integer`: base-10 string
  - `Boolean`: `true`/`false` (case-sensitive on decode)
- Replay payload validity is strict in v1.2:
  - when `hit="true"`, every declared `result.<outputFieldName>` key must be present
  - missing required replay fields fail fast with deterministic exception:
    - `idempotency replay payload missing field: result.<outputFieldName>`
  - invalid typed replay value fails fast with deterministic exception:
    - `idempotency replay payload invalid value: result.<outputFieldName>`

## Determinism Guarantees
- Deterministic generated file list, file paths, class names, method order, and imports.
- No timestamps/random/UUID values in generated output.
- Text format is canonical UTF-8 with LF newlines.
- Running `bear compile` twice with identical normalized IR yields byte-identical generated artifacts.

## Invariant Violation Marker (Normative)
Generated wrappers throw `com.bear.generated.runtime.BearInvariantViolationException` and the top-level exception message must begin with:

`BEAR_INVARIANT_VIOLATION|`

Marker payload contract (exact order/count):

`block=...|kind=...|field=...|observed=...|rule=...`

Rules:
- exactly 5 keyed fields
- no extras/duplicates/missing fields
- per-field escaping only (prefix/separators are never escaped)

Canonical `rule` value serialization:
- `non_negative` -> `non_negative`
- `non_empty` -> `non_empty`
- `equals` -> `equals:<escapedValue>`
- `one_of` -> `one_of:<n>|<len>#<v1>|<len>#<v2>|...` (IR order)

## Failure Atomicity (v0)
v0 does not guarantee atomic replacement of generated trees.
Partial generated output on compile failure is allowed.
