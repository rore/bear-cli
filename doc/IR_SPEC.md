# BEAR IR v1 Spec (Canonical)

## Purpose
BEAR IR is a strict, machine-checkable contract for one logic block.
It is intentionally constrained so validate/normalize/compile/check behavior stays deterministic.

## Scope Lock (v1.2)
v1 supports:
- one `logic` block per IR file
- structured effect ports
- contract inputs/outputs
- idempotency with either `key` or `keyFromInputs`
- invariant rules with explicit `kind/scope/field/params`
- optional implementation dependency allow-list (`impl.allowedDeps`)

v1 does not support:
- block graphs/composition DSL
- behavior DSL (`requires/ensures`)
- transaction semantics / cross-port atomicity
- undeclared dynamic semantics outside declared IR fields

## Semantics Decision Rule (Canonical)
BEAR enforces a semantic only if all are true:
- it can be implemented as wrapper-owned behavior/checks using declared inputs/outputs and declared ports
- it requires no hidden context unless that context is explicitly declared as an input or port
- it is deterministic and implementable by the active target
- it has a frozen, testable contract (for example key format, marker format, and error envelope)

If any condition fails, the semantic is out of scope for BEAR.

## Model
Root object:
- `version` (required, must be `v1`)
- `block` (required)

`block` fields:
- `name` (required)
- `kind` (required, must be `logic`)
- `contract` (required)
- `effects` (required)
- `idempotency` (optional)
- `invariants` (optional)
- `impl` (optional)

`contract` fields:
- `inputs` (required non-empty list)
- `outputs` (required non-empty list)

Field shape:
- `name` (required)
- `type` (required enum: `string`, `decimal`, `int`, `bool`, `enum`)

`effects` fields:
- `allow` (required list, may be empty)

Effect-port shape:
- `port` (required)
- `ops` (required list, may be empty)

## Idempotency Schema
When `block.idempotency` is present:
- exactly one of:
  - `key` (single input field name)
  - `keyFromInputs` (non-empty ordered list of input field names)
- `store` (required):
  - `port`
  - `getOp`
  - `putOp`

Validation:
- referenced key fields must exist in `contract.inputs`
- `keyFromInputs` values must be unique
- `store.port` must exist in `effects.allow[*].port`
- `store.getOp` / `store.putOp` must exist under that port

## Invariant Schema
`block.invariants` is a list of rules.
Each rule shape:
- `kind` (required enum)
- `scope` (required in canonical form, must be `result`)
- `field` (required output field name)
- `params` (optional map; kind-specific)

Supported kinds:
- `non_negative`
- `non_empty`
- `equals`
- `one_of`

Kind rules:
- `non_negative`
  - output type must be `int` or `decimal`
  - no params allowed
- `non_empty`
  - output type must be `string`
  - no params allowed
- `equals`
  - requires `params.value` (string)
  - `params.values` forbidden
- `one_of`
  - requires non-empty `params.values` (unique string list)
  - `params.value` forbidden

## `impl.allowedDeps`
Optional:
- `impl.allowedDeps` list entries:
  - `maven` (exact `groupId:artifactId`)
  - `version` (pinned exact version)

Validation:
- no wildcard/range forms
- duplicate `maven` entries are invalid

## Validation Rules (Strict)
- fail on unknown keys at every level
- fail on invalid enums
- fail on invalid references
- input/output names unique
- port names unique
- ops unique within each port
- idempotency exactly-one-of (`key`, `keyFromInputs`)
- invariant field must reference declared output
- invariant scope must be `result`

## Deterministic Normalization
Canonical form:
- root key order: `version`, `block`
- `block` key order: `name`, `kind`, `contract`, `effects`, `idempotency`, `invariants`, `impl`
- sort inputs/outputs by `name`
- sort ports by `port`
- sort ops within each port
- preserve invariant list order (IR order)
- sort `impl.allowedDeps` by `maven`
- emit canonical invariant shape (`scope` + explicit `params` object)

## Wrapper-Owned Semantics (Contract)
Generated wrappers own semantic enforcement:
- idempotency get/replay/put is wrapper-owned
- invariants are checked on:
  - fresh logic result
  - replay-decoded result

Logic implementations are not the semantic authority for these checks.

## Why Idempotency Is Included
Idempotency is included because it satisfies the decision rule without extra domain context:
- key material comes from declared request inputs
- side-effect boundaries come from declared ports/ops
- replay payload shape comes from declared outputs
- storage boundary is explicit (`idempotency.store`)

This allows one deterministic guarantee:
- same computed key -> no duplicate side effects -> deterministic replay result

BEAR uses a frozen idempotency contract for enforceability and reproducibility. Contract variation belongs in explicit future IR semantics, not ad hoc logic conventions.

## Why Invariants Are Limited
`invariants` in v1.2 are intentionally output-level structural checks:
- `non_negative`
- `non_empty`
- `equals`
- `one_of`

They are boundary-checkable and deterministic. They do not encode business policy inference.

## Explicit Non-Goals
- BEAR is not an application runtime framework.
- BEAR is not a business rules engine.
- BEAR does not infer or guess undeclared semantics.
- BEAR does not guarantee cross-port transaction atomicity.

## Canonical Example (excerpt)
```yaml
version: v1
block:
  name: Withdraw
  kind: logic
  contract:
    inputs:
      - name: txId
        type: string
    outputs:
      - name: balance
        type: decimal
  effects:
    allow:
      - port: idempotency
        ops: [get, put]
  idempotency:
    key: txId
    store:
      port: idempotency
      getOp: get
      putOp: put
  invariants:
    - kind: non_negative
      scope: result
      field: balance
      params: {}
```

## Notes
- IR defines semantics; targets must enforce them or `check` fails deterministically.
- v1 does not prove cross-port atomicity.
