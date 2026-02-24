# Protocol Specification

PTT Dictation message protocol specification.

## Overview

JSON messages are transmitted between Phone (Android) and Desktop (macOS). All messages include `type` and `clientId` fields.

The current transport layer is **BLE (Bluetooth Low Energy)**. For the previous WebSocket-based transport, see the "Legacy: WebSocket Transport" section.

## BLE Transport

### GATT Service & Characteristics

| Name | UUID | Direction | Description |
|------|------|-----------|-------------|
| PTT Dictation Service | `A1B2C3D4-E5F6-7890-ABCD-EF1234567890` | — | Primary GATT Service |
| Control | `...7891` | Phone → Desktop (Write) | PTT_START message |
| Partial Text | `...7892` | Phone → Desktop (Write Without Response) | PARTIAL message |
| Final Text | `...7893` | Phone → Desktop (Write) | FINAL message |
| Device Info | `...7894` | Phone → Desktop (Write) | HELLO message |
| Status | `...7895` | Desktop → Phone (Notify) | Connection status notification |

> UUID prefix: `A1B2C3D4-E5F6-7890-ABCD-EF12345678xx`

### WebSocket → BLE Mapping

| WebSocket Message | BLE Characteristic | Write Type | Notes |
|-------------------|--------------------|------------|-------|
| `HELLO` | Device Info (`...7894`) | Write | Device info on connection |
| `PTT_START` | Control (`...7891`) | Write | Start dictation session |
| `PARTIAL` | Partial Text (`...7892`) | Write Without Response | Real-time partial results (low latency) |
| `FINAL` | Final Text (`...7893`) | Write | Final recognition result |
| `HEARTBEAT` | _(removed)_ | — | BLE manages connection state automatically |
| `ACK` | _(BLE write response)_ | — | Automatic confirmation via write response |

### Notes

- Message payloads use the same **JSON encoding** as before. Only the transport layer changed from WebSocket to BLE.
- `HEARTBEAT` is unnecessary. The BLE protocol automatically detects connection state, and `CBPeripheralManagerDelegate`/`BluetoothGattCallback` notify on disconnection.
- `ACK` is replaced by BLE Write response (`.withResponse`). No separate message needed.
- `PARTIAL` uses `Write Without Response` to minimize latency.

---

## Legacy: WebSocket Transport

> The content below is reference documentation for the previous WebSocket-based architecture.

### Message Types

| Direction | Type | Purpose |
|-----------|------|---------|
| Phone → Desktop | `HELLO` | Device info on connection |
| Phone → Desktop | `PTT_START` | Speech recognition start notification |
| Phone → Desktop | `PARTIAL` | Partial recognition text (real-time) |
| Phone → Desktop | `FINAL` | Final recognition text |
| Phone → Desktop | `HEARTBEAT` | Keep-alive (every 5 seconds) |
| Desktop → Phone | `ACK` | Acknowledgement for HELLO/FINAL |

### Message Schemas

#### HELLO

First message sent by the client right after connection. Contains device information.

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
| `clientId` | `string` | Unique client ID |
| `payload.deviceModel` | `string` | Device model name |
| `payload.engine` | `string` | STT engine name |
| `payload.capabilities` | `string[]` | Supported capabilities |

#### PTT_START

Sent when PTT button is pressed. Signals the start of a new dictation session.

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
| `clientId` | `string` | Unique client ID |
| `payload.sessionId` | `string` | Unique session ID |

#### PARTIAL

Intermediate speech recognition result. Sent repeatedly in real-time.

```json
{
  "type": "PARTIAL",
  "clientId": "phone-01",
  "timestamp": 1670000000000,
  "payload": {
    "sessionId": "s-abc123",
    "seq": 12,
    "text": "hello today",
    "confidence": 0.60
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | `string` | `"PARTIAL"` |
| `clientId` | `string` | Unique client ID |
| `timestamp` | `number` | Unix millisecond timestamp |
| `payload.sessionId` | `string` | Unique session ID |
| `payload.seq` | `number` | Sequence number (starts from 1) |
| `payload.text` | `string` | Partial recognition text |
| `payload.confidence` | `number` | Confidence score (0.0 ~ 1.0) |

#### FINAL

Final speech recognition result. Sent when PTT button is released.

```json
{
  "type": "FINAL",
  "clientId": "phone-01",
  "timestamp": 1670000000000,
  "payload": {
    "sessionId": "s-abc123",
    "text": "Hello. Today's meeting is at 3 PM.",
    "confidence": 0.93
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | `string` | `"FINAL"` |
| `clientId` | `string` | Unique client ID |
| `timestamp` | `number` | Unix millisecond timestamp |
| `payload.sessionId` | `string` | Unique session ID |
| `payload.text` | `string` | Final recognition text |
| `payload.confidence` | `number` | Confidence score (0.0 ~ 1.0) |

#### HEARTBEAT

Keep-alive message. Sent every 5 seconds.

```json
{
  "type": "HEARTBEAT",
  "clientId": "phone-01"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | `string` | `"HEARTBEAT"` |
| `clientId` | `string` | Unique client ID |

#### ACK

Confirmation message sent by Desktop upon receiving HELLO or FINAL.

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
| `clientId` | `string` | Unique client ID |
| `payload.ackType` | `string` | ACK target message type (`"HELLO"` or `"FINAL"`) |

### Partial Strategy

PARTIAL messages can occur frequently, so the client applies throttling:

- **200ms throttle**: Identical text within 200ms of last send is ignored
- **Deduplication**: Text identical to previous send is not transmitted
- Implementation: `ThrottleDeduper` (Android client)

### ACK Flow

```
Phone                           Desktop
  |                                |
  |------- HELLO ---------------→ |
  |←------ ACK {ackType:"HELLO"}- |
  |                                |
  |------- PTT_START -----------→ |  (No ACK)
  |------- PARTIAL (seq:1) -----→ |  (No ACK)
  |------- PARTIAL (seq:2) -----→ |  (No ACK)
  |------- FINAL ---------------→ |
  |←------ ACK {ackType:"FINAL"}- |
```

- `HELLO` → ACK response (client registration confirmation)
- `PTT_START` → No ACK
- `PARTIAL` → No ACK (real-time streaming)
- `FINAL` → ACK response (text injection confirmation)
- `HEARTBEAT` → No ACK

### Heartbeat Behavior

- **Interval**: Client sends `HEARTBEAT` message every 5 seconds
- **Timeout**: Server tracks last heartbeat time per client
- **Disconnect detection**: 3 consecutive misses (15 seconds) marks client as timed out
- Server's `ClientRegistry` detects timed-out clients via `heartbeat_timeout` (default 15s)
