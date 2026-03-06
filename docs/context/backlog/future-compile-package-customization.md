# Future Spec: Compile Package Customization

## Status

Future initiative only; not part of the active P2/P3 queue.

## Goal

Allow `bear compile` to generate under a caller-chosen base package while preserving deterministic naming and BEAR ownership boundaries.

## Scope

- Add `--base-package <pkg>` to `bear compile`.
- Generate under `<base-package>.generated.<blockname-sanitized>` instead of the fixed `com.bear.generated...` package root.
- Preserve deterministic package and name sanitization rules.
- Preserve the two-tree ownership model between generated BEAR code and user-owned implementation code.

## Non-Goals

- No relaxation of deterministic naming rules.
- No per-file package overrides.
- No change to the generated-versus-user-owned ownership contract.

## Acceptance Criteria

1. Package customization is explicit and deterministic.
2. Generated package paths remain reproducible from the same inputs.
3. Existing compile or check ownership and drift contracts continue to hold.
