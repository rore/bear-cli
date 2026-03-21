# Versioning

## Preview contract stability

Preview keeps the public command contract stable for automation reliability:

- command invocation forms
- output line shapes used by parsers and CI
- exit-code meanings
- non-zero failure footer contract

These are documented in [CONTRACTS.md](CONTRACTS.md), command pages, [exit-codes.md](exit-codes.md), and [output-format.md](output-format.md).

## What may still change in Preview

This repo is a proof of concept and internals may evolve quickly.
Within Preview, changes are expected in:

- internal implementation details
- performance characteristics
- additional diagnostics that do not break frozen output contracts
- new capabilities that do not change existing contract behavior

## Breaking change policy

Breaking changes to the stable public contracts are deferred to post-Preview
transitions and should be called out explicitly.