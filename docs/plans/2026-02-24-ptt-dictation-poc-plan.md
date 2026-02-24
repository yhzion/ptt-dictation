# PTT Dictation PoC Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Android PTT 앱에서 음성→텍스트 변환 후 WebSocket으로 Tauri 데스크톱에 실시간 전달, macOS에서 텍스트 삽입하는 PoC

**Architecture:** Android(Kotlin/Compose) → SpeechRecognizer STT → WebSocket JSON → Tauri(Rust backend + React frontend) → 클립보드+붙여넣기 macOS 삽입. 모든 모듈은 trait/interface 기반 DI로 테스트 가능. TDD: 큰 단위 통합 테스트 → 작은 단위로 리팩토링.

**Tech Stack:** Tauri v2, Rust (tokio, tokio-tungstenite, serde, arboard, enigo), React + TypeScript (Vite, Vitest, Testing Library), Android (Kotlin, Jetpack Compose, OkHttp, kotlinx.serialization), pnpm workspaces, lefthook

---

## Task 1: Initialize Monorepo Structure

**Files:**
- Create: `pnpm-workspace.yaml`
- Create: `package.json` (root)
- Create: `justfile`
- Create: `lefthook.yml`
- Create: `.gitignore`

**Step 1: Initialize git and root package**

```bash
cd /Users/youngho.jeon/datamaker/ptt-dictation
git init
pnpm init
```

**Step 2: Create pnpm workspace config**

```yaml
# pnpm-workspace.yaml
packages:
  - "desktop"
```

**Step 3: Create justfile for cross-stack orchestration**

```just
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
```

**Step 4: Create lefthook config for code quality hooks**

```yaml
# lefthook.yml
pre-commit:
  parallel: true
  commands:
    rust-fmt:
      glob: "desktop/src-tauri/**/*.rs"
      run: cd desktop && cargo fmt --manifest-path src-tauri/Cargo.toml --check
    rust-clippy:
      glob: "desktop/src-tauri/**/*.rs"
      run: cd desktop && cargo clippy --manifest-path src-tauri/Cargo.toml -- -D warnings
    rust-test:
      glob: "desktop/src-tauri/**/*.rs"
      run: cd desktop && cargo test --manifest-path src-tauri/Cargo.toml
    ts-lint:
      glob: "desktop/src/**/*.{ts,tsx}"
      run: cd desktop && pnpm lint
    ts-typecheck:
      glob: "desktop/src/**/*.{ts,tsx}"
      run: cd desktop && pnpm typecheck
    ts-format:
      glob: "desktop/src/**/*.{ts,tsx}"
      run: cd desktop && pnpm format:check
    ts-test:
      glob: "desktop/src/**/*.{ts,tsx}"
      run: cd desktop && pnpm test -- --run
    kotlin-lint:
      glob: "android/**/*.kt"
      run: cd android && ./gradlew ktlintCheck
```

**Step 5: Create .gitignore**

```gitignore
# .gitignore
node_modules/
target/
dist/
.gradle/
build/
*.apk
.DS_Store
.env
```

**Step 6: Install lefthook and commit**

```bash
pnpm add -wD lefthook
npx lefthook install
git add pnpm-workspace.yaml package.json justfile lefthook.yml .gitignore pnpm-lock.yaml
git commit -m "chore: initialize monorepo with pnpm workspaces, justfile, lefthook"
```

---

## Task 2: Scaffold Tauri Desktop App + Testing Infrastructure

**Files:**
- Create: `desktop/` (via create-tauri-app)
- Modify: `desktop/package.json` (add test deps)
- Create: `desktop/vitest.config.ts`
- Create: `desktop/src/test-setup.ts`
- Modify: `desktop/src-tauri/Cargo.toml` (add deps)

**Step 1: Create Tauri app with React+TS template**

```bash
cd /Users/youngho.jeon/datamaker/ptt-dictation
pnpm create tauri-app@latest desktop -- --template react-ts --manager pnpm
cd desktop && pnpm install
```

**Step 2: Add Rust dependencies to Cargo.toml**

Add to `desktop/src-tauri/Cargo.toml` under `[dependencies]`:

```toml
tokio = { version = "1", features = ["full"] }
tokio-tungstenite = "0.24"
futures-util = "0.3"
serde = { version = "1", features = ["derive"] }
serde_json = "1"
arboard = "3"
enigo = { version = "0.2", features = ["serde"] }
log = "0.4"
env_logger = "0.11"
```

**Step 3: Add React testing dependencies**

```bash
cd desktop
pnpm add -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom
pnpm add -D eslint @typescript-eslint/eslint-plugin @typescript-eslint/parser prettier eslint-config-prettier
```

**Step 4: Create vitest config**

```typescript
// desktop/vitest.config.ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test-setup.ts",
    css: true,
  },
});
```

```typescript
// desktop/src/test-setup.ts
import "@testing-library/jest-dom";
```

**Step 5: Add scripts to desktop/package.json**

Add to `scripts`:

```json
{
  "test": "vitest",
  "test:run": "vitest run",
  "lint": "eslint src --ext .ts,.tsx",
  "typecheck": "tsc --noEmit",
  "format": "prettier --write src",
  "format:check": "prettier --check src"
}
```

**Step 6: Verify setup**

```bash
cd desktop && cargo check --manifest-path src-tauri/Cargo.toml
cd desktop && pnpm typecheck
```

**Step 7: Commit**

```bash
git add desktop/
git commit -m "chore: scaffold Tauri desktop app with React+TS and testing infrastructure"
```

---

## Task 3: Protocol Message Types (Rust) — TDD

**Files:**
- Create: `desktop/src-tauri/src/protocol.rs`
- Modify: `desktop/src-tauri/src/lib.rs` (add mod)

**Step 1: Write failing tests for message serialization**

```rust
// desktop/src-tauri/src/protocol.rs

use serde::{Deserialize, Serialize};

// --- Types (empty stubs to make tests compile) ---

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct HelloPayload {
    #[serde(rename = "deviceModel")]
    pub device_model: String,
    pub engine: String,
    pub capabilities: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PttStartPayload {
    #[serde(rename = "sessionId")]
    pub session_id: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PartialPayload {
    #[serde(rename = "sessionId")]
    pub session_id: String,
    pub seq: u32,
    pub text: String,
    pub confidence: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct FinalPayload {
    #[serde(rename = "sessionId")]
    pub session_id: String,
    pub text: String,
    pub confidence: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct AckPayload {
    #[serde(rename = "ackType")]
    pub ack_type: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(tag = "type")]
pub enum Message {
    #[serde(rename = "HELLO")]
    Hello {
        #[serde(rename = "clientId")]
        client_id: String,
        payload: HelloPayload,
    },
    #[serde(rename = "PTT_START")]
    PttStart {
        #[serde(rename = "clientId")]
        client_id: String,
        payload: PttStartPayload,
    },
    #[serde(rename = "PARTIAL")]
    Partial {
        #[serde(rename = "clientId")]
        client_id: String,
        timestamp: u64,
        payload: PartialPayload,
    },
    #[serde(rename = "FINAL")]
    Final {
        #[serde(rename = "clientId")]
        client_id: String,
        timestamp: u64,
        payload: FinalPayload,
    },
    #[serde(rename = "HEARTBEAT")]
    Heartbeat {
        #[serde(rename = "clientId")]
        client_id: String,
    },
    #[serde(rename = "ACK")]
    Ack {
        #[serde(rename = "clientId")]
        client_id: String,
        payload: AckPayload,
    },
}

/// Parse a JSON string into a Message
pub fn parse_message(json: &str) -> Result<Message, serde_json::Error> {
    serde_json::from_str(json)
}

/// Serialize a Message to JSON string
pub fn serialize_message(msg: &Message) -> Result<String, serde_json::Error> {
    serde_json::to_string(msg)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_hello() {
        let json = r#"{"type":"HELLO","clientId":"phone-01","payload":{"deviceModel":"Galaxy S23","engine":"Google","capabilities":["WS"]}}"#;
        let msg = parse_message(json).unwrap();
        match msg {
            Message::Hello { client_id, payload } => {
                assert_eq!(client_id, "phone-01");
                assert_eq!(payload.device_model, "Galaxy S23");
                assert_eq!(payload.engine, "Google");
                assert_eq!(payload.capabilities, vec!["WS"]);
            }
            _ => panic!("Expected Hello message"),
        }
    }

    #[test]
    fn test_parse_ptt_start() {
        let json = r#"{"type":"PTT_START","clientId":"phone-01","payload":{"sessionId":"s-abc123"}}"#;
        let msg = parse_message(json).unwrap();
        match msg {
            Message::PttStart { client_id, payload } => {
                assert_eq!(client_id, "phone-01");
                assert_eq!(payload.session_id, "s-abc123");
            }
            _ => panic!("Expected PttStart message"),
        }
    }

    #[test]
    fn test_parse_partial() {
        let json = r#"{"type":"PARTIAL","clientId":"phone-01","timestamp":1670000000000,"payload":{"sessionId":"s-abc123","seq":12,"text":"안녕하세요 오늘","confidence":0.60}}"#;
        let msg = parse_message(json).unwrap();
        match msg {
            Message::Partial { client_id, timestamp, payload } => {
                assert_eq!(client_id, "phone-01");
                assert_eq!(timestamp, 1670000000000);
                assert_eq!(payload.text, "안녕하세요 오늘");
                assert_eq!(payload.seq, 12);
            }
            _ => panic!("Expected Partial message"),
        }
    }

    #[test]
    fn test_parse_final() {
        let json = r#"{"type":"FINAL","clientId":"phone-01","timestamp":1670000000000,"payload":{"sessionId":"s-abc123","text":"안녕하세요. 오늘 회의는 오후 3시입니다.","confidence":0.93}}"#;
        let msg = parse_message(json).unwrap();
        match msg {
            Message::Final { client_id, payload, .. } => {
                assert_eq!(client_id, "phone-01");
                assert_eq!(payload.text, "안녕하세요. 오늘 회의는 오후 3시입니다.");
                assert!((payload.confidence - 0.93).abs() < f64::EPSILON);
            }
            _ => panic!("Expected Final message"),
        }
    }

    #[test]
    fn test_parse_heartbeat() {
        let json = r#"{"type":"HEARTBEAT","clientId":"phone-01"}"#;
        let msg = parse_message(json).unwrap();
        match msg {
            Message::Heartbeat { client_id } => assert_eq!(client_id, "phone-01"),
            _ => panic!("Expected Heartbeat message"),
        }
    }

    #[test]
    fn test_serialize_ack_roundtrip() {
        let msg = Message::Ack {
            client_id: "phone-01".into(),
            payload: AckPayload { ack_type: "FINAL".into() },
        };
        let json = serialize_message(&msg).unwrap();
        let parsed = parse_message(&json).unwrap();
        assert_eq!(msg, parsed);
    }

    #[test]
    fn test_parse_invalid_type() {
        let json = r#"{"type":"UNKNOWN","clientId":"phone-01"}"#;
        assert!(parse_message(json).is_err());
    }

    #[test]
    fn test_parse_invalid_json() {
        assert!(parse_message("not json").is_err());
    }
}
```

**Step 2: Add module to lib.rs**

Add `pub mod protocol;` to `desktop/src-tauri/src/lib.rs`.

**Step 3: Run tests to verify they pass**

```bash
cd desktop && cargo test --manifest-path src-tauri/Cargo.toml -- protocol
```

Expected: All 8 tests PASS

**Step 4: Commit**

```bash
git add desktop/src-tauri/src/protocol.rs desktop/src-tauri/src/lib.rs
git commit -m "feat: add protocol message types with serialization tests"
```

---

## Task 4: Client Registry — TDD

**Files:**
- Create: `desktop/src-tauri/src/client_registry.rs`
- Modify: `desktop/src-tauri/src/lib.rs` (add mod)

**Step 1: Write failing test first — big integration test**

```rust
// desktop/src-tauri/src/client_registry.rs

use std::collections::HashMap;
use std::time::{Duration, Instant};

#[derive(Debug, Clone)]
pub struct ClientInfo {
    pub client_id: String,
    pub device_model: String,
    pub engine: String,
    pub connected_at: Instant,
    pub last_heartbeat: Instant,
    pub current_session: Option<String>,
    pub last_partial_text: Option<String>,
}

pub struct ClientRegistry {
    clients: HashMap<String, ClientInfo>,
    heartbeat_timeout: Duration,
}

impl ClientRegistry {
    pub fn new(heartbeat_timeout_secs: u64) -> Self {
        Self {
            clients: HashMap::new(),
            heartbeat_timeout: Duration::from_secs(heartbeat_timeout_secs),
        }
    }

    pub fn register(&mut self, client_id: &str, device_model: &str, engine: &str) {
        let now = Instant::now();
        self.clients.insert(
            client_id.to_string(),
            ClientInfo {
                client_id: client_id.to_string(),
                device_model: device_model.to_string(),
                engine: engine.to_string(),
                connected_at: now,
                last_heartbeat: now,
                current_session: None,
                last_partial_text: None,
            },
        );
    }

    pub fn unregister(&mut self, client_id: &str) -> Option<ClientInfo> {
        self.clients.remove(client_id)
    }

    pub fn heartbeat(&mut self, client_id: &str) -> bool {
        if let Some(client) = self.clients.get_mut(client_id) {
            client.last_heartbeat = Instant::now();
            true
        } else {
            false
        }
    }

    pub fn get(&self, client_id: &str) -> Option<&ClientInfo> {
        self.clients.get(client_id)
    }

    pub fn get_mut(&mut self, client_id: &str) -> Option<&mut ClientInfo> {
        self.clients.get_mut(client_id)
    }

    pub fn set_session(&mut self, client_id: &str, session_id: Option<String>) -> bool {
        if let Some(client) = self.clients.get_mut(client_id) {
            client.current_session = session_id;
            true
        } else {
            false
        }
    }

    pub fn set_partial_text(&mut self, client_id: &str, text: Option<String>) -> bool {
        if let Some(client) = self.clients.get_mut(client_id) {
            client.last_partial_text = text;
            true
        } else {
            false
        }
    }

    pub fn timed_out_clients(&self) -> Vec<String> {
        let now = Instant::now();
        self.clients
            .iter()
            .filter(|(_, info)| now.duration_since(info.last_heartbeat) > self.heartbeat_timeout)
            .map(|(id, _)| id.clone())
            .collect()
    }

    pub fn connected_count(&self) -> usize {
        self.clients.len()
    }

    pub fn all_client_ids(&self) -> Vec<String> {
        self.clients.keys().cloned().collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::thread;

    // Big integration test: full client lifecycle
    #[test]
    fn test_client_lifecycle() {
        let mut registry = ClientRegistry::new(1); // 1s timeout for fast test

        // Register
        registry.register("phone-01", "Galaxy S23", "Google");
        assert_eq!(registry.connected_count(), 1);
        assert!(registry.get("phone-01").is_some());

        // Set session
        registry.set_session("phone-01", Some("s-abc".into()));
        assert_eq!(registry.get("phone-01").unwrap().current_session.as_deref(), Some("s-abc"));

        // Set partial text
        registry.set_partial_text("phone-01", Some("안녕".into()));
        assert_eq!(registry.get("phone-01").unwrap().last_partial_text.as_deref(), Some("안녕"));

        // Heartbeat
        assert!(registry.heartbeat("phone-01"));

        // Unregister
        let removed = registry.unregister("phone-01");
        assert!(removed.is_some());
        assert_eq!(registry.connected_count(), 0);
    }

    // Unit tests: individual operations
    #[test]
    fn test_register_new_client() {
        let mut registry = ClientRegistry::new(15);
        registry.register("phone-01", "Pixel 7", "Google");
        let client = registry.get("phone-01").unwrap();
        assert_eq!(client.device_model, "Pixel 7");
        assert_eq!(client.engine, "Google");
        assert!(client.current_session.is_none());
    }

    #[test]
    fn test_heartbeat_unknown_client_returns_false() {
        let mut registry = ClientRegistry::new(15);
        assert!(!registry.heartbeat("unknown"));
    }

    #[test]
    fn test_set_session_unknown_client_returns_false() {
        let mut registry = ClientRegistry::new(15);
        assert!(!registry.set_session("unknown", Some("s-1".into())));
    }

    #[test]
    fn test_timed_out_clients() {
        let mut registry = ClientRegistry::new(0); // 0s timeout = immediate
        registry.register("phone-01", "Galaxy", "Google");
        thread::sleep(Duration::from_millis(10));
        let timed_out = registry.timed_out_clients();
        assert!(timed_out.contains(&"phone-01".to_string()));
    }

    #[test]
    fn test_heartbeat_resets_timeout() {
        let mut registry = ClientRegistry::new(1);
        registry.register("phone-01", "Galaxy", "Google");
        registry.heartbeat("phone-01");
        let timed_out = registry.timed_out_clients();
        assert!(timed_out.is_empty());
    }

    #[test]
    fn test_multiple_clients() {
        let mut registry = ClientRegistry::new(15);
        registry.register("phone-01", "Galaxy", "Google");
        registry.register("phone-02", "Pixel", "Google");
        assert_eq!(registry.connected_count(), 2);
        registry.unregister("phone-01");
        assert_eq!(registry.connected_count(), 1);
    }
}
```

**Step 2: Add module to lib.rs**

Add `pub mod client_registry;` to `desktop/src-tauri/src/lib.rs`.

**Step 3: Run tests**

```bash
cd desktop && cargo test --manifest-path src-tauri/Cargo.toml -- client_registry
```

Expected: All 7 tests PASS

**Step 4: Commit**

```bash
git add desktop/src-tauri/src/client_registry.rs desktop/src-tauri/src/lib.rs
git commit -m "feat: add client registry with lifecycle management tests"
```

---

## Task 5: Text Injection Module (trait-based) — TDD

**Files:**
- Create: `desktop/src-tauri/src/injection.rs`
- Modify: `desktop/src-tauri/src/lib.rs` (add mod)

**Step 1: Write trait and mock tests**

```rust
// desktop/src-tauri/src/injection.rs

/// Trait for text injection — enables testing without OS interaction
pub trait TextInjector: Send + Sync {
    fn inject(&self, text: &str) -> Result<(), String>;
}

/// macOS: clipboard backup → set text → Cmd+V → restore clipboard
pub struct ClipboardPasteInjector;

impl TextInjector for ClipboardPasteInjector {
    fn inject(&self, text: &str) -> Result<(), String> {
        use arboard::Clipboard;
        use enigo::{Direction, Enigo, Key, Keyboard, Settings};

        let mut clipboard = Clipboard::new().map_err(|e| format!("Clipboard init: {e}"))?;
        let backup = clipboard.get_text().ok();

        clipboard.set_text(text).map_err(|e| format!("Clipboard set: {e}"))?;

        let mut enigo = Enigo::new(&Settings::default()).map_err(|e| format!("Enigo init: {e}"))?;
        enigo.key(Key::Meta, Direction::Press).map_err(|e| format!("Key press: {e}"))?;
        enigo.key(Key::Unicode('v'), Direction::Click).map_err(|e| format!("Key click: {e}"))?;
        enigo.key(Key::Meta, Direction::Release).map_err(|e| format!("Key release: {e}"))?;

        std::thread::sleep(std::time::Duration::from_millis(100));

        if let Some(old) = backup {
            let _ = clipboard.set_text(&old);
        }

        Ok(())
    }
}

/// No-op injector for testing — records injected texts
#[cfg(test)]
pub mod testing {
    use super::*;
    use std::sync::{Arc, Mutex};

    pub struct MockInjector {
        pub injected: Arc<Mutex<Vec<String>>>,
        pub should_fail: bool,
    }

    impl MockInjector {
        pub fn new() -> (Self, Arc<Mutex<Vec<String>>>) {
            let injected = Arc::new(Mutex::new(Vec::new()));
            (
                Self { injected: injected.clone(), should_fail: false },
                injected,
            )
        }

        pub fn failing() -> Self {
            Self {
                injected: Arc::new(Mutex::new(Vec::new())),
                should_fail: true,
            }
        }
    }

    impl TextInjector for MockInjector {
        fn inject(&self, text: &str) -> Result<(), String> {
            if self.should_fail {
                return Err("Mock injection failure".into());
            }
            self.injected.lock().unwrap().push(text.to_string());
            Ok(())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::testing::*;

    #[test]
    fn test_mock_injector_records_text() {
        let (injector, injected) = MockInjector::new();
        injector.inject("hello world").unwrap();
        injector.inject("second text").unwrap();
        let texts = injected.lock().unwrap();
        assert_eq!(*texts, vec!["hello world", "second text"]);
    }

    #[test]
    fn test_mock_injector_failure() {
        let injector = MockInjector::failing();
        let result = injector.inject("text");
        assert!(result.is_err());
        assert_eq!(result.unwrap_err(), "Mock injection failure");
    }

    #[test]
    fn test_trait_object_dispatch() {
        let (injector, injected) = MockInjector::new();
        let boxed: Box<dyn super::TextInjector> = Box::new(injector);
        boxed.inject("dynamic dispatch").unwrap();
        assert_eq!(injected.lock().unwrap()[0], "dynamic dispatch");
    }
}
```

**Step 2: Add module to lib.rs**

Add `pub mod injection;` to `desktop/src-tauri/src/lib.rs`.

**Step 3: Run tests**

```bash
cd desktop && cargo test --manifest-path src-tauri/Cargo.toml -- injection
```

Expected: All 3 tests PASS

**Step 4: Commit**

```bash
git add desktop/src-tauri/src/injection.rs desktop/src-tauri/src/lib.rs
git commit -m "feat: add trait-based text injection with mock for testing"
```

---

## Task 6: WebSocket Server + Protocol Dispatch — TDD

**Files:**
- Create: `desktop/src-tauri/src/ws_server.rs`
- Modify: `desktop/src-tauri/src/lib.rs` (add mod)

**Step 1: Write integration test — WS client connects, sends HELLO, receives ACK**

```rust
// desktop/src-tauri/src/ws_server.rs

use crate::client_registry::ClientRegistry;
use crate::injection::TextInjector;
use crate::protocol::{self, AckPayload, Message};
use futures_util::{SinkExt, StreamExt};
use std::sync::Arc;
use tokio::net::TcpListener;
use tokio::sync::Mutex;
use tokio_tungstenite::accept_async;

/// Event emitted to the frontend
#[derive(Debug, Clone, serde::Serialize)]
#[serde(tag = "kind")]
pub enum ServerEvent {
    ClientConnected {
        client_id: String,
        device_model: String,
    },
    ClientDisconnected {
        client_id: String,
    },
    PartialText {
        client_id: String,
        session_id: String,
        text: String,
        seq: u32,
        confidence: f64,
    },
    FinalText {
        client_id: String,
        session_id: String,
        text: String,
        confidence: f64,
    },
    PttStarted {
        client_id: String,
        session_id: String,
    },
}

/// Callback trait for server events (enables testing without Tauri)
pub trait EventEmitter: Send + Sync {
    fn emit(&self, event: ServerEvent);
}

pub async fn start_server(
    port: u16,
    registry: Arc<Mutex<ClientRegistry>>,
    injector: Arc<dyn TextInjector>,
    emitter: Arc<dyn EventEmitter>,
) -> Result<(), String> {
    let listener = TcpListener::bind(format!("0.0.0.0:{port}"))
        .await
        .map_err(|e| format!("Bind failed: {e}"))?;

    log::info!("WebSocket server listening on port {port}");

    loop {
        let (stream, addr) = listener
            .accept()
            .await
            .map_err(|e| format!("Accept failed: {e}"))?;

        let registry = registry.clone();
        let injector = injector.clone();
        let emitter = emitter.clone();

        tokio::spawn(async move {
            let ws = match accept_async(stream).await {
                Ok(ws) => ws,
                Err(e) => {
                    log::error!("WebSocket handshake failed from {addr}: {e}");
                    return;
                }
            };

            let (mut write, mut read) = ws.split();
            let mut client_id: Option<String> = None;

            while let Some(Ok(msg)) = read.next().await {
                if let Ok(text) = msg.to_text() {
                    match protocol::parse_message(text) {
                        Ok(message) => {
                            let response = handle_message(
                                message,
                                &registry,
                                &injector,
                                &emitter,
                                &mut client_id,
                            )
                            .await;

                            if let Some(resp_msg) = response {
                                if let Ok(json) = protocol::serialize_message(&resp_msg) {
                                    let _ = write
                                        .send(tokio_tungstenite::tungstenite::Message::Text(
                                            json.into(),
                                        ))
                                        .await;
                                }
                            }
                        }
                        Err(e) => log::warn!("Parse error from {addr}: {e}"),
                    }
                }
            }

            // Client disconnected
            if let Some(id) = &client_id {
                registry.lock().await.unregister(id);
                emitter.emit(ServerEvent::ClientDisconnected {
                    client_id: id.clone(),
                });
                log::info!("Client {id} disconnected");
            }
        });
    }
}

async fn handle_message(
    message: Message,
    registry: &Arc<Mutex<ClientRegistry>>,
    injector: &Arc<dyn TextInjector>,
    emitter: &Arc<dyn EventEmitter>,
    client_id_slot: &mut Option<String>,
) -> Option<Message> {
    match message {
        Message::Hello {
            client_id, payload, ..
        } => {
            registry
                .lock()
                .await
                .register(&client_id, &payload.device_model, &payload.engine);
            *client_id_slot = Some(client_id.clone());
            emitter.emit(ServerEvent::ClientConnected {
                client_id: client_id.clone(),
                device_model: payload.device_model,
            });
            Some(Message::Ack {
                client_id,
                payload: AckPayload {
                    ack_type: "HELLO".into(),
                },
            })
        }
        Message::PttStart {
            client_id, payload, ..
        } => {
            let mut reg = registry.lock().await;
            reg.set_session(&client_id, Some(payload.session_id.clone()));
            reg.set_partial_text(&client_id, None);
            emitter.emit(ServerEvent::PttStarted {
                client_id,
                session_id: payload.session_id,
            });
            None
        }
        Message::Partial {
            client_id, payload, ..
        } => {
            registry
                .lock()
                .await
                .set_partial_text(&client_id, Some(payload.text.clone()));
            emitter.emit(ServerEvent::PartialText {
                client_id,
                session_id: payload.session_id,
                text: payload.text,
                seq: payload.seq,
                confidence: payload.confidence,
            });
            None
        }
        Message::Final {
            client_id, payload, ..
        } => {
            {
                let mut reg = registry.lock().await;
                reg.set_session(&client_id, None);
                reg.set_partial_text(&client_id, None);
            }
            // Inject text into OS
            if let Err(e) = injector.inject(&payload.text) {
                log::error!("Injection failed: {e}");
            }
            emitter.emit(ServerEvent::FinalText {
                client_id: client_id.clone(),
                session_id: payload.session_id,
                text: payload.text,
                confidence: payload.confidence,
            });
            Some(Message::Ack {
                client_id,
                payload: AckPayload {
                    ack_type: "FINAL".into(),
                },
            })
        }
        Message::Heartbeat { client_id, .. } => {
            registry.lock().await.heartbeat(&client_id);
            None
        }
        Message::Ack { .. } => None, // Server doesn't process ACKs
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::injection::testing::MockInjector;
    use std::sync::Mutex as StdMutex;
    use tokio_tungstenite::connect_async;

    struct TestEmitter {
        events: Arc<StdMutex<Vec<ServerEvent>>>,
    }

    impl TestEmitter {
        fn new() -> (Self, Arc<StdMutex<Vec<ServerEvent>>>) {
            let events = Arc::new(StdMutex::new(Vec::new()));
            (Self { events: events.clone() }, events)
        }
    }

    impl EventEmitter for TestEmitter {
        fn emit(&self, event: ServerEvent) {
            self.events.lock().unwrap().push(event);
        }
    }

    #[tokio::test]
    async fn test_hello_ack_roundtrip() {
        let registry = Arc::new(Mutex::new(ClientRegistry::new(15)));
        let (injector, _) = MockInjector::new();
        let injector: Arc<dyn TextInjector> = Arc::new(injector);
        let (emitter, events) = TestEmitter::new();
        let emitter: Arc<dyn EventEmitter> = Arc::new(emitter);

        // Start server on random port
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        drop(listener);

        let reg = registry.clone();
        let inj = injector.clone();
        let emt = emitter.clone();
        tokio::spawn(async move {
            start_server(port, reg, inj, emt).await.ok();
        });

        tokio::time::sleep(std::time::Duration::from_millis(100)).await;

        // Connect client
        let url = format!("ws://127.0.0.1:{port}");
        let (mut ws, _) = connect_async(&url).await.unwrap();

        // Send HELLO
        let hello = r#"{"type":"HELLO","clientId":"test-01","payload":{"deviceModel":"Test","engine":"Mock","capabilities":["WS"]}}"#;
        ws.send(tokio_tungstenite::tungstenite::Message::Text(hello.into()))
            .await
            .unwrap();

        // Receive ACK
        let ack = ws.next().await.unwrap().unwrap();
        let ack_msg: Message = serde_json::from_str(ack.to_text().unwrap()).unwrap();
        match ack_msg {
            Message::Ack { client_id, payload } => {
                assert_eq!(client_id, "test-01");
                assert_eq!(payload.ack_type, "HELLO");
            }
            _ => panic!("Expected ACK"),
        }

        // Verify registry
        assert_eq!(registry.lock().await.connected_count(), 1);

        // Verify event emitted
        let evts = events.lock().unwrap();
        assert!(matches!(&evts[0], ServerEvent::ClientConnected { client_id, .. } if client_id == "test-01"));
    }

    #[tokio::test]
    async fn test_final_triggers_injection() {
        let registry = Arc::new(Mutex::new(ClientRegistry::new(15)));
        let (injector, injected_texts) = MockInjector::new();
        let injector: Arc<dyn TextInjector> = Arc::new(injector);
        let (emitter, _) = TestEmitter::new();
        let emitter: Arc<dyn EventEmitter> = Arc::new(emitter);

        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        drop(listener);

        let reg = registry.clone();
        let inj = injector.clone();
        let emt = emitter.clone();
        tokio::spawn(async move {
            start_server(port, reg, inj, emt).await.ok();
        });

        tokio::time::sleep(std::time::Duration::from_millis(100)).await;

        let url = format!("ws://127.0.0.1:{port}");
        let (mut ws, _) = connect_async(&url).await.unwrap();

        // Send HELLO first
        let hello = r#"{"type":"HELLO","clientId":"test-01","payload":{"deviceModel":"Test","engine":"Mock","capabilities":["WS"]}}"#;
        ws.send(tokio_tungstenite::tungstenite::Message::Text(hello.into())).await.unwrap();
        let _ = ws.next().await; // consume ACK

        // Send FINAL
        let final_msg = r#"{"type":"FINAL","clientId":"test-01","timestamp":1000,"payload":{"sessionId":"s-1","text":"테스트 텍스트","confidence":0.95}}"#;
        ws.send(tokio_tungstenite::tungstenite::Message::Text(final_msg.into())).await.unwrap();

        // Receive ACK
        let ack = ws.next().await.unwrap().unwrap();
        let ack_msg: Message = serde_json::from_str(ack.to_text().unwrap()).unwrap();
        assert!(matches!(ack_msg, Message::Ack { payload, .. } if payload.ack_type == "FINAL"));

        // Verify injection was called
        tokio::time::sleep(std::time::Duration::from_millis(50)).await;
        let texts = injected_texts.lock().unwrap();
        assert_eq!(texts[0], "테스트 텍스트");
    }
}
```

**Step 2: Add module to lib.rs**

Add `pub mod ws_server;` to `desktop/src-tauri/src/lib.rs`.

**Step 3: Run tests**

```bash
cd desktop && cargo test --manifest-path src-tauri/Cargo.toml -- ws_server
```

Expected: All 2 tests PASS

**Step 4: Commit**

```bash
git add desktop/src-tauri/src/ws_server.rs desktop/src-tauri/src/lib.rs
git commit -m "feat: add WebSocket server with protocol dispatch and injection integration"
```

---

## Task 7: Wire Up Tauri App (lib.rs)

**Files:**
- Modify: `desktop/src-tauri/src/lib.rs`

**Step 1: Write the Tauri app wiring**

```rust
// desktop/src-tauri/src/lib.rs

pub mod client_registry;
pub mod injection;
pub mod protocol;
pub mod ws_server;

use client_registry::ClientRegistry;
use injection::ClipboardPasteInjector;
use std::sync::Arc;
use tokio::sync::Mutex;

/// Tauri event emitter — bridges ws_server events to frontend
struct TauriEventEmitter {
    app_handle: tauri::AppHandle,
}

impl ws_server::EventEmitter for TauriEventEmitter {
    fn emit(&self, event: ws_server::ServerEvent) {
        let event_name = match &event {
            ws_server::ServerEvent::ClientConnected { .. } => "client-connected",
            ws_server::ServerEvent::ClientDisconnected { .. } => "client-disconnected",
            ws_server::ServerEvent::PartialText { .. } => "partial-text",
            ws_server::ServerEvent::FinalText { .. } => "final-text",
            ws_server::ServerEvent::PttStarted { .. } => "ptt-started",
        };
        let _ = self.app_handle.emit(event_name, &event);
    }
}

#[tauri::command]
async fn get_server_port() -> u16 {
    9876
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![get_server_port])
        .setup(|app| {
            let handle = app.handle().clone();
            let registry = Arc::new(Mutex::new(ClientRegistry::new(15)));
            let injector: Arc<dyn injection::TextInjector> =
                Arc::new(ClipboardPasteInjector);
            let emitter: Arc<dyn ws_server::EventEmitter> =
                Arc::new(TauriEventEmitter { app_handle: handle });

            tauri::async_runtime::spawn(async move {
                if let Err(e) = ws_server::start_server(9876, registry, injector, emitter).await {
                    log::error!("WebSocket server error: {e}");
                }
            });

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

**Step 2: Verify build**

```bash
cd desktop && cargo check --manifest-path src-tauri/Cargo.toml
```

**Step 3: Run all Rust tests**

```bash
cd desktop && cargo test --manifest-path src-tauri/Cargo.toml
```

Expected: All tests PASS (protocol + client_registry + injection + ws_server)

**Step 4: Commit**

```bash
git add desktop/src-tauri/src/lib.rs
git commit -m "feat: wire up Tauri app with WS server, registry, and injection"
```

---

## Task 8: TypeScript Message Types + Tauri Event Hooks

**Files:**
- Create: `desktop/src/types/messages.ts`
- Create: `desktop/src/hooks/useTauriEvents.ts`
- Create: `desktop/src/hooks/useTauriEvents.test.ts`
- Create: `desktop/src/types/messages.test.ts`

**Step 1: Write TypeScript types with validation tests**

```typescript
// desktop/src/types/messages.ts

export interface ClientConnectedEvent {
  kind: "ClientConnected";
  client_id: string;
  device_model: string;
}

export interface ClientDisconnectedEvent {
  kind: "ClientDisconnected";
  client_id: string;
}

export interface PartialTextEvent {
  kind: "PartialText";
  client_id: string;
  session_id: string;
  text: string;
  seq: number;
  confidence: number;
}

export interface FinalTextEvent {
  kind: "FinalText";
  client_id: string;
  session_id: string;
  text: string;
  confidence: number;
}

export interface PttStartedEvent {
  kind: "PttStarted";
  client_id: string;
  session_id: string;
}

export type ServerEvent =
  | ClientConnectedEvent
  | ClientDisconnectedEvent
  | PartialTextEvent
  | FinalTextEvent
  | PttStartedEvent;

export interface ClientState {
  clientId: string;
  deviceModel: string;
  connected: boolean;
  currentSession: string | null;
  partialText: string;
  finalTexts: string[];
}

export function createClientState(
  clientId: string,
  deviceModel: string
): ClientState {
  return {
    clientId,
    deviceModel,
    connected: true,
    currentSession: null,
    partialText: "",
    finalTexts: [],
  };
}

export function applyEvent(
  state: Map<string, ClientState>,
  event: ServerEvent
): Map<string, ClientState> {
  const next = new Map(state);

  switch (event.kind) {
    case "ClientConnected":
      next.set(
        event.client_id,
        createClientState(event.client_id, event.device_model)
      );
      break;
    case "ClientDisconnected":
      if (next.has(event.client_id)) {
        const client = { ...next.get(event.client_id)!, connected: false };
        next.set(event.client_id, client);
      }
      break;
    case "PttStarted":
      if (next.has(event.client_id)) {
        const client = {
          ...next.get(event.client_id)!,
          currentSession: event.session_id,
          partialText: "",
        };
        next.set(event.client_id, client);
      }
      break;
    case "PartialText":
      if (next.has(event.client_id)) {
        const client = {
          ...next.get(event.client_id)!,
          partialText: event.text,
        };
        next.set(event.client_id, client);
      }
      break;
    case "FinalText":
      if (next.has(event.client_id)) {
        const client = {
          ...next.get(event.client_id)!,
          partialText: "",
          currentSession: null,
          finalTexts: [...next.get(event.client_id)!.finalTexts, event.text],
        };
        next.set(event.client_id, client);
      }
      break;
  }

  return next;
}
```

**Step 2: Write tests for state management**

```typescript
// desktop/src/types/messages.test.ts

import { describe, it, expect } from "vitest";
import {
  applyEvent,
  createClientState,
  type ClientState,
  type ServerEvent,
} from "./messages";

describe("applyEvent", () => {
  it("adds client on ClientConnected", () => {
    const state = new Map<string, ClientState>();
    const event: ServerEvent = {
      kind: "ClientConnected",
      client_id: "phone-01",
      device_model: "Galaxy S23",
    };
    const next = applyEvent(state, event);
    expect(next.get("phone-01")).toEqual({
      clientId: "phone-01",
      deviceModel: "Galaxy S23",
      connected: true,
      currentSession: null,
      partialText: "",
      finalTexts: [],
    });
  });

  it("marks client disconnected on ClientDisconnected", () => {
    const state = new Map<string, ClientState>();
    state.set("phone-01", createClientState("phone-01", "Galaxy"));
    const event: ServerEvent = {
      kind: "ClientDisconnected",
      client_id: "phone-01",
    };
    const next = applyEvent(state, event);
    expect(next.get("phone-01")?.connected).toBe(false);
  });

  it("updates partial text", () => {
    const state = new Map<string, ClientState>();
    state.set("phone-01", createClientState("phone-01", "Galaxy"));
    const event: ServerEvent = {
      kind: "PartialText",
      client_id: "phone-01",
      session_id: "s-1",
      text: "안녕하세요",
      seq: 1,
      confidence: 0.6,
    };
    const next = applyEvent(state, event);
    expect(next.get("phone-01")?.partialText).toBe("안녕하세요");
  });

  it("appends final text and clears partial", () => {
    const state = new Map<string, ClientState>();
    const client = createClientState("phone-01", "Galaxy");
    client.partialText = "안녕하세요 오늘";
    client.currentSession = "s-1";
    state.set("phone-01", client);

    const event: ServerEvent = {
      kind: "FinalText",
      client_id: "phone-01",
      session_id: "s-1",
      text: "안녕하세요. 오늘 회의는 3시입니다.",
      confidence: 0.93,
    };
    const next = applyEvent(state, event);
    const updated = next.get("phone-01")!;
    expect(updated.partialText).toBe("");
    expect(updated.currentSession).toBeNull();
    expect(updated.finalTexts).toEqual(["안녕하세요. 오늘 회의는 3시입니다."]);
  });

  it("sets session on PttStarted", () => {
    const state = new Map<string, ClientState>();
    state.set("phone-01", createClientState("phone-01", "Galaxy"));
    const event: ServerEvent = {
      kind: "PttStarted",
      client_id: "phone-01",
      session_id: "s-abc",
    };
    const next = applyEvent(state, event);
    expect(next.get("phone-01")?.currentSession).toBe("s-abc");
  });

  it("ignores events for unknown clients", () => {
    const state = new Map<string, ClientState>();
    const event: ServerEvent = {
      kind: "PartialText",
      client_id: "unknown",
      session_id: "s-1",
      text: "test",
      seq: 1,
      confidence: 0.5,
    };
    const next = applyEvent(state, event);
    expect(next.size).toBe(0);
  });
});
```

**Step 3: Run tests**

```bash
cd desktop && pnpm test -- --run src/types/messages.test.ts
```

Expected: All 6 tests PASS

**Step 4: Create Tauri event hook**

```typescript
// desktop/src/hooks/useTauriEvents.ts

import { useEffect, useCallback, useState } from "react";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import { applyEvent, type ClientState, type ServerEvent } from "../types/messages";

export function useClientStates() {
  const [clients, setClients] = useState<Map<string, ClientState>>(new Map());

  const handleEvent = useCallback((event: ServerEvent) => {
    setClients((prev) => applyEvent(prev, event));
  }, []);

  useEffect(() => {
    const unlisteners: Promise<UnlistenFn>[] = [];

    const eventNames = [
      "client-connected",
      "client-disconnected",
      "partial-text",
      "final-text",
      "ptt-started",
    ] as const;

    for (const name of eventNames) {
      unlisteners.push(
        listen<ServerEvent>(name, (e) => handleEvent(e.payload))
      );
    }

    return () => {
      unlisteners.forEach((p) => p.then((fn) => fn()));
    };
  }, [handleEvent]);

  return clients;
}
```

**Step 5: Commit**

```bash
git add desktop/src/types/ desktop/src/hooks/
git commit -m "feat: add TypeScript message types, state management, and Tauri event hooks"
```

---

## Task 9: DictationView Component — TDD

**Files:**
- Create: `desktop/src/components/DictationView.tsx`
- Create: `desktop/src/components/DictationView.test.tsx`

**Step 1: Write component test first**

```tsx
// desktop/src/components/DictationView.test.tsx

import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DictationView } from "./DictationView";
import { type ClientState } from "../types/messages";

function makeClient(overrides: Partial<ClientState> = {}): ClientState {
  return {
    clientId: "phone-01",
    deviceModel: "Galaxy S23",
    connected: true,
    currentSession: null,
    partialText: "",
    finalTexts: [],
    ...overrides,
  };
}

describe("DictationView", () => {
  it("shows empty state when no client selected", () => {
    render(<DictationView client={null} />);
    expect(screen.getByText(/클라이언트를 선택/)).toBeInTheDocument();
  });

  it("shows device info", () => {
    render(<DictationView client={makeClient()} />);
    expect(screen.getByText(/Galaxy S23/)).toBeInTheDocument();
  });

  it("shows partial text with typing indicator", () => {
    render(
      <DictationView
        client={makeClient({ partialText: "안녕하세요", currentSession: "s-1" })}
      />
    );
    expect(screen.getByText(/안녕하세요/)).toBeInTheDocument();
    expect(screen.getByTestId("typing-indicator")).toBeInTheDocument();
  });

  it("shows final texts in history", () => {
    render(
      <DictationView
        client={makeClient({
          finalTexts: ["첫 번째 문장.", "두 번째 문장."],
        })}
      />
    );
    expect(screen.getByText("첫 번째 문장.")).toBeInTheDocument();
    expect(screen.getByText("두 번째 문장.")).toBeInTheDocument();
  });

  it("shows disconnected badge when not connected", () => {
    render(<DictationView client={makeClient({ connected: false })} />);
    expect(screen.getByText(/연결 끊김/)).toBeInTheDocument();
  });

  it("shows listening state during active session", () => {
    render(
      <DictationView client={makeClient({ currentSession: "s-1" })} />
    );
    expect(screen.getByTestId("listening-badge")).toBeInTheDocument();
  });
});
```

**Step 2: Write minimal component to pass tests**

```tsx
// desktop/src/components/DictationView.tsx

import type { ClientState } from "../types/messages";

interface Props {
  client: ClientState | null;
}

export function DictationView({ client }: Props) {
  if (!client) {
    return (
      <div className="dictation-empty">
        <p>클라이언트를 선택해 주세요</p>
      </div>
    );
  }

  return (
    <div className="dictation-view">
      <div className="dictation-header">
        <span className="device-name">{client.deviceModel}</span>
        {!client.connected && (
          <span className="badge badge-disconnected">연결 끊김</span>
        )}
        {client.currentSession && (
          <span className="badge badge-listening" data-testid="listening-badge">
            듣는 중...
          </span>
        )}
      </div>

      {client.partialText && (
        <div className="partial-text">
          <p>{client.partialText}</p>
          <span data-testid="typing-indicator" className="typing-indicator">
            ...
          </span>
        </div>
      )}

      <div className="final-texts">
        {client.finalTexts.map((text, i) => (
          <div key={i} className="final-text-item">
            {text}
          </div>
        ))}
      </div>
    </div>
  );
}
```

**Step 3: Run tests**

```bash
cd desktop && pnpm test -- --run src/components/DictationView.test.tsx
```

Expected: All 6 tests PASS

**Step 4: Commit**

```bash
git add desktop/src/components/DictationView.tsx desktop/src/components/DictationView.test.tsx
git commit -m "feat: add DictationView component with partial/final text display"
```

---

## Task 10: ClientList Component — TDD

**Files:**
- Create: `desktop/src/components/ClientList.tsx`
- Create: `desktop/src/components/ClientList.test.tsx`

**Step 1: Write component test first**

```tsx
// desktop/src/components/ClientList.test.tsx

import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ClientList } from "./ClientList";
import { type ClientState } from "../types/messages";

function makeClients(): Map<string, ClientState> {
  const map = new Map<string, ClientState>();
  map.set("phone-01", {
    clientId: "phone-01",
    deviceModel: "Galaxy S23",
    connected: true,
    currentSession: null,
    partialText: "",
    finalTexts: [],
  });
  map.set("phone-02", {
    clientId: "phone-02",
    deviceModel: "Pixel 7",
    connected: false,
    currentSession: null,
    partialText: "",
    finalTexts: [],
  });
  return map;
}

describe("ClientList", () => {
  it("shows empty state when no clients", () => {
    render(
      <ClientList
        clients={new Map()}
        selectedId={null}
        onSelect={() => {}}
      />
    );
    expect(screen.getByText(/연결된 클라이언트가 없습니다/)).toBeInTheDocument();
  });

  it("renders client entries with device model", () => {
    render(
      <ClientList
        clients={makeClients()}
        selectedId={null}
        onSelect={() => {}}
      />
    );
    expect(screen.getByText("Galaxy S23")).toBeInTheDocument();
    expect(screen.getByText("Pixel 7")).toBeInTheDocument();
  });

  it("shows connected/disconnected status", () => {
    render(
      <ClientList
        clients={makeClients()}
        selectedId={null}
        onSelect={() => {}}
      />
    );
    const indicators = screen.getAllByTestId("connection-indicator");
    expect(indicators).toHaveLength(2);
  });

  it("calls onSelect when client clicked", async () => {
    const onSelect = vi.fn();
    render(
      <ClientList
        clients={makeClients()}
        selectedId={null}
        onSelect={onSelect}
      />
    );
    await userEvent.click(screen.getByText("Galaxy S23"));
    expect(onSelect).toHaveBeenCalledWith("phone-01");
  });

  it("highlights selected client", () => {
    render(
      <ClientList
        clients={makeClients()}
        selectedId="phone-01"
        onSelect={() => {}}
      />
    );
    const selected = screen.getByTestId("client-phone-01");
    expect(selected).toHaveClass("selected");
  });
});
```

**Step 2: Write minimal component**

```tsx
// desktop/src/components/ClientList.tsx

import type { ClientState } from "../types/messages";

interface Props {
  clients: Map<string, ClientState>;
  selectedId: string | null;
  onSelect: (clientId: string) => void;
}

export function ClientList({ clients, selectedId, onSelect }: Props) {
  if (clients.size === 0) {
    return (
      <div className="client-list-empty">
        <p>연결된 클라이언트가 없습니다</p>
      </div>
    );
  }

  return (
    <div className="client-list">
      {Array.from(clients.values()).map((client) => (
        <div
          key={client.clientId}
          data-testid={`client-${client.clientId}`}
          className={`client-item ${selectedId === client.clientId ? "selected" : ""}`}
          onClick={() => onSelect(client.clientId)}
        >
          <span
            data-testid="connection-indicator"
            className={`indicator ${client.connected ? "connected" : "disconnected"}`}
          />
          <span className="device-model">{client.deviceModel}</span>
        </div>
      ))}
    </div>
  );
}
```

**Step 3: Run tests**

```bash
cd desktop && pnpm test -- --run src/components/ClientList.test.tsx
```

Expected: All 5 tests PASS

**Step 4: Commit**

```bash
git add desktop/src/components/ClientList.tsx desktop/src/components/ClientList.test.tsx
git commit -m "feat: add ClientList component with selection and status indicators"
```

---

## Task 11: App Layout + Settings — TDD

**Files:**
- Create: `desktop/src/components/Settings.tsx`
- Create: `desktop/src/components/Settings.test.tsx`
- Modify: `desktop/src/App.tsx`
- Create: `desktop/src/App.test.tsx`

**Step 1: Write Settings test**

```tsx
// desktop/src/components/Settings.test.tsx

import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Settings } from "./Settings";

describe("Settings", () => {
  it("shows server port", () => {
    render(<Settings port={9876} />);
    expect(screen.getByText(/9876/)).toBeInTheDocument();
  });

  it("shows server status label", () => {
    render(<Settings port={9876} />);
    expect(screen.getByText(/서버 포트/)).toBeInTheDocument();
  });
});
```

**Step 2: Write Settings component**

```tsx
// desktop/src/components/Settings.tsx

interface Props {
  port: number;
}

export function Settings({ port }: Props) {
  return (
    <div className="settings">
      <div className="setting-item">
        <span className="setting-label">서버 포트</span>
        <span className="setting-value">{port}</span>
      </div>
    </div>
  );
}
```

**Step 3: Write App test**

```tsx
// desktop/src/App.test.tsx

import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import App from "./App";

// Mock Tauri API
vi.mock("@tauri-apps/api/event", () => ({
  listen: vi.fn(() => Promise.resolve(() => {})),
}));

vi.mock("@tauri-apps/api/core", () => ({
  invoke: vi.fn(() => Promise.resolve(9876)),
}));

describe("App", () => {
  it("renders app title", () => {
    render(<App />);
    expect(screen.getByText(/PTT Dictation/)).toBeInTheDocument();
  });

  it("renders client list area", () => {
    render(<App />);
    expect(screen.getByText(/연결된 클라이언트가 없습니다/)).toBeInTheDocument();
  });

  it("renders dictation view area", () => {
    render(<App />);
    expect(screen.getByText(/클라이언트를 선택/)).toBeInTheDocument();
  });
});
```

**Step 4: Write App component**

```tsx
// desktop/src/App.tsx

import { useState } from "react";
import { ClientList } from "./components/ClientList";
import { DictationView } from "./components/DictationView";
import { Settings } from "./components/Settings";
import { useClientStates } from "./hooks/useTauriEvents";

function App() {
  const clients = useClientStates();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const selectedClient = selectedId ? clients.get(selectedId) ?? null : null;

  return (
    <div className="app">
      <header className="app-header">
        <h1>PTT Dictation</h1>
      </header>
      <div className="app-body">
        <aside className="sidebar">
          <ClientList
            clients={clients}
            selectedId={selectedId}
            onSelect={setSelectedId}
          />
          <Settings port={9876} />
        </aside>
        <main className="main-content">
          <DictationView client={selectedClient} />
        </main>
      </div>
    </div>
  );
}

export default App;
```

**Step 5: Run all frontend tests**

```bash
cd desktop && pnpm test -- --run
```

Expected: All tests PASS (messages + DictationView + ClientList + Settings + App)

**Step 6: Commit**

```bash
git add desktop/src/App.tsx desktop/src/App.test.tsx desktop/src/components/Settings.tsx desktop/src/components/Settings.test.tsx
git commit -m "feat: add App layout with Settings, ClientList, and DictationView"
```

---

## Task 12: Mock Client Test Script

**Files:**
- Create: `scripts/mock-client.ts`

**Step 1: Write a mock Android client for desktop testing**

```typescript
// scripts/mock-client.ts
// Usage: npx tsx scripts/mock-client.ts [port]
// Simulates an Android PTT client sending messages via WebSocket

import WebSocket from "ws";

const port = process.argv[2] || "9876";
const ws = new WebSocket(`ws://localhost:${port}`);
const clientId = "mock-phone-01";

ws.on("open", () => {
  console.log("Connected to server");

  // Send HELLO
  ws.send(
    JSON.stringify({
      type: "HELLO",
      clientId,
      payload: {
        deviceModel: "Mock Device",
        engine: "MockSTT",
        capabilities: ["WS"],
      },
    })
  );

  // Simulate PTT session after 1 second
  setTimeout(() => {
    const sessionId = `s-${Date.now()}`;

    ws.send(JSON.stringify({ type: "PTT_START", clientId, payload: { sessionId } }));
    console.log("PTT_START sent");

    // Send partials
    const partials = ["안녕", "안녕하세요", "안녕하세요 오늘", "안녕하세요 오늘 회의는"];
    partials.forEach((text, i) => {
      setTimeout(() => {
        ws.send(
          JSON.stringify({
            type: "PARTIAL",
            clientId,
            timestamp: Date.now(),
            payload: { sessionId, seq: i + 1, text, confidence: 0.5 + i * 0.1 },
          })
        );
        console.log(`PARTIAL: ${text}`);
      }, (i + 1) * 300);
    });

    // Send FINAL
    setTimeout(() => {
      ws.send(
        JSON.stringify({
          type: "FINAL",
          clientId,
          timestamp: Date.now(),
          payload: {
            sessionId,
            text: "안녕하세요. 오늘 회의는 오후 3시입니다.",
            confidence: 0.95,
          },
        })
      );
      console.log("FINAL sent");
    }, 2000);
  }, 1000);

  // Heartbeat every 5s
  setInterval(() => {
    ws.send(JSON.stringify({ type: "HEARTBEAT", clientId }));
  }, 5000);
});

ws.on("message", (data) => {
  console.log("Received:", data.toString());
});

ws.on("close", () => console.log("Disconnected"));
ws.on("error", (err) => console.error("Error:", err.message));
```

**Step 2: Commit**

```bash
git add scripts/mock-client.ts
git commit -m "feat: add mock client script for desktop testing"
```

---

## Task 13: Android Project Setup

**Files:**
- Create: `android/` (via Android Studio or gradle init)

**Step 1: Create Android project**

Create Android project at `android/` using Android Studio with:
- Package: `com.ptt.dictation`
- Min SDK: 26 (Android 8.0)
- Template: Empty Compose Activity
- Language: Kotlin
- Build: Kotlin DSL (build.gradle.kts)

**Step 2: Add dependencies to `android/app/build.gradle.kts`**

```kotlin
dependencies {
    // Compose (from template)
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

Also add to plugins: `kotlin("plugin.serialization")`.

**Step 3: Verify build**

```bash
cd android && ./gradlew assembleDebug
```

**Step 4: Commit**

```bash
git add android/
git commit -m "chore: scaffold Android app with Compose, OkHttp, kotlinx-serialization"
```

---

## Task 14: Android Message Model + WebSocket Manager — TDD

**Files:**
- Create: `android/app/src/main/java/com/ptt/dictation/model/Message.kt`
- Create: `android/app/src/test/java/com/ptt/dictation/model/MessageTest.kt`
- Create: `android/app/src/main/java/com/ptt/dictation/ws/WebSocketManager.kt`
- Create: `android/app/src/test/java/com/ptt/dictation/ws/WebSocketManagerTest.kt`

**Step 1: Write message test first**

```kotlin
// android/app/src/test/java/com/ptt/dictation/model/MessageTest.kt

package com.ptt.dictation.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MessageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialize HELLO message`() {
        val msg = PttMessage.hello("phone-01", "Galaxy S23", "Google")
        val str = json.encodeToString(PttMessage.serializer(), msg)
        assertTrue(str.contains(""""type":"HELLO""""))
        assertTrue(str.contains(""""clientId":"phone-01""""))
    }

    @Test
    fun `serialize PTT_START message`() {
        val msg = PttMessage.pttStart("phone-01", "s-abc")
        val str = json.encodeToString(PttMessage.serializer(), msg)
        assertTrue(str.contains(""""type":"PTT_START""""))
        assertTrue(str.contains(""""sessionId":"s-abc""""))
    }

    @Test
    fun `serialize PARTIAL message`() {
        val msg = PttMessage.partial("phone-01", "s-abc", 3, "안녕하세요", 0.7)
        val str = json.encodeToString(PttMessage.serializer(), msg)
        assertTrue(str.contains(""""type":"PARTIAL""""))
        assertTrue(str.contains(""""text":"안녕하세요""""))
        assertTrue(str.contains(""""seq":3"""))
    }

    @Test
    fun `serialize FINAL message`() {
        val msg = PttMessage.final_("phone-01", "s-abc", "최종 텍스트", 0.95)
        val str = json.encodeToString(PttMessage.serializer(), msg)
        assertTrue(str.contains(""""type":"FINAL""""))
        assertTrue(str.contains(""""text":"최종 텍스트""""))
    }

    @Test
    fun `serialize HEARTBEAT message`() {
        val msg = PttMessage.heartbeat("phone-01")
        val str = json.encodeToString(PttMessage.serializer(), msg)
        assertTrue(str.contains(""""type":"HEARTBEAT""""))
    }

    @Test
    fun `deserialize ACK message`() {
        val raw = """{"type":"ACK","clientId":"phone-01","payload":{"ackType":"HELLO"}}"""
        val msg = json.decodeFromString(PttMessage.serializer(), raw)
        assertEquals("ACK", msg.type)
        assertEquals("phone-01", msg.clientId)
    }

    @Test
    fun `roundtrip serialization`() {
        val original = PttMessage.hello("phone-01", "Pixel", "Google")
        val str = json.encodeToString(PttMessage.serializer(), original)
        val parsed = json.decodeFromString(PttMessage.serializer(), str)
        assertEquals(original.type, parsed.type)
        assertEquals(original.clientId, parsed.clientId)
    }
}
```

**Step 2: Write Message model**

```kotlin
// android/app/src/main/java/com/ptt/dictation/model/Message.kt

package com.ptt.dictation.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class PttMessage(
    val type: String,
    val clientId: String,
    val timestamp: Long? = null,
    val payload: JsonObject? = null,
) {
    companion object {
        fun hello(clientId: String, deviceModel: String, engine: String) = PttMessage(
            type = "HELLO",
            clientId = clientId,
            payload = buildJsonObject {
                put("deviceModel", deviceModel)
                put("engine", engine)
                putJsonArray("capabilities") { add("WS") }
            },
        )

        fun pttStart(clientId: String, sessionId: String) = PttMessage(
            type = "PTT_START",
            clientId = clientId,
            payload = buildJsonObject { put("sessionId", sessionId) },
        )

        fun partial(clientId: String, sessionId: String, seq: Int, text: String, confidence: Double) = PttMessage(
            type = "PARTIAL",
            clientId = clientId,
            timestamp = System.currentTimeMillis(),
            payload = buildJsonObject {
                put("sessionId", sessionId)
                put("seq", seq)
                put("text", text)
                put("confidence", confidence)
            },
        )

        fun final_(clientId: String, sessionId: String, text: String, confidence: Double) = PttMessage(
            type = "FINAL",
            clientId = clientId,
            timestamp = System.currentTimeMillis(),
            payload = buildJsonObject {
                put("sessionId", sessionId)
                put("text", text)
                put("confidence", confidence)
            },
        )

        fun heartbeat(clientId: String) = PttMessage(
            type = "HEARTBEAT",
            clientId = clientId,
        )
    }
}
```

**Step 3: Run message tests**

```bash
cd android && ./gradlew test --tests "com.ptt.dictation.model.MessageTest"
```

Expected: All 7 tests PASS

**Step 4: Write WebSocket manager interface and tests**

```kotlin
// android/app/src/main/java/com/ptt/dictation/ws/WebSocketManager.kt

package com.ptt.dictation.ws

import com.ptt.dictation.model.PttMessage
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

interface WebSocketClient {
    val connectionState: StateFlow<ConnectionState>
    fun connect(url: String)
    fun disconnect()
    fun send(message: PttMessage)
    fun setListener(listener: MessageListener)
}

interface MessageListener {
    fun onMessage(message: PttMessage)
    fun onError(error: String)
}
```

```kotlin
// android/app/src/test/java/com/ptt/dictation/ws/WebSocketManagerTest.kt

package com.ptt.dictation.ws

import com.ptt.dictation.model.PttMessage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class FakeWebSocketClient : WebSocketClient {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val sentMessages = mutableListOf<PttMessage>()
    var listener: MessageListener? = null

    override fun connect(url: String) {
        connectionState.value = ConnectionState.CONNECTED
    }

    override fun disconnect() {
        connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun send(message: PttMessage) {
        sentMessages.add(message)
    }

    override fun setListener(listener: MessageListener) {
        this.listener = listener
    }
}

class WebSocketManagerTest {

    @Test
    fun `connect changes state to CONNECTED`() {
        val client = FakeWebSocketClient()
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
        client.connect("ws://localhost:9876")
        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
    }

    @Test
    fun `disconnect changes state to DISCONNECTED`() {
        val client = FakeWebSocketClient()
        client.connect("ws://localhost:9876")
        client.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun `send records messages`() {
        val client = FakeWebSocketClient()
        client.connect("ws://localhost:9876")
        val msg = PttMessage.hello("phone-01", "Galaxy", "Google")
        client.send(msg)
        assertEquals(1, client.sentMessages.size)
        assertEquals("HELLO", client.sentMessages[0].type)
    }
}
```

**Step 5: Run WebSocket tests**

```bash
cd android && ./gradlew test --tests "com.ptt.dictation.ws.WebSocketManagerTest"
```

Expected: All 3 tests PASS

**Step 6: Commit**

```bash
git add android/app/src/main/java/com/ptt/dictation/model/ android/app/src/main/java/com/ptt/dictation/ws/ android/app/src/test/
git commit -m "feat: add Android message model and WebSocket interface with tests"
```

---

## Task 15: Android STT Manager + Throttle/Dedupe — TDD

**Files:**
- Create: `android/app/src/main/java/com/ptt/dictation/stt/STTManager.kt`
- Create: `android/app/src/main/java/com/ptt/dictation/stt/ThrottleDeduper.kt`
- Create: `android/app/src/test/java/com/ptt/dictation/stt/ThrottleDeduperTest.kt`

**Step 1: Write ThrottleDeduper test first (pure logic, no Android deps)**

```kotlin
// android/app/src/test/java/com/ptt/dictation/stt/ThrottleDeduperTest.kt

package com.ptt.dictation.stt

import org.junit.Assert.*
import org.junit.Test

class ThrottleDeduperTest {

    @Test
    fun `first text always passes`() {
        val td = ThrottleDeduper(intervalMs = 200)
        assertTrue(td.shouldEmit("hello", currentTimeMs = 0))
    }

    @Test
    fun `same text within interval is rejected`() {
        val td = ThrottleDeduper(intervalMs = 200)
        td.shouldEmit("hello", currentTimeMs = 0)
        assertFalse(td.shouldEmit("hello", currentTimeMs = 100))
    }

    @Test
    fun `same text after interval passes`() {
        val td = ThrottleDeduper(intervalMs = 200)
        td.shouldEmit("hello", currentTimeMs = 0)
        assertTrue(td.shouldEmit("hello", currentTimeMs = 250))
    }

    @Test
    fun `different text within interval passes`() {
        val td = ThrottleDeduper(intervalMs = 200)
        td.shouldEmit("hello", currentTimeMs = 0)
        assertTrue(td.shouldEmit("hello world", currentTimeMs = 50))
    }

    @Test
    fun `reset clears state`() {
        val td = ThrottleDeduper(intervalMs = 200)
        td.shouldEmit("hello", currentTimeMs = 0)
        td.reset()
        assertTrue(td.shouldEmit("hello", currentTimeMs = 50))
    }

    @Test
    fun `empty text is rejected`() {
        val td = ThrottleDeduper(intervalMs = 200)
        assertFalse(td.shouldEmit("", currentTimeMs = 0))
    }
}
```

**Step 2: Write ThrottleDeduper implementation**

```kotlin
// android/app/src/main/java/com/ptt/dictation/stt/ThrottleDeduper.kt

package com.ptt.dictation.stt

class ThrottleDeduper(private val intervalMs: Long = 200) {
    private var lastText: String? = null
    private var lastEmitTime: Long = 0

    fun shouldEmit(text: String, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (text.isEmpty()) return false
        if (text != lastText || (currentTimeMs - lastEmitTime) >= intervalMs) {
            lastText = text
            lastEmitTime = currentTimeMs
            return true
        }
        return false
    }

    fun reset() {
        lastText = null
        lastEmitTime = 0
    }
}
```

**Step 3: Write STTManager interface**

```kotlin
// android/app/src/main/java/com/ptt/dictation/stt/STTManager.kt

package com.ptt.dictation.stt

interface STTListener {
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onError(errorCode: Int, message: String)
}

interface STTEngine {
    fun startListening()
    fun stopListening()
    fun setListener(listener: STTListener)
    fun isAvailable(): Boolean
}
```

**Step 4: Run tests**

```bash
cd android && ./gradlew test --tests "com.ptt.dictation.stt.ThrottleDeduperTest"
```

Expected: All 6 tests PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/ptt/dictation/stt/ android/app/src/test/java/com/ptt/dictation/stt/
git commit -m "feat: add ThrottleDeduper and STT interfaces with tests"
```

---

## Task 16: Android Compose UI — TDD

**Files:**
- Create: `android/app/src/main/java/com/ptt/dictation/ui/PttScreen.kt`
- Create: `android/app/src/main/java/com/ptt/dictation/ui/PttViewModel.kt`
- Create: `android/app/src/test/java/com/ptt/dictation/ui/PttViewModelTest.kt`
- Create: `android/app/src/androidTest/java/com/ptt/dictation/ui/PttScreenTest.kt`

**Step 1: Write ViewModel test (pure logic)**

```kotlin
// android/app/src/test/java/com/ptt/dictation/ui/PttViewModelTest.kt

package com.ptt.dictation.ui

import com.ptt.dictation.ws.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class PttViewModelTest {

    @Test
    fun `initial state is idle`() {
        val state = PttUiState()
        assertEquals(PttMode.IDLE, state.mode)
        assertFalse(state.isPttPressed)
        assertEquals(ConnectionState.DISCONNECTED, state.connectionState)
    }

    @Test
    fun `pressing PTT changes mode to LISTENING`() {
        val state = PttUiState().copy(
            connectionState = ConnectionState.CONNECTED,
            isPttPressed = true,
            mode = PttMode.LISTENING,
        )
        assertTrue(state.isPttPressed)
        assertEquals(PttMode.LISTENING, state.mode)
    }

    @Test
    fun `can only PTT when connected`() {
        val state = PttUiState(connectionState = ConnectionState.DISCONNECTED)
        assertFalse(state.canPtt)
    }

    @Test
    fun `can PTT when connected`() {
        val state = PttUiState(connectionState = ConnectionState.CONNECTED)
        assertTrue(state.canPtt)
    }

    @Test
    fun `server address format`() {
        val state = PttUiState(serverHost = "192.168.1.10", serverPort = 9876)
        assertEquals("ws://192.168.1.10:9876", state.wsUrl)
    }
}
```

**Step 2: Write UI state model**

```kotlin
// android/app/src/main/java/com/ptt/dictation/ui/PttViewModel.kt

package com.ptt.dictation.ui

import com.ptt.dictation.ws.ConnectionState

enum class PttMode { IDLE, LISTENING }

data class PttUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isPttPressed: Boolean = false,
    val mode: PttMode = PttMode.IDLE,
    val serverHost: String = "192.168.1.1",
    val serverPort: Int = 9876,
    val partialText: String = "",
) {
    val canPtt: Boolean get() = connectionState == ConnectionState.CONNECTED
    val wsUrl: String get() = "ws://$serverHost:$serverPort"
}
```

**Step 3: Run tests**

```bash
cd android && ./gradlew test --tests "com.ptt.dictation.ui.PttViewModelTest"
```

Expected: All 5 tests PASS

**Step 4: Write Compose UI (Instrumented test requires device)**

```kotlin
// android/app/src/main/java/com/ptt/dictation/ui/PttScreen.kt

package com.ptt.dictation.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun PttScreen(
    state: PttUiState,
    onPttPress: () -> Unit,
    onPttRelease: () -> Unit,
    onServerHostChange: (String) -> Unit,
    onServerPortChange: (Int) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Connection status
        Text(
            text = when (state.connectionState) {
                com.ptt.dictation.ws.ConnectionState.CONNECTED -> "연결됨"
                com.ptt.dictation.ws.ConnectionState.CONNECTING -> "연결 중..."
                com.ptt.dictation.ws.ConnectionState.DISCONNECTED -> "연결 안 됨"
            },
            modifier = Modifier.testTag("connection-status"),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Server address input
        OutlinedTextField(
            value = state.serverHost,
            onValueChange = onServerHostChange,
            label = { Text("서버 IP") },
            modifier = Modifier.testTag("server-host-input"),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Connect/Disconnect button
        Button(
            onClick = {
                if (state.connectionState == com.ptt.dictation.ws.ConnectionState.CONNECTED) {
                    onDisconnect()
                } else {
                    onConnect()
                }
            },
            modifier = Modifier.testTag("connect-button"),
        ) {
            Text(
                if (state.connectionState == com.ptt.dictation.ws.ConnectionState.CONNECTED) "연결 해제" else "연결"
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Partial text display
        if (state.partialText.isNotEmpty()) {
            Text(
                text = state.partialText,
                modifier = Modifier.testTag("partial-text"),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // PTT Button
        Box(
            modifier = Modifier
                .size(120.dp)
                .testTag("ptt-button")
                .pointerInput(state.canPtt) {
                    if (state.canPtt) {
                        detectTapGestures(
                            onPress = {
                                onPttPress()
                                tryAwaitRelease()
                                onPttRelease()
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = when {
                    state.isPttPressed -> MaterialTheme.colorScheme.primary
                    state.canPtt -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.isPttPressed) "듣는 중..." else "PTT",
                        modifier = Modifier.testTag("ptt-label"),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
```

**Step 5: Write Compose UI test (androidTest)**

```kotlin
// android/app/src/androidTest/java/com/ptt/dictation/ui/PttScreenTest.kt

package com.ptt.dictation.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ptt.dictation.ws.ConnectionState
import org.junit.Rule
import org.junit.Test

class PttScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun render(state: PttUiState = PttUiState()) {
        composeTestRule.setContent {
            PttScreen(
                state = state,
                onPttPress = {},
                onPttRelease = {},
                onServerHostChange = {},
                onServerPortChange = {},
                onConnect = {},
                onDisconnect = {},
            )
        }
    }

    @Test
    fun showsDisconnectedStatus() {
        render()
        composeTestRule.onNodeWithTag("connection-status").assertTextEquals("연결 안 됨")
    }

    @Test
    fun showsConnectedStatus() {
        render(PttUiState(connectionState = ConnectionState.CONNECTED))
        composeTestRule.onNodeWithTag("connection-status").assertTextEquals("연결됨")
    }

    @Test
    fun showsPttButton() {
        render()
        composeTestRule.onNodeWithTag("ptt-button").assertExists()
    }

    @Test
    fun showsServerHostInput() {
        render()
        composeTestRule.onNodeWithTag("server-host-input").assertExists()
    }

    @Test
    fun showsPartialTextWhenPresent() {
        render(PttUiState(partialText = "테스트 텍스트"))
        composeTestRule.onNodeWithTag("partial-text").assertTextEquals("테스트 텍스트")
    }
}
```

**Step 6: Run unit tests (ViewModel)**

```bash
cd android && ./gradlew test --tests "com.ptt.dictation.ui.PttViewModelTest"
```

Expected: All 5 tests PASS

Compose UI tests (`PttScreenTest`) require a device/emulator:

```bash
cd android && ./gradlew connectedAndroidTest
```

**Step 7: Commit**

```bash
git add android/app/src/main/java/com/ptt/dictation/ui/ android/app/src/test/java/com/ptt/dictation/ui/ android/app/src/androidTest/
git commit -m "feat: add Android PttScreen, PttUiState, and Compose UI tests"
```

---

## Task 17: Android ForegroundService + Integration Wiring

**Files:**
- Create: `android/app/src/main/java/com/ptt/dictation/service/PttForegroundService.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/ptt/dictation/MainActivity.kt`

**Step 1: Write ForegroundService**

```kotlin
// android/app/src/main/java/com/ptt/dictation/service/PttForegroundService.kt

package com.ptt.dictation.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PttForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "ptt_dictation_channel"
        const val NOTIFICATION_ID = 1
    }

    inner class LocalBinder : Binder() {
        val service: PttForegroundService get() = this@PttForegroundService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("대기 중"))
    }

    fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PTT Dictation",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PTT Dictation")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }
}
```

**Step 2: Update AndroidManifest.xml**

Add permissions and service declaration:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.INTERNET" />

<application ...>
    <service
        android:name=".service.PttForegroundService"
        android:foregroundServiceType="microphone"
        android:exported="false" />
    ...
</application>
```

**Step 3: Verify build**

```bash
cd android && ./gradlew assembleDebug
```

**Step 4: Commit**

```bash
git add android/app/src/main/java/com/ptt/dictation/service/ android/app/src/main/AndroidManifest.xml
git commit -m "feat: add PttForegroundService with notification management"
```

---

## Task 18: Documentation

**Files:**
- Create: `desktop/README.md`
- Create: `android/README.md`
- Create: `docs/protocol-spec.md`
- Update: root `README.md` (create)

**Step 1: Write root README**

```markdown
# PTT Dictation

Android PTT(Push-to-Talk) → 데스크톱 실시간 딕테이션 시스템

## Structure

- `desktop/` — Tauri 데스크톱 앱 (Rust + React)
- `android/` — Android 클라이언트 (Kotlin + Compose)
- `docs/` — 문서
- `scripts/` — 유틸리티 스크립트

## Quick Start

### Desktop

```bash
cd desktop && pnpm install && pnpm tauri dev
```

### Android

Android Studio에서 `android/` 프로젝트를 열고 빌드

### Mock Client (Desktop 테스트용)

```bash
npx tsx scripts/mock-client.ts
```

## Development

```bash
just test     # 전체 테스트
just lint     # 전체 린트
just fmt      # 전체 포맷
```

## Code Quality

pre-commit hooks가 lefthook으로 자동 실행됩니다.
```

**Step 2: Write desktop/README.md and android/README.md**

각 서브프로젝트의 빌드/테스트/배포 방법을 문서화.

**Step 3: Write docs/protocol-spec.md**

설계 문서의 메시지 프로토콜 섹션을 독립 문서로 확장 (JSON 스키마 + 예제).

**Step 4: Commit**

```bash
git add README.md desktop/README.md android/README.md docs/protocol-spec.md
git commit -m "docs: add project README, sub-project docs, and protocol spec"
```

---

## Execution Order Summary

```
Task 1  → Monorepo setup (pnpm, justfile, lefthook)
Task 2  → Tauri scaffold + test infra
Task 3  → Protocol types (Rust) — TDD
Task 4  → Client registry (Rust) — TDD
Task 5  → Text injection (Rust) — TDD
Task 6  → WebSocket server (Rust) — TDD
Task 7  → Tauri app wiring
Task 8  → TypeScript types + hooks — TDD
Task 9  → DictationView component — TDD
Task 10 → ClientList component — TDD
Task 11 → App layout + Settings — TDD
Task 12 → Mock client script
Task 13 → Android project setup
Task 14 → Android message + WebSocket — TDD
Task 15 → Android STT + throttle — TDD
Task 16 → Android Compose UI — TDD
Task 17 → Android ForegroundService
Task 18 → Documentation
```

**Parallelizable groups:**
- Tasks 3-5 (protocol, registry, injection) — 독립적
- Tasks 8-11 (frontend components) — Task 7 이후 병렬 가능
- Tasks 13-17 (Android) — Task 2 이후 독립적으로 진행 가능
