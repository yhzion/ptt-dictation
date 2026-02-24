# Production-Ready PTT Dictation Design

## Context

MVP PoC 완료 (2026-02-24). Tauri(Rust+React) 데스크톱 + Kotlin/Compose Android 앱이 WebSocket으로 통신하는 구조.
Production-ready 전환을 위해 사용성 중심으로 아키텍처를 재설계한다.

## Key Decisions

| 항목 | MVP (현재) | Production |
|------|-----------|------------|
| 데스크톱 프레임워크 | Tauri v2 (Rust + React) | Swift / AppKit 네이티브 |
| 데스크톱 UI | 윈도우 앱 | 메뉴바 앱 (NSStatusItem) |
| 통신 | WebSocket (WiFi, adb reverse) | Bluetooth Low Energy |
| 페어링 | 수동 IP/포트 입력 | OS 블루투스 페어링 (자동 재접속) |
| Android | Kotlin/Compose | 유지 (통신 레이어만 교체) |
| 텍스트 주입 | clipboard + Cmd+V (enigo) | clipboard + Cmd+V (CGEvent) |

## Architecture

```
┌─────────────────┐         BLE          ┌─────────────────────┐
│  Android Phone   │ ◄──────────────────► │  macOS Menu Bar App  │
│  (Kotlin/Compose)│    GATT Service      │  (Swift/AppKit)      │
│                  │                      │                      │
│  STT Engine      │  partial/final text  │  Text Injector       │
│  BLE Central     │  ────────────────►   │  BLE Peripheral      │
│  UI (기존 유지)   │                      │  NSStatusItem        │
└─────────────────┘                      └─────────────────────┘
```

### macOS App (New — Swift/AppKit)

- **NSStatusItem**: 메뉴바 아이콘. 연결 상태를 아이콘으로 표시.
- **NSMenu**: 드롭다운 — 연결된 기기, 최근 딕테이션, 연결 해제.
- **Settings Panel (NSWindow)**: 텍스트 주입 설정, 페어링 관리. 필요할 때만 열림.
- **CoreBluetooth Peripheral**: GATT 서비스로 딕테이션 데이터 수신.
- **Text Injection**: NSPasteboard + CGEvent(Cmd+V). 기존 방식 유지.
- **디자인 원칙**: 순수 macOS 네이티브 컴포넌트만 사용. 외부 디자인 요소 없음.

### Android App (Modified — Kotlin/Compose)

- 기존 앱 유지. 통신 레이어만 교체.
- `WebSocketClient` 인터페이스 → `BleClient` 구현체로 대체.
- STT (SpeechRecognizer, ThrottleDeduper) 그대로.
- UI (PttScreen, PttViewModel) 그대로.

### BLE Communication

**GATT Service 구조:**
- Service UUID: PTT Dictation 전용 커스텀 UUID
- Characteristics:
  - Control (Write): PTT start/stop
  - Partial Text (Notify): 실시간 부분 인식 결과
  - Final Text (Notify + Write): 최종 인식 결과
  - Device Info (Read): 기기 정보 (HELLO 대체)

**프로토콜 매핑:**
- HELLO → Device Info characteristic read + initial connection
- PTT_START → Control characteristic write
- PARTIAL → Partial Text characteristic notify
- FINAL → Final Text characteristic notify
- HEARTBEAT → BLE connection state monitoring (OS 레벨)
- ACK → BLE write response (자동)

## What Gets Removed

- `desktop/` 폴더 전체 (Tauri + React + Rust 백엔드)
- WebSocket 기반 통신 (OkHttp client, tokio-tungstenite server)
- adb reverse USB 터널링

## What Gets Preserved

- Android STT 엔진 (SpeechRecognizer, ThrottleDeduper)
- Android UI (PttScreen, PttViewModel)
- Android 테스트 (Message, ThrottleDeduper, ViewModel)
- 프로토콜 메시지 구조 (전송 방식만 BLE로 변경)
- `docs/protocol-spec.md` (BLE 매핑 섹션 추가)

## Development Phases

### Phase 1: BLE Communication Layer
양쪽(macOS + Android) BLE 구현. 기존 프로토콜을 BLE GATT로 매핑.
텍스트 송수신이 동작하는 것이 Phase 1의 성공 기준.

### Phase 2: macOS Native Menu Bar App
Swift/AppKit 메뉴바 앱. CoreBluetooth peripheral + 텍스트 주입.
Amphetamine 스타일 UX. 순수 macOS 네이티브 컴포넌트.

### Phase 3: Auto-Connect UX
BLE 페어링 후 자동 재접속. 연결 상태 실시간 표시.
사용자가 아무것도 하지 않아도 폰을 들고 PTT 누르면 바로 동작.
