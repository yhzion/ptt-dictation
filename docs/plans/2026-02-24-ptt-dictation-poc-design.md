# PTT Dictation PoC Design

**Date**: 2026-02-24
**Status**: Approved

## Overview

Android PTT(Push-to-Talk) 클라이언트에서 폰 내 STT로 음성을 텍스트로 변환하고,
WebSocket을 통해 Tauri 데스크톱 앱에 실시간 전달하여 macOS에서 텍스트를 삽입하는 시스템.

## PoC Scope

- 통신: WebSocket만 (BLE/RFCOMM은 이후 단계)
- STT: Android 내장 SpeechRecognizer (오프라인 우선)
- OS 삽입: macOS 클립보드+붙여넣기 방식
- 인증: clientId 기반 식별 (토큰/암호화 이후)
- 교정(Correction): 제외 (이후 단계)

## Architecture

```
┌─────────────────────┐       WebSocket (JSON)       ┌──────────────────────┐
│   Android (Client)  │ ──────────────────────────▶  │   Tauri (Desktop)    │
│                     │                              │                      │
│  PTT Button         │  HELLO, PTT_START,           │  Rust Backend        │
│  SpeechRecognizer   │  PARTIAL, FINAL,             │   ├─ WS Server       │
│  WebSocket Client   │  HEARTBEAT                   │   ├─ ProtocolHandler │
│                     │ ◀──────────────────────────  │   └─ InjectionMgr   │
│                     │  ACK                         │                      │
│  Jetpack Compose UI │                              │  React Frontend      │
│  ForegroundService  │                              │   ├─ Client List     │
└─────────────────────┘                              │   ├─ Partial/Final   │
                                                     │   └─ Settings        │
                                                     └──────────────────────┘
```

## Message Protocol (6 types)

| Direction | Type | Purpose |
|-----------|------|---------|
| Phone → Desktop | `HELLO` | 연결 시 디바이스 정보 전달 |
| Phone → Desktop | `PTT_START` | 음성 인식 시작 알림 |
| Phone → Desktop | `PARTIAL` | 부분 인식 텍스트 (실시간) |
| Phone → Desktop | `FINAL` | 최종 인식 텍스트 |
| Phone → Desktop | `HEARTBEAT` | 연결 유지 (5초 주기) |
| Desktop → Phone | `ACK` | HELLO/FINAL 수신 확인 |

### Partial Strategy
- Throttle: 200ms interval
- Dedupe: skip if text unchanged

## Android App Structure

```
app/src/main/java/com/ptt/dictation/
├── MainActivity.kt          # Compose UI (PTT button, connection status)
├── PttForegroundService.kt  # ForegroundService
├── stt/
│   └── STTManager.kt        # SpeechRecognizer wrapper
├── ws/
│   └── WebSocketManager.kt  # OkHttp WebSocket client
└── model/
    └── Message.kt           # Message data classes
```

**Libraries**: OkHttp, kotlinx.serialization, Jetpack Compose

**Flow**:
1. App start → ForegroundService → WebSocket connect → HELLO
2. PTT press → PTT_START → startListening()
3. onPartialResults → throttle+dedupe → PARTIAL
4. PTT release → stopListening() → onResults → FINAL
5. Background: HEARTBEAT every 5s

## Tauri Desktop Structure

**Rust backend** (`src-tauri/src/`):
```
├── main.rs              # Entry point, command registration
├── ws_server.rs         # tokio + tungstenite WebSocket server
├── protocol.rs          # Message parsing/serialization
├── client_registry.rs   # Connected client state management
└── injection.rs         # macOS clipboard+paste text injection
```

**React frontend** (`src/`):
```
├── App.tsx
├── components/
│   ├── ClientList.tsx    # Connected clients + status
│   ├── DictationView.tsx # Real-time partial/final text
│   └── Settings.tsx      # Server port, injection mode
├── hooks/
│   └── useTauriEvents.ts # Tauri IPC event subscription
└── types/
    └── messages.ts       # Message type definitions
```

**Crates**: tokio, tokio-tungstenite, serde/serde_json, cocoa, core-graphics

**Flow**:
1. App start → WS server on port 9876
2. HELLO → register client → emit to frontend
3. PARTIAL → emit to frontend → DictationView
4. FINAL → clipboard+paste injection → ACK
5. HEARTBEAT miss x3 → disconnected

## macOS Text Injection (injection.rs)

1. Backup current clipboard (NSPasteboard)
2. Set FINAL text to clipboard
3. Simulate Cmd+V via CGEvent
4. Restore clipboard after 100ms

## Design Principles

- **TDD (Test-Driven Development)**: 모든 모듈은 테스트 먼저 작성 → 최소 구현 → 리팩토링 순서로 개발
  - Red: 실패하는 테스트 작성
  - Green: 테스트를 통과하는 최소 코드 작성
  - Refactor: 중복 제거, 구조 개선
- **모듈화**: 모든 기능은 독립적으로 측정·테스트 가능한 단위로 분리
- **의존성 주입**: 각 모듈은 인터페이스/trait를 통해 의존성을 주입받아 mock 교체 가능
  - Android: `STTManager`, `WebSocketManager` → 인터페이스 기반, 테스트 시 fake 구현
  - Rust: `protocol`, `client_registry`, `injection` → trait 기반, 단위 테스트 가능
- **순수 로직 분리**: 프로토콜 파싱, throttle/dedupe, 클라이언트 상태 관리 등은 I/O 없는 순수 함수/구조체로 작성
- **UI 테스트 가능성**:
  - Android Compose: `@Composable` 함수는 상태를 외부에서 주입, Compose Testing(`composeTestRule`) 사용
  - React: 컴포넌트는 props/hooks로 상태 분리, React Testing Library + Vitest로 렌더링·인터랙션 검증
  - UI 로직(상태 변환, 이벤트 핸들링)은 커스텀 훅/ViewModel로 분리하여 독립 테스트
- **테스트 전략**:
  - Unit: 메시지 직렬화/역직렬화, throttle/dedupe 로직, 클라이언트 상태 전이, UI 상태 로직
  - Component: Android Compose UI 테스트, React 컴포넌트 렌더링 테스트
  - Integration: WebSocket 연결 → 메시지 라운드트립 (mock STT)
  - E2E: 실제 디바이스 PTT → Desktop 텍스트 표시 (수동)

## Out of PoC Scope

- BLE GATT / RFCOMM communication
- Authentication (token/challenge-response)
- Correction system (CorrectionStore)
- TLS/WSS encryption
- Multi-client management (single client for PoC)
- Windows/Linux support
