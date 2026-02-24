# PTT Dictation

Android PTT(Push-to-Talk) → macOS 실시간 딕테이션 시스템

## Architecture

- **Android** (Kotlin + Compose) — PTT 버튼 + 음성 인식 (SpeechRecognizer) + BLE Central
- **macOS** (Swift + AppKit) — BLE Peripheral + 메뉴바 앱 + 텍스트 주입 (Clipboard + Cmd+V)
- **연결**: BLE (Bluetooth Low Energy) — 별도 네트워크 설정 불필요

## Structure

- `macos/` — macOS 네이티브 앱 (Swift + SwiftPM)
- `android/` — Android 클라이언트 (Kotlin + Compose)
- `docs/` — 문서 (프로토콜 스펙 등)

## Quick Start

### macOS

```bash
cd macos && swift build && swift run
```

### Android

```bash
cd android && ./gradlew installDebug
```

## Connection

1. macOS 앱 실행 → 메뉴바에 아이콘 표시, BLE advertising 시작
2. Android 앱 실행 → BLE 스캔으로 macOS 기기 자동 발견
3. 연결 완료 후 PTT 버튼을 눌러 딕테이션 시작

## Development

```bash
just test     # 전체 테스트
just lint     # 전체 린트
just fmt      # 전체 포맷
```

## Code Quality

pre-commit hooks가 lefthook으로 자동 실행됩니다.
