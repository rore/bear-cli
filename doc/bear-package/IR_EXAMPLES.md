# IR_EXAMPLES.md

Purpose:
- Minimal generic examples for valid BEAR v1 IR and multi-block indexing.

## Example A: Minimal Single Block

`spec/process-task.bear.yaml`

```yaml
version: v1
block:
  name: ProcessTask
  kind: logic
  contract:
    inputs:
      - name: requestId
        type: string
      - name: workloadUnits
        type: decimal
    outputs:
      - name: processed
        type: bool
      - name: remainingQuota
        type: decimal
  effects:
    allow:
      - port: quotaStore
        ops: [read, write]
      - port: idempotency
        ops: [get, put]
  idempotency:
    key: requestId
    store:
      port: idempotency
      getOp: get
      putOp: put
  invariants:
    - kind: non_negative
      scope: result
      field: remainingQuota
      params: {}
```

## Example B: Minimal Multi-Block (Indexed)

Note:
- IR files use `version: v1`; `bear.blocks.yaml` currently uses `version: v0`.

`bear.blocks.yaml`

```yaml
version: v0
blocks:
  - name: execution-core
    ir: spec/execution-core.bear.yaml
    projectRoot: .
  - name: activity-log
    ir: spec/activity-log.bear.yaml
    projectRoot: .
```

`spec/execution-core.bear.yaml`

```yaml
version: v1
block:
  name: ExecutionCore
  kind: logic
  contract:
    inputs:
      - name: requestId
        type: string
      - name: workloadUnits
        type: decimal
    outputs:
      - name: success
        type: bool
      - name: remainingQuota
        type: decimal
  effects:
    allow:
      - port: quotaStore
        ops: [read, write]
      - port: idempotency
        ops: [get, put]
  idempotency:
    key: requestId
    store:
      port: idempotency
      getOp: get
      putOp: put
  invariants:
    - kind: non_negative
      scope: result
      field: remainingQuota
      params: {}
```

`spec/activity-log.bear.yaml`

```yaml
version: v1
block:
  name: ActivityLog
  kind: logic
  contract:
    inputs:
      - name: requestId
        type: string
      - name: activityType
        type: enum
    outputs:
      - name: recorded
        type: bool
  effects:
    allow:
      - port: log
        ops: [append]
      - port: notifications
        ops: [emit]
```

## Notes

- Examples are intentionally generic and minimal.
- Use them as shape references, not as domain templates.
