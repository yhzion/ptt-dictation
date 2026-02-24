# Protocol Specification

PTT Dictation 메시지 프로토콜 명세.

## Overview

Phone(Android)과 Desktop(macOS) 간 JSON 메시지를 전송합니다. 모든 메시지는 `type`과 `clientId` 필드를 포함합니다.

현재 전송 계층은 **BLE (Bluetooth Low Energy)** 입니다. 이전 WebSocket 기반 전송은 "Legacy: WebSocket Transport" 섹션을 참조하세요.

## BLE Transport

### GATT Service & Characteristics

| Name | UUID | Direction | Description |
|------|------|-----------|-------------|
| PTT Dictation Service | `A1B2C3D4-E5F6-7890-ABCD-EF1234567890` | — | Primary GATT Service |
| Control | `...7891` | Phone → Desktop (Write) | PTT_START 메시지 전송 |
| Partial Text | `...7892` | Phone → Desktop (Write Without Response) | PARTIAL 메시지 전송 |
| Final Text | `...7893` | Phone → Desktop (Write) | FINAL 메시지 전송 |
| Device Info | `...7894` | Phone → Desktop (Write) | HELLO 메시지 전송 |
| Status | `...7895` | Desktop → Phone (Notify) | 연결 상태 알림 |

> UUID prefix: `A1B2C3D4-E5F6-7890-ABCD-EF12345678xx`

### WebSocket → BLE Mapping

| WebSocket Message | BLE Characteristic | Write Type | Notes |
|-------------------|--------------------|------------|-------|
| `HELLO` | Device Info (`...7894`) | Write | 연결 시 디바이스 정보 전달 |
| `PTT_START` | Control (`...7891`) | Write | 딕테이션 세션 시작 |
| `PARTIAL` | Partial Text (`...7892`) | Write Without Response | 실시간 중간 결과 (낮은 지연) |
| `FINAL` | Final Text (`...7893`) | Write | 최종 인식 결과 |
| `HEARTBEAT` | _(제거됨)_ | — | BLE가 연결 상태를 자동 관리 |
| `ACK` | _(BLE write response)_ | — | Write 응답으로 자동 확인 |

### Notes

- 메시지 페이로드는 기존과 동일한 **JSON 인코딩**을 사용합니다. 전송 계층만 WebSocket에서 BLE로 변경되었습니다.
- `HEARTBEAT`는 불필요합니다. BLE 프로토콜이 연결 상태를 자동으로 감지하며, `CBPeripheralManagerDelegate`/`BluetoothGattCallback`이 연결 해제를 통지합니다.
- `ACK`는 BLE Write 응답(`.withResponse`)으로 대체됩니다. 별도 메시지가 필요 없습니다.
- `PARTIAL`은 `Write Without Response`를 사용하여 지연을 최소화합니다.

---

## Legacy: WebSocket Transport

> 아래 내용은 이전 WebSocket 기반 아키텍처에 대한 참조용 문서입니다.

### Message Types

| Direction | Type | Purpose |
|-----------|------|---------|
| Phone → Desktop | `HELLO` | 연결 시 디바이스 정보 전달 |
| Phone → Desktop | `PTT_START` | 음성 인식 시작 알림 |
| Phone → Desktop | `PARTIAL` | 부분 인식 텍스트 (실시간) |
| Phone → Desktop | `FINAL` | 최종 인식 텍스트 |
| Phone → Desktop | `HEARTBEAT` | 연결 유지 (5초 주기) |
| Desktop → Phone | `ACK` | HELLO/FINAL 수신 확인 |

### Message Schemas

#### HELLO

클라이언트가 연결 직후 보내는 첫 메시지. 디바이스 정보를 포함합니다.

```json
{
  "type": "HELLO",
  "clientId": "phone-01",
  "payload": {
    "deviceModel": "Galaxy S23",
    "engine": "Google",
    "capabilities": ["WS"]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | `string` | `"HELLO"` |
| `clientId` | `string` | 클라이언트 고유 ID |
| `payload.deviceModel` | `string` | 디바이스 모델명 |
| `payload.engine` | `string` | STT 엔진 이름 |
| `payload.capabilities` | `string[]` | 지원 기능 목록 |

#### PTT_START

PTT 버튼을 누르면 전송. 새 딕테이션 세션의 시작을 알립니다.

```json
{
  "type": "PTT_START",
  "clientId": "phone-01",
  "payload": {
    "sessionId": "s-abc123"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | `string` | `"PTT_START"` |
| `clientId` | `string` | 클라이언트 고유 ID |
| `payload.sessionId` | `string` | 세션 고유 ID |

#### PARTIAL

음성 인식 중간 결과. 실시간으로 반복 전송됩니다.

```json
{
  "type": "PARTIAL",
  "clientId": "phone-01",
  "timestamp": 1670000000000,
  "payload": {
    "sessionId": "s-abc123",
    "seq": 12,
    "text": "안녕하세요 오늘",
    "confidence": 0.60
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | `string` | `"PARTIAL"` |
| `clientId` | `string` | 클라이언트 고유 ID |
| `timestamp` | `number` | Unix 밀리초 타임스탬프 |
| `payload.sessionId` | `string` | 세션 고유 ID |
| `payload.seq` | `number` | 시퀀스 번호 (1부터 증가) |
| `payload.text` | `string` | 중간 인식 텍스트 |
| `payload.confidence` | `number` | 신뢰도 (0.0 ~ 1.0) |

#### FINAL

음성 인식 최종 결과. PTT 버튼을 놓으면 전송됩니다.

```json
{
  "type": "FINAL",
  "clientId": "phone-01",
  "timestamp": 1670000000000,
  "payload": {
    "sessionId": "s-abc123",
    "text": "안녕하세요. 오늘 회의는 오후 3시입니다.",
    "confidence": 0.93
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | `string` | `"FINAL"` |
| `clientId` | `string` | 클라이언트 고유 ID |
| `timestamp` | `number` | Unix 밀리초 타임스탬프 |
| `payload.sessionId` | `string` | 세션 고유 ID |
| `payload.text` | `string` | 최종 인식 텍스트 |
| `payload.confidence` | `number` | 신뢰도 (0.0 ~ 1.0) |

#### HEARTBEAT

연결 유지용 메시지. 5초 주기로 전송합니다.

```json
{
  "type": "HEARTBEAT",
  "clientId": "phone-01"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | `string` | `"HEARTBEAT"` |
| `clientId` | `string` | 클라이언트 고유 ID |

#### ACK

Desktop이 HELLO 또는 FINAL 수신 시 응답으로 보내는 확인 메시지.

```json
{
  "type": "ACK",
  "clientId": "phone-01",
  "payload": {
    "ackType": "HELLO"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | `string` | `"ACK"` |
| `clientId` | `string` | 클라이언트 고유 ID |
| `payload.ackType` | `string` | ACK 대상 메시지 타입 (`"HELLO"` 또는 `"FINAL"`) |

### Partial Strategy

PARTIAL 메시지는 빈번하게 발생할 수 있으므로 클라이언트 측에서 스로틀링합니다:

- **200ms 스로틀**: 마지막 전송 후 200ms 이내의 동일 텍스트는 무시
- **중복 제거**: 이전과 동일한 텍스트는 전송하지 않음
- 구현: `ThrottleDeduper` (Android 클라이언트)

### ACK Flow

```
Phone                           Desktop
  |                                |
  |------- HELLO ---------------→ |
  |←------ ACK {ackType:"HELLO"}- |
  |                                |
  |------- PTT_START -----------→ |  (ACK 없음)
  |------- PARTIAL (seq:1) -----→ |  (ACK 없음)
  |------- PARTIAL (seq:2) -----→ |  (ACK 없음)
  |------- FINAL ---------------→ |
  |←------ ACK {ackType:"FINAL"}- |
```

- `HELLO` → ACK 응답 (클라이언트 등록 확인)
- `PTT_START` → ACK 없음
- `PARTIAL` → ACK 없음 (실시간 스트리밍)
- `FINAL` → ACK 응답 (텍스트 주입 확인)
- `HEARTBEAT` → ACK 없음

### Heartbeat Behavior

- **간격**: 5초마다 클라이언트가 `HEARTBEAT` 메시지 전송
- **타임아웃**: 서버는 클라이언트별로 마지막 heartbeat 시각을 추적
- **연결 해제 판정**: 3회 연속 미수신 (15초) 시 클라이언트를 타임아웃으로 간주
- 서버의 `ClientRegistry`가 `heartbeat_timeout` (기본 15초)으로 타임아웃 클라이언트를 감지
