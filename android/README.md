# Android — PTT Dictation Client

Android client built with Kotlin + Jetpack Compose.
Press the PTT (Push-to-Talk) button to recognize speech and send text to the desktop in real-time via BLE.

## Architecture

```
app/src/main/java/com/ptt/dictation/
  MainActivity.kt              Activity entry point
  model/
    PttMessage.kt              JSON message model (6 message types, kotlinx.serialization)
  ble/
    BleCentralClient.kt        BLE Central client (scan, connect, GATT write)
    BLEConstants.kt            Service/characteristic UUIDs
    BleMessageEncoder.kt       Message → JSON encoding
    PttTransport.kt            Transport interface
  stt/
    SpeechRecognizerSTTEngine.kt  Speech recognition engine (SpeechRecognizer API)
    ThrottleDeduper.kt         PARTIAL message 200ms throttle + dedup
  ui/
    PttScreen.kt               Compose UI (OLED-optimized, PTT button, connection status)
    PttViewModel.kt            UI state management (PttUiState)
```

## Build

Open project in Android Studio, or:

```bash
./gradlew assembleDebug
```

## Install

```bash
./gradlew installDebug
```

## Test

```bash
# Unit tests
./gradlew test

# UI tests (requires emulator/device)
./gradlew connectedAndroidTest
```

## Lint

```bash
./gradlew ktlintCheck
```

## Key Dependencies

- **Jetpack Compose** (BOM 2024.09) — UI
- **kotlinx-serialization** — JSON serialization
- **Android SDK 34** (minSdk 26)

## Usage

1. Run the app and tap "Scan" to discover the macOS BLE peripheral
2. After connection, hold the PTT button to start speech recognition
3. Release to send the final text to the desktop
