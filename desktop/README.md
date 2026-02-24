# Desktop — Tauri v2 App (Legacy)

> **Note**: This is the legacy Tauri-based desktop app. The current architecture uses a native macOS Swift app (`macos/`).

Desktop app built with Rust backend + React/TypeScript frontend.
Receives speech recognition text from the Android client via WebSocket and injects text into the currently focused app.

## Architecture

```
src-tauri/src/          (Rust backend)
  lib.rs               Tauri app entry point, WS server start, event bridge
  protocol.rs          JSON message parsing/serialization (6 message types)
  client_registry.rs   Connected client management, heartbeat timeout tracking
  ws_server.rs         tokio-tungstenite WebSocket server, message dispatch
  injection.rs         Text injection (clipboard → Cmd+V, arboard + enigo)
  main.rs              Binary entry point

src/                    (React frontend)
  App.tsx              Main layout (ClientList + DictationView + Settings)
  types/messages.ts    TypeScript message type definitions
  hooks/useTauriEvents.ts  Tauri events → React state bridge
  components/
    DictationView.tsx  Real-time dictation text display
    ClientList.tsx     Connected client list
    Settings.tsx       Server port and settings display
```

## Build

```bash
pnpm install
pnpm tauri build
```

## Run (Development)

```bash
pnpm tauri dev
```

WebSocket server starts automatically on port 9876.

## Test

```bash
# Rust tests
cargo test --manifest-path src-tauri/Cargo.toml

# React tests
pnpm test        # watch mode
pnpm test:run    # single run
```

## Lint & Format

```bash
# Rust
cargo clippy --manifest-path src-tauri/Cargo.toml -- -D warnings
cargo fmt --manifest-path src-tauri/Cargo.toml --check

# TypeScript
pnpm lint
pnpm typecheck
pnpm format:check
```

## Key Dependencies

- **Tauri v2** — Desktop app framework
- **tokio-tungstenite** — Async WebSocket server
- **arboard** — Cross-platform clipboard
- **enigo** — OS key input simulation
- **React 19** + **Vite** — Frontend
- **Vitest** + **Testing Library** — Frontend tests
