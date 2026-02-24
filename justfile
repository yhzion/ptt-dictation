# justfile - Cross-stack task orchestration

# Run all tests
test: test-desktop test-android

# Desktop tests (Rust + React)
test-desktop:
    cd desktop && cargo test --manifest-path src-tauri/Cargo.toml
    cd desktop && pnpm test -- --run

# Android tests
test-android:
    cd android && ./gradlew test

# Lint all
lint: lint-desktop lint-android

lint-desktop:
    cd desktop && cargo clippy --manifest-path src-tauri/Cargo.toml -- -D warnings
    cd desktop && cargo fmt --manifest-path src-tauri/Cargo.toml --check
    cd desktop && pnpm lint
    cd desktop && pnpm typecheck

lint-android:
    cd android && ./gradlew ktlintCheck detekt

# Format all
fmt:
    cd desktop && cargo fmt --manifest-path src-tauri/Cargo.toml
    cd desktop && pnpm format

# Build all
build: build-desktop build-android

build-desktop:
    cd desktop && pnpm tauri build

build-android:
    cd android && ./gradlew assembleDebug
