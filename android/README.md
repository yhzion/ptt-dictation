# Android — PTT Dictation Client

Kotlin + Jetpack Compose로 구성된 Android 클라이언트.
PTT(Push-to-Talk) 버튼을 누르면 음성을 인식하고, WebSocket으로 데스크톱에 실시간 전송합니다.

## Architecture

```
app/src/main/java/com/ptt/dictation/
  MainActivity.kt              액티비티 진입점
  model/
    PttMessage.kt              JSON 메시지 모델 (6 message types, kotlinx.serialization)
  ws/
    WebSocketManager.kt        WebSocket 인터페이스 (OkHttp 기반)
  stt/
    STTManager.kt              음성 인식 엔진 인터페이스
    ThrottleDeduper.kt         PARTIAL 메시지 200ms 스로틀 + 중복 제거
  ui/
    PttScreen.kt               Compose UI (연결 상태, 서버 IP 입력, PTT 버튼)
    PttViewModel.kt            UI 상태 관리 (PttUiState)
  service/
    PttForegroundService.kt    포그라운드 서비스 (백그라운드 녹음 유지)
```

## Build

Android Studio에서 프로젝트를 열거나:

```bash
./gradlew assembleDebug
```

## Install

```bash
./gradlew installDebug
```

## Test

```bash
# 유닛 테스트
./gradlew test

# UI 테스트 (에뮬레이터/디바이스 필요)
./gradlew connectedAndroidTest
```

## Lint

```bash
./gradlew ktlintCheck
```

## Key Dependencies

- **Jetpack Compose** (BOM 2024.09) — UI
- **OkHttp 4** — WebSocket 클라이언트
- **kotlinx-serialization** — JSON 직렬화
- **Android SDK 34** (minSdk 26)

## 사용법

1. 앱을 실행하고 데스크톱 서버 IP를 입력합니다
2. "연결" 버튼을 눌러 WebSocket 연결
3. PTT 버튼을 길게 눌러 음성 인식 시작
4. 손을 떼면 최종 텍스트가 데스크톱으로 전송됩니다
