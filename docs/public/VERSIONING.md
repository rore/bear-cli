# Versioning

## Preview contract freeze

Preview freezes the public command contract for automation reliability:

- command invocation forms
- output line shapes used by parsers and CI
- exit-code meanings
- non-zero failure footer contract

These are documented in `CONTRACTS.md`, command pages, `exit-codes.md`, and `output-format.md`.

## What may still change in Preview

- internal implementation details
- performance characteristics
- additional diagnostics that do not break frozen output contracts
- new capabilities that do not change frozen existing contract behavior

## Breaking change policy

Breaking changes to frozen public contracts are deferred to post-Preview version transitions and should be called out explicitly in release notes.

## Related

- `CONTRACTS.md`
- `INDEX.md`
- `exit-codes.md`
- `output-format.md`
- `commands-check.md`
