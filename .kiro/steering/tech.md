---
inclusion: auto
description: BEAR technical stack including build system, language versions, and tooling
---

# BEAR Technical Stack

## Build System

- **Gradle** multi-module project
- Java application with Gradle wrapper
- Main modules: `kernel/` (core) and `app/` (CLI)

## Tech Stack

- **Language**: Java
- **Dependencies**: 
  - SnakeYAML 2.2 (YAML parsing)
  - Project dependency: `kernel` → `app`

## Application Entry Point

- Main class: `com.bear.app.BearCli`
- Application name: `bear`

## Common Commands

### Build and Test
```bash
# Run tests (use Gradle daemon for speed during iteration)
./gradlew :app:test :kernel:test

# Full verification (CI-parity, no daemon)
./gradlew --no-daemon :app:test :kernel:test

# Run specific test
./gradlew :app:test --tests ClassName
```

### BEAR CLI Commands
```bash
# Validate IR
bear validate --all --project .

# Compile/generate artifacts
bear compile --all --project .

# Fix generated artifacts
bear fix --all --project .

# Run enforcement gate
bear check --all --project .

# PR governance gate (local sanity)
bear pr-check --all --project . --base HEAD

# PR governance gate (CI flow)
bear pr-check --all --project . --base <target-branch>
```

## Verification Strategy

- **Fast-by-default**: Batch related edits before verification
- **Targeted tests**: Use method-level tests during iteration
- **Gradle daemon**: Default for local work (faster)
- **Full verify**: Use `--no-daemon` for CI-parity checks
- **Docs guard**: Run `ContextDocsConsistencyTest` before push

## Platform Support

- Windows: Use `.bat` wrapper (`.bear/tools/bear-cli/bin/bear.bat`)
- macOS/Linux: Use shell wrapper (`.bear/tools/bear-cli/bin/bear`)
