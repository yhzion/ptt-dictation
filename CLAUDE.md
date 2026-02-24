# PTT Dictation - Development Guidelines

## Development Workflow (Mandatory)

All development must follow this order:

1. **Research** — Understand existing code, docs, and dependencies
2. **Plan** — Determine scope of changes, impact analysis, approach
3. **Write test cases** — Write failing tests first (Red)
4. **Implement** — Write minimum code to pass tests (Green)
5. **Verify** — Confirm all tests pass + regression tests remain Green

## TDD Rules

- Strictly follow Red → Green → Refactor cycle
- Start test cases at integration level, then refactor into unit tests
- All features must be testable — no exceptions
- UI/UX is also a test target — design is functionality
- Before modifying features, always verify regression tests are Green

## Code Quality Hooks

The following must be automatically validated via hooks during development:

- Dead code (unused code)
- Duplicate code
- Type errors
- Lint errors
- Format errors
- All warnings

### Per-Stack Quality Commands

- **Rust**: `cargo clippy -- -D warnings`, `cargo fmt --check`, `cargo test`
- **TypeScript/React**: `tsc --noEmit`, `eslint .`, `prettier --check .`, `vitest run`
- **Android/Kotlin**: `ktlint`, `detekt`, `./gradlew test`

## Project Structure

Monorepo: Server (macOS/Swift) and client (Android/Kotlin) use separate build systems, managed as a monorepo.

## Documentation Strategy

- Server/client have different build/deploy methods, so each maintains independent README
- Shared protocol spec is managed as single source at `docs/protocol-spec.md`
- Architecture changes must keep `docs/` in sync

### Document Locations

- `docs/` — Common docs (protocol, architecture, etc.)
- `macos/` — macOS app docs
- `android/` — Android client docs

### Per-Task Document Sets

All research and plans must be documented. Create directories per work item:

```
docs/<task-name>/
├── RESEARCH.md    # Research findings
├── PROGRESS.md    # Progress tracking
├── PLAN.md        # Implementation plan
├── SPEC.md        # Spec/requirements
└── CHECKLIST.md   # Checklist
```

## Pre-Task Skill/Agent Search (Mandatory)

Before starting any task:
1. Search for applicable **skills**
2. Search for applicable **agents**
3. If found, use the skill/agent to perform the task
4. Only proceed manually if none found
