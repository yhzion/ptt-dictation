# PTT Dictation

Android PTT (Push-to-Talk) → macOS real-time dictation system

## Architecture

- **Android** (Kotlin + Compose) — PTT button + Speech recognition (SpeechRecognizer) + BLE Central
- **macOS** (Swift + AppKit) — BLE Peripheral + Menu bar app + Text injection (Clipboard + Cmd+V)
- **Connection**: BLE (Bluetooth Low Energy) — No separate network configuration needed

## Structure

- `macos/` — macOS native app (Swift + SwiftPM)
- `android/` — Android client (Kotlin + Compose)
- `docs/` — Documentation (protocol spec, etc.)

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

1. Run macOS app → Menu bar icon appears, BLE advertising starts
2. Run Android app → Auto-discovers macOS device via BLE scan
3. After connection, press PTT button to start dictation

## Development

```bash
just test     # Run all tests
just lint     # Run all linters
just fmt      # Format all code
```

## Code Quality

Pre-commit hooks run automatically via lefthook.
