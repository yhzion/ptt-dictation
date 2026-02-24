# Desktop — Tauri v2 App

Rust 백엔드 + React/TypeScript 프론트엔드로 구성된 데스크톱 앱.
Android 클라이언트로부터 WebSocket으로 음성 인식 텍스트를 수신하여 현재 포커스된 앱에 텍스트를 주입합니다.

## Architecture

```
src-tauri/src/          (Rust backend)
  lib.rs               Tauri 앱 진입점, WS 서버 시작, 이벤트 브릿지
  protocol.rs          JSON 메시지 파싱/직렬화 (6 message types)
  client_registry.rs   연결된 클라이언트 관리, heartbeat timeout 추적
  ws_server.rs         tokio-tungstenite WebSocket 서버, 메시지 디스패치
  injection.rs         텍스트 주입 (클립보드 → Cmd+V, arboard + enigo)
  main.rs              바이너리 진입점

src/                    (React frontend)
  App.tsx              메인 레이아웃 (ClientList + DictationView + Settings)
  types/messages.ts    TypeScript 메시지 타입 정의
  hooks/useTauriEvents.ts  Tauri 이벤트 → React 상태 브릿지
  components/
    DictationView.tsx  실시간 딕테이션 텍스트 표시
    ClientList.tsx     연결된 클라이언트 목록
    Settings.tsx       서버 포트 등 설정 표시
```

## Build

```bash
pnpm install
pnpm tauri build
```

## Run (개발 모드)

```bash
pnpm tauri dev
```

WebSocket 서버가 포트 9876에서 자동으로 시작됩니다.

## Test

```bash
# Rust 테스트
cargo test --manifest-path src-tauri/Cargo.toml

# React 테스트
pnpm test        # watch 모드
pnpm test:run    # 단일 실행
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

- **Tauri v2** — 데스크톱 앱 프레임워크
- **tokio-tungstenite** — async WebSocket 서버
- **arboard** — 크로스 플랫폼 클립보드
- **enigo** — OS 키 입력 시뮬레이션
- **React 19** + **Vite** — 프론트엔드
- **Vitest** + **Testing Library** — 프론트엔드 테스트
