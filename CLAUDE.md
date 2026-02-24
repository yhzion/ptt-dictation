# PTT Dictation - Development Guidelines

## Development Workflow (Mandatory)

모든 개발은 다음 순서를 따른다:

1. **리서치** — 기존 코드·문서·의존성 파악
2. **계획** — 변경 범위, 영향 분석, 접근 방식 결정
3. **테스트 케이스 작성** — 실패하는 테스트 먼저 작성 (Red)
4. **구현** — 테스트를 통과하는 최소 코드 작성 (Green)
5. **검증** — 모든 테스트 통과 확인 + 회귀 테스트 Green 상태 확인

## TDD Rules

- Red → Green → Refactor 사이클 엄수
- 테스트 케이스는 큰 단위(통합)에서 시작하여 작은 단위(유닛)로 리팩토링
- 모든 세부 기능은 테스트 가능해야 함 — 예외 없음
- UI/UX도 테스트 대상임 — 디자인은 기능이다
- 기능 수정 시 반드시 회귀 테스트가 Green 상태인지 확인 후 진행

## Code Quality Hooks

코드 작성 중 다음 항목이 hook으로 자동 검증되어야 함:

- 사용되지 않는 코드 (dead code)
- 중복 코드
- 타입 오류
- 린트 오류
- 포맷 오류
- 모든 경고 (warnings)

### Per-Stack Quality Commands

- **Rust**: `cargo clippy -- -D warnings`, `cargo fmt --check`, `cargo test`
- **TypeScript/React**: `tsc --noEmit`, `eslint .`, `prettier --check .`, `vitest run`
- **Android/Kotlin**: `ktlint`, `detekt`, `./gradlew test`

## Project Structure

Monorepo: 서버(Tauri/Rust+React)와 클라이언트(Android/Kotlin)는 별도 빌드 시스템을 사용하므로 monorepo 도구로 통합 관리.

## Documentation Strategy

- 서버/클라이언트의 빌드·배포 방법이 다르므로 각각 독립적인 README 유지
- 공유 프로토콜 스펙은 `docs/protocol-spec.md`에 단일 소스로 관리
- 아키텍처 변경 시 `docs/` 문서 동기화 필수

### 문서 위치

- `docs/` — 공통 문서 (프로토콜, 아키텍처 등)
- `<서버앱>/docs/` — 서버(Tauri) 관련 문서
- `<클라이언트앱>/docs/` — 클라이언트(Android) 관련 문서

### 작업별 문서 세트

모든 research와 plan은 문서화되어야 한다. 작업 단위로 디렉토리를 생성하여 관리:

```
docs/<작업명>/
├── RESEARCH.md    # 리서치 내용, 조사 결과
├── PROGRESS.md    # 진행 상황 추적
├── PLAN.md        # 구현 계획
├── SPEC.md        # 스펙/요구사항 정의
└── CHECKLIST.md   # 체크리스트
```

## Pre-Task Skill/Agent Search (Mandatory)

모든 작업을 시작하기 전에 반드시:
1. 해당 작업에 적합한 **스킬(skill)**이 있는지 검색
2. 해당 작업에 적합한 **에이전트(agent)**가 있는지 검색
3. 적합한 스킬/에이전트가 발견되면 해당 도구를 사용하여 작업 수행
4. 없을 경우에만 직접 수행
