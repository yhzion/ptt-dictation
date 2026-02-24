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
