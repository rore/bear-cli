# Task Ordering and Dependency Map

## Implementation Order

Tasks are ordered by dependency. Tasks at the same level can be done in parallel.

### Level 1: Foundation (no prerequisites)
- **Task A1**: TargetDetector Interface, DetectedTarget, .bear/target.id Pin
- **Task A3**: Canonical Locator Schema Types

### Level 2: Registry + Profile (depends on A1)
- **Task A2**: Refactor TargetRegistry.resolve() to Use Detectors (needs A1)
- **Task A4**: Target/Profile Separation - GovernanceProfile (needs A1 for TargetId)

### Level 3: SPI + Node Detector (depends on A1, A4)
- **Task A5**: AnalyzerProvider SPI Draft Types (needs A4 for GovernanceProfile)
- **Task B1**: NodeTargetDetector (needs A1 for TargetDetector interface)

### Level 4: Node Registration (depends on A2, B1)
- **Task B2**: NODE in TargetId + NodeTarget Registration (needs A2, B1)

### Level 5: Node Core Implementation (depends on B2)
- **Task B3**: Node Compile Output (needs B2)
- **Task B4**: Node Governed Roots (needs B2)
- **Task B7**: impl.allowedDeps Unsupported Check (needs B2)

### Level 6: Node Scanners (depends on B3, B4)
- **Task B5**: NodeImportContainmentScanner (needs B3 for artifact paths, B4 for governed roots)
- **Task B6**: Node Drift Gate (needs B3 for compile output)
- **Task E1**: NodeProjectTestRunner (needs B2, independent of scanners)

### Level 7: Node Reach + Dynamic (depends on B4, B5)
- **Task C1**: NodeUndeclaredReachScanner (needs B4 for governed roots)
- **Task C2**: Dynamic Import Detection (needs B5 for import scanning pattern)

### Level 8: Node PR Check (depends on B2)
- **Task D1**: NodePrCheckContributor (needs B2, can be done in parallel with scanners)

## Dependency Graph (simplified)

```
A1 ──┬── A2 ──── B2 ──┬── B3 ──┬── B5
     │                 │        └── B6
     │                 ├── B4 ──┬── C1
     │                 │        └── (B5)
     │                 ├── B7
     │                 ├── E1
     │                 └── D1
     └── A4 ──── A5

A3 (independent, can run at any point)
```

## Total: 16 tasks across 5 phases

| Phase | Tasks | Estimated Total |
|---|---|---|
| A: Architecture Prerequisites | A1, A2, A3, A4, A5 | 5-8 hours |
| B: Node Scan Only | B1, B2, B3, B4, B5, B6, B7 | 8-11 hours |
| C: Node Undeclared Reach | C1, C2 | 2-3 hours |
| D: Node Dependency Governance | D1 | 2 hours |
| E: Node Project Verification | E1 | 1-2 hours |
| **Total** | **16 tasks** | **18-26 hours** |
