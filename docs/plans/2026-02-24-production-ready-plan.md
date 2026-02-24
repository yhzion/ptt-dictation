# Production-Ready PTT Dictation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** MVP의 Tauri+WebSocket 아키텍처를 Swift/AppKit 네이티브 메뉴바 앱 + BLE 통신으로 전환한다.

**Architecture:** macOS는 CoreBluetooth Peripheral로 GATT 서비스를 광고하고, Android는 BLE Central로 스캔/연결하여 딕테이션 텍스트를 전송한다. macOS 앱은 NSStatusItem 기반 메뉴바 앱으로 동작하며, 수신된 텍스트를 클립보드+Cmd+V로 주입한다.

**Tech Stack:** Swift 5.9+, AppKit, CoreBluetooth, Kotlin, Android BLE API, Swift Package Manager

---

## Shared Constants

BLE GATT 서비스/특성 UUID (양 플랫폼에서 동일하게 사용):

```
Service UUID:         "A1B2C3D4-E5F6-7890-ABCD-EF1234567890"
Control Char UUID:    "A1B2C3D4-E5F6-7890-ABCD-EF1234567891"  (Write) — PTT_START/STOP
Partial Text UUID:    "A1B2C3D4-E5F6-7890-ABCD-EF1234567892"  (Write Without Response) — PARTIAL
Final Text UUID:      "A1B2C3D4-E5F6-7890-ABCD-EF1234567893"  (Write) — FINAL
Device Info UUID:     "A1B2C3D4-E5F6-7890-ABCD-EF1234567894"  (Write) — HELLO
Status UUID:          "A1B2C3D4-E5F6-7890-ABCD-EF1234567895"  (Read + Notify) — ACK/Status
```

BLE 메시지는 UTF-8 인코딩된 JSON 문자열. 기존 프로토콜 메시지 구조를 그대로 사용하되 clientId는 BLE 연결에서 자동 식별되므로 생략 가능.

---

## Task 1: macOS Swift 프로젝트 스캐폴딩

**Files:**
- Create: `macos/Package.swift`
- Create: `macos/Sources/App/AppDelegate.swift`
- Create: `macos/Sources/App/main.swift`

**Step 1: 프로젝트 디렉토리 생성 및 Package.swift 작성**

```swift
// macos/Package.swift
// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "PttDictation",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(
            name: "PttDictation",
            dependencies: [],
            path: "Sources/App"
        ),
        .testTarget(
            name: "PttDictationTests",
            dependencies: ["PttDictation"],
            path: "Tests"
        ),
    ]
)
```

**Step 2: main.swift — NSApplication 부트스트랩**

```swift
// macos/Sources/App/main.swift
import AppKit

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.run()
```

**Step 3: AppDelegate.swift — 최소 메뉴바 앱**

```swift
// macos/Sources/App/AppDelegate.swift
import AppKit

class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!

    func applicationDidFinishLaunching(_ notification: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)

        if let button = statusItem.button {
            button.image = NSImage(
                systemSymbolName: "mic.slash",
                accessibilityDescription: "PTT Dictation"
            )
        }

        let menu = NSMenu()
        menu.addItem(NSMenuItem(title: "No devices connected", action: nil, keyEquivalent: ""))
        menu.addItem(NSMenuItem.separator())
        menu.addItem(NSMenuItem(title: "Quit", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
        statusItem.menu = menu
    }
}
```

**Step 4: 빌드 및 실행 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/macos && swift build`
Expected: 빌드 성공

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/macos && swift run`
Expected: 메뉴바에 마이크 아이콘 표시. Ctrl+C로 종료.

**Step 5: Info.plist 추가 (Dock 아이콘 숨기기)**

```xml
<!-- macos/Sources/App/Resources/Info.plist -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>LSUIElement</key>
    <true/>
    <key>NSBluetoothAlwaysUsageDescription</key>
    <string>PTT Dictation uses Bluetooth to receive dictation from your phone.</string>
</dict>
</plist>
```

**Step 6: Commit**

```bash
git add macos/
git commit -m "feat(macos): scaffold Swift menu bar app with NSStatusItem"
```

---

## Task 2: BLE 상수 및 프로토콜 모델 (macOS)

**Files:**
- Create: `macos/Sources/App/BLE/BLEConstants.swift`
- Create: `macos/Sources/App/BLE/BLEMessage.swift`
- Create: `macos/Tests/BLEMessageTests.swift`

**Step 1: 실패하는 테스트 작성**

```swift
// macos/Tests/BLEMessageTests.swift
import XCTest
@testable import PttDictation

final class BLEMessageTests: XCTestCase {
    func testDecodePttStart() throws {
        let json = """
        {"type":"PTT_START","payload":{"sessionId":"s-abc123"}}
        """
        let message = try BLEMessage.decode(from: json.data(using: .utf8)!)
        guard case .pttStart(let sessionId) = message else {
            XCTFail("Expected pttStart")
            return
        }
        XCTAssertEqual(sessionId, "s-abc123")
    }

    func testDecodePartial() throws {
        let json = """
        {"type":"PARTIAL","payload":{"sessionId":"s-abc123","seq":3,"text":"안녕하세요","confidence":0.85}}
        """
        let message = try BLEMessage.decode(from: json.data(using: .utf8)!)
        guard case .partial(_, _, let text, _) = message else {
            XCTFail("Expected partial")
            return
        }
        XCTAssertEqual(text, "안녕하세요")
    }

    func testDecodeFinal() throws {
        let json = """
        {"type":"FINAL","payload":{"sessionId":"s-abc123","text":"안녕하세요 반갑습니다","confidence":0.95}}
        """
        let message = try BLEMessage.decode(from: json.data(using: .utf8)!)
        guard case .final(_, let text, _) = message else {
            XCTFail("Expected final")
            return
        }
        XCTAssertEqual(text, "안녕하세요 반갑습니다")
    }

    func testDecodeHello() throws {
        let json = """
        {"type":"HELLO","payload":{"deviceModel":"Galaxy S23","engine":"Google","capabilities":["BLE"]}}
        """
        let message = try BLEMessage.decode(from: json.data(using: .utf8)!)
        guard case .hello(let deviceModel, let engine) = message else {
            XCTFail("Expected hello")
            return
        }
        XCTAssertEqual(deviceModel, "Galaxy S23")
        XCTAssertEqual(engine, "Google")
    }

    func testEncodeAck() throws {
        let data = BLEMessage.ack(ackType: "FINAL").encode()
        let json = String(data: data, encoding: .utf8)!
        XCTAssertTrue(json.contains("\"type\":\"ACK\""))
        XCTAssertTrue(json.contains("\"ackType\":\"FINAL\""))
    }
}
```

**Step 2: 테스트 실패 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/macos && swift test`
Expected: 컴파일 에러 (BLEMessage 타입 없음)

**Step 3: BLEConstants.swift 구현**

```swift
// macos/Sources/App/BLE/BLEConstants.swift
import CoreBluetooth

enum BLEConstants {
    static let serviceUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567890")
    static let controlCharUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567891")
    static let partialTextCharUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567892")
    static let finalTextCharUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567893")
    static let deviceInfoCharUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567894")
    static let statusCharUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567895")
}
```

**Step 4: BLEMessage.swift 구현**

```swift
// macos/Sources/App/BLE/BLEMessage.swift
import Foundation

enum BLEMessage {
    case hello(deviceModel: String, engine: String)
    case pttStart(sessionId: String)
    case partial(sessionId: String, seq: Int, text: String, confidence: Double)
    case final(sessionId: String, text: String, confidence: Double)

    // Desktop → Phone
    case ack(ackType: String)

    static func decode(from data: Data) throws -> BLEMessage {
        let json = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        let type = json["type"] as! String
        let payload = json["payload"] as? [String: Any] ?? [:]

        switch type {
        case "HELLO":
            return .hello(
                deviceModel: payload["deviceModel"] as? String ?? "Unknown",
                engine: payload["engine"] as? String ?? "Unknown"
            )
        case "PTT_START":
            return .pttStart(sessionId: payload["sessionId"] as! String)
        case "PARTIAL":
            return .partial(
                sessionId: payload["sessionId"] as! String,
                seq: payload["seq"] as! Int,
                text: payload["text"] as! String,
                confidence: payload["confidence"] as! Double
            )
        case "FINAL":
            return .final(
                sessionId: payload["sessionId"] as! String,
                text: payload["text"] as! String,
                confidence: payload["confidence"] as! Double
            )
        default:
            throw NSError(domain: "BLEMessage", code: -1, userInfo: [NSLocalizedDescriptionKey: "Unknown type: \(type)"])
        }
    }

    func encode() -> Data {
        var dict: [String: Any] = [:]
        switch self {
        case .ack(let ackType):
            dict["type"] = "ACK"
            dict["payload"] = ["ackType": ackType]
        default:
            break
        }
        return try! JSONSerialization.data(withJSONObject: dict)
    }
}
```

**Step 5: 테스트 통과 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/macos && swift test`
Expected: 5 tests passed

**Step 6: Commit**

```bash
git add macos/Sources/App/BLE/ macos/Tests/
git commit -m "feat(macos): add BLE constants and message protocol model"
```

---

## Task 3: macOS CoreBluetooth Peripheral

**Files:**
- Create: `macos/Sources/App/BLE/BLEPeripheralManager.swift`
- Create: `macos/Tests/BLEPeripheralManagerTests.swift`

**Step 1: 실패하는 테스트 작성**

```swift
// macos/Tests/BLEPeripheralManagerTests.swift
import XCTest
@testable import PttDictation

final class BLEPeripheralManagerTests: XCTestCase {
    func testConnectedDeviceTracking() {
        let manager = BLEPeripheralManager()
        XCTAssertTrue(manager.connectedDevices.isEmpty)
    }

    func testHandleHelloMessage() {
        let manager = BLEPeripheralManager()
        let json = """
        {"type":"HELLO","payload":{"deviceModel":"Galaxy S23","engine":"Google","capabilities":["BLE"]}}
        """
        manager.handleIncomingData(json.data(using: .utf8)!, characteristicUUID: BLEConstants.deviceInfoCharUUID)
        XCTAssertEqual(manager.connectedDevices.count, 1)
        XCTAssertEqual(manager.connectedDevices.first?.deviceModel, "Galaxy S23")
    }

    func testHandleFinalMessageCallsDelegate() {
        let manager = BLEPeripheralManager()
        var receivedText: String?
        manager.onFinalText = { text in receivedText = text }

        let json = """
        {"type":"FINAL","payload":{"sessionId":"s-1","text":"테스트 텍스트","confidence":0.95}}
        """
        manager.handleIncomingData(json.data(using: .utf8)!, characteristicUUID: BLEConstants.finalTextCharUUID)
        XCTAssertEqual(receivedText, "테스트 텍스트")
    }
}
```

**Step 2: 테스트 실패 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/macos && swift test`
Expected: 컴파일 에러

**Step 3: BLEPeripheralManager.swift 구현**

```swift
// macos/Sources/App/BLE/BLEPeripheralManager.swift
import CoreBluetooth
import Foundation

struct ConnectedDevice {
    let deviceModel: String
    let engine: String
    let connectedAt: Date
}

class BLEPeripheralManager: NSObject {
    private var peripheralManager: CBPeripheralManager?
    private var service: CBMutableService?
    private var statusCharacteristic: CBMutableCharacteristic?

    private(set) var connectedDevices: [ConnectedDevice] = []
    private(set) var isAdvertising = false

    var onFinalText: ((String) -> Void)?
    var onPartialText: ((String) -> Void)?
    var onPttStart: ((String) -> Void)?
    var onDeviceConnected: ((ConnectedDevice) -> Void)?
    var onDeviceDisconnected: (() -> Void)?

    func startAdvertising() {
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }

    func stopAdvertising() {
        peripheralManager?.stopAdvertising()
        isAdvertising = false
    }

    func handleIncomingData(_ data: Data, characteristicUUID: CBUUID) {
        guard let message = try? BLEMessage.decode(from: data) else { return }

        switch message {
        case .hello(let deviceModel, let engine):
            let device = ConnectedDevice(deviceModel: deviceModel, engine: engine, connectedAt: Date())
            connectedDevices.append(device)
            onDeviceConnected?(device)

        case .pttStart(let sessionId):
            onPttStart?(sessionId)

        case .partial(_, _, let text, _):
            onPartialText?(text)

        case .final(_, let text, _):
            onFinalText?(text)

        default:
            break
        }
    }

    private func setupService() {
        let controlChar = CBMutableCharacteristic(
            type: BLEConstants.controlCharUUID,
            properties: [.write],
            value: nil,
            permissions: [.writeable]
        )
        let partialTextChar = CBMutableCharacteristic(
            type: BLEConstants.partialTextCharUUID,
            properties: [.writeWithoutResponse],
            value: nil,
            permissions: [.writeable]
        )
        let finalTextChar = CBMutableCharacteristic(
            type: BLEConstants.finalTextCharUUID,
            properties: [.write],
            value: nil,
            permissions: [.writeable]
        )
        let deviceInfoChar = CBMutableCharacteristic(
            type: BLEConstants.deviceInfoCharUUID,
            properties: [.write],
            value: nil,
            permissions: [.writeable]
        )
        statusCharacteristic = CBMutableCharacteristic(
            type: BLEConstants.statusCharUUID,
            properties: [.read, .notify],
            value: nil,
            permissions: [.readable]
        )

        let service = CBMutableService(type: BLEConstants.serviceUUID, primary: true)
        service.characteristics = [controlChar, partialTextChar, finalTextChar, deviceInfoChar, statusCharacteristic!]
        self.service = service
        peripheralManager?.add(service)
    }
}

extension BLEPeripheralManager: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        guard peripheral.state == .poweredOn else { return }
        setupService()
        peripheral.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [BLEConstants.serviceUUID],
            CBAdvertisementDataLocalNameKey: "PTT-Dictation",
        ])
        isAdvertising = true
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            guard let data = request.value else { continue }
            handleIncomingData(data, characteristicUUID: request.characteristic.uuid)
            peripheral.respond(to: request, withResult: .success)
        }
    }
}
```

**Step 4: 테스트 통과 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/macos && swift test`
Expected: 모든 테스트 통과

**Step 5: Commit**

```bash
git add macos/Sources/App/BLE/BLEPeripheralManager.swift macos/Tests/BLEPeripheralManagerTests.swift
git commit -m "feat(macos): add CoreBluetooth peripheral manager with GATT service"
```

---

## Task 4: macOS 텍스트 주입

**Files:**
- Create: `macos/Sources/App/TextInjection/TextInjector.swift`
- Create: `macos/Tests/TextInjectorTests.swift`

**Step 1: 실패하는 테스트 작성**

```swift
// macos/Tests/TextInjectorTests.swift
import XCTest
@testable import PttDictation

final class TextInjectorTests: XCTestCase {
    func testProtocolExists() {
        // TextInjector 프로토콜이 존재하고 MockTextInjector가 구현 가능한지 확인
        let mock = MockTextInjector()
        mock.inject(text: "테스트")
        XCTAssertEqual(mock.lastInjectedText, "테스트")
    }

    func testEmptyTextIsIgnored() {
        let mock = MockTextInjector()
        mock.inject(text: "")
        XCTAssertNil(mock.lastInjectedText)
    }
}

class MockTextInjector: TextInjector {
    var lastInjectedText: String?

    func inject(text: String) {
        guard !text.isEmpty else { return }
        lastInjectedText = text
    }
}
```

**Step 2: 테스트 실패 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/macos && swift test`
Expected: 컴파일 에러

**Step 3: TextInjector.swift 구현**

```swift
// macos/Sources/App/TextInjection/TextInjector.swift
import AppKit
import Carbon.HIToolbox

protocol TextInjector {
    func inject(text: String)
}

class ClipboardTextInjector: TextInjector {
    private let pasteboard = NSPasteboard.general

    func inject(text: String) {
        guard !text.isEmpty else { return }

        // 1. 클립보드 백업
        let backup = pasteboard.string(forType: .string)

        // 2. 텍스트를 클립보드에 설정
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)

        // 3. Cmd+V 실행
        let source = CGEventSource(stateID: .hidSystemState)
        let keyDown = CGEvent(keyboardEventSource: source, virtualKey: CGKeyCode(kVK_ANSI_V), keyDown: true)
        let keyUp = CGEvent(keyboardEventSource: source, virtualKey: CGKeyCode(kVK_ANSI_V), keyDown: false)
        keyDown?.flags = .maskCommand
        keyUp?.flags = .maskCommand
        keyDown?.post(tap: .cghidEventTap)
        keyUp?.post(tap: .cghidEventTap)

        // 4. 붙여넣기 완료 대기 후 클립보드 복원
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
            guard let self else { return }
            self.pasteboard.clearContents()
            if let backup {
                self.pasteboard.setString(backup, forType: .string)
            }
        }
    }
}
```

**Step 4: 테스트 통과 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/macos && swift test`
Expected: 모든 테스트 통과

**Step 5: Commit**

```bash
git add macos/Sources/App/TextInjection/ macos/Tests/TextInjectorTests.swift
git commit -m "feat(macos): add text injection via clipboard + Cmd+V (CGEvent)"
```

---

## Task 5: macOS 앱 통합 — BLE + 메뉴바 + 텍스트 주입

**Files:**
- Modify: `macos/Sources/App/AppDelegate.swift`

**Step 1: AppDelegate에 BLE + 텍스트 주입 통합**

```swift
// macos/Sources/App/AppDelegate.swift
import AppKit

class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private let bleManager = BLEPeripheralManager()
    private let textInjector: TextInjector = ClipboardTextInjector()

    private var deviceMenuItem: NSMenuItem!

    func applicationDidFinishLaunching(_ notification: Notification) {
        setupStatusItem()
        setupBLE()
    }

    private func setupStatusItem() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)

        if let button = statusItem.button {
            button.image = NSImage(
                systemSymbolName: "mic.slash",
                accessibilityDescription: "PTT Dictation"
            )
        }

        let menu = NSMenu()
        deviceMenuItem = NSMenuItem(title: "No devices connected", action: nil, keyEquivalent: "")
        menu.addItem(deviceMenuItem)
        menu.addItem(NSMenuItem.separator())
        menu.addItem(NSMenuItem(title: "Quit", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
        statusItem.menu = menu
    }

    private func setupBLE() {
        bleManager.onDeviceConnected = { [weak self] device in
            DispatchQueue.main.async {
                self?.updateStatusIcon(connected: true)
                self?.deviceMenuItem.title = "\(device.deviceModel) — Connected"
            }
        }

        bleManager.onDeviceDisconnected = { [weak self] in
            DispatchQueue.main.async {
                self?.updateStatusIcon(connected: false)
                self?.deviceMenuItem.title = "No devices connected"
            }
        }

        bleManager.onFinalText = { [weak self] text in
            DispatchQueue.main.async {
                self?.textInjector.inject(text: text)
            }
        }

        bleManager.startAdvertising()
    }

    private func updateStatusIcon(connected: Bool) {
        let symbolName = connected ? "mic.fill" : "mic.slash"
        statusItem.button?.image = NSImage(
            systemSymbolName: symbolName,
            accessibilityDescription: "PTT Dictation"
        )
    }
}
```

**Step 2: 빌드 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/macos && swift build`
Expected: 빌드 성공

**Step 3: Commit**

```bash
git add macos/Sources/App/AppDelegate.swift
git commit -m "feat(macos): wire BLE peripheral + text injection into menu bar app"
```

---

## Task 6: Android BLE 권한 및 상수 추가

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/ptt/dictation/ble/BLEConstants.kt`

**Step 1: AndroidManifest.xml에 BLE 권한 추가**

기존 권한에 추가:

```xml
<!-- BLE 권한 -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

기존 `INTERNET` 권한과 `android:usesCleartextTraffic="true"`는 유지 (나중에 정리).

**Step 2: BLE 상수 정의**

```kotlin
// android/app/src/main/java/com/ptt/dictation/ble/BLEConstants.kt
package com.ptt.dictation.ble

import java.util.UUID

object BLEConstants {
    val SERVICE_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567890")
    val CONTROL_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567891")
    val PARTIAL_TEXT_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567892")
    val FINAL_TEXT_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567893")
    val DEVICE_INFO_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567894")
    val STATUS_CHAR_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-7890-ABCD-EF1234567895")
}
```

**Step 3: Commit**

```bash
git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/ptt/dictation/ble/
git commit -m "feat(android): add BLE permissions and shared GATT constants"
```

---

## Task 7: Android BLE Central 클라이언트

**Files:**
- Create: `android/app/src/main/java/com/ptt/dictation/ble/BleTransport.kt`
- Create: `android/app/src/test/java/com/ptt/dictation/ble/BleTransportTest.kt`

**Step 1: Transport 인터페이스 정의 및 실패하는 테스트 작성**

```kotlin
// android/app/src/test/java/com/ptt/dictation/ble/BleTransportTest.kt
package com.ptt.dictation.ble

import com.ptt.dictation.model.PttMessage
import com.ptt.dictation.ws.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class BleTransportTest {
    @Test
    fun `initial connection state is DISCONNECTED`() {
        val transport = FakeBleTransport()
        assertEquals(ConnectionState.DISCONNECTED, transport.connectionState.value)
    }

    @Test
    fun `encodeMessage produces valid JSON for PTT_START`() {
        val message = PttMessage.pttStart("client-1", "session-1")
        val json = BleMessageEncoder.encode(message)
        assert(json.contains("PTT_START"))
        assert(json.contains("session-1"))
    }

    @Test
    fun `encodeMessage produces valid JSON for PARTIAL`() {
        val message = PttMessage.partial("client-1", "session-1", 1, "안녕", 0.8)
        val json = BleMessageEncoder.encode(message)
        assert(json.contains("PARTIAL"))
        assert(json.contains("안녕"))
    }

    @Test
    fun `encodeMessage produces valid JSON for FINAL`() {
        val message = PttMessage.finalResult("client-1", "session-1", "안녕하세요", 0.95)
        val json = BleMessageEncoder.encode(message)
        assert(json.contains("FINAL"))
        assert(json.contains("안녕하세요"))
    }
}

class FakeBleTransport : PttTransport {
    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.DISCONNECTED)

    override fun startScanning() {}
    override fun connect(deviceId: String) {}
    override fun disconnect() {}
    override fun send(message: PttMessage) {}
    override fun setListener(listener: PttTransportListener) {}
}
```

**Step 2: 테스트 실패 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/android && ./gradlew test`
Expected: 컴파일 에러 (PttTransport, BleMessageEncoder 없음)

**Step 3: Transport 인터페이스 + BLE 메시지 인코더 구현**

```kotlin
// android/app/src/main/java/com/ptt/dictation/ble/BleTransport.kt
package com.ptt.dictation.ble

import com.ptt.dictation.model.PttMessage
import com.ptt.dictation.ws.ConnectionState
import kotlinx.coroutines.flow.StateFlow

interface PttTransportListener {
    fun onConnected()
    fun onDisconnected()
    fun onError(error: String)
}

interface PttTransport {
    val connectionState: StateFlow<ConnectionState>
    fun startScanning()
    fun connect(deviceId: String)
    fun disconnect()
    fun send(message: PttMessage)
    fun setListener(listener: PttTransportListener)
}

object BleMessageEncoder {
    fun encode(message: PttMessage): String {
        return kotlinx.serialization.json.Json.encodeToString(
            PttMessage.serializer(),
            message,
        )
    }
}
```

**Step 4: 테스트 통과 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/android && ./gradlew test`
Expected: 모든 테스트 통과

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/ptt/dictation/ble/ android/app/src/test/java/com/ptt/dictation/ble/
git commit -m "feat(android): add PttTransport interface and BLE message encoder"
```

---

## Task 8: Android BLE Central 구현

**Files:**
- Create: `android/app/src/main/java/com/ptt/dictation/ble/BleCentralClient.kt`

**Step 1: BleCentralClient 구현**

```kotlin
// android/app/src/main/java/com/ptt/dictation/ble/BleCentralClient.kt
package com.ptt.dictation.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.ptt.dictation.model.PttMessage
import com.ptt.dictation.ws.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class BleCentralClient(
    private val context: Context,
    private val clientId: String,
    private val deviceModel: String,
) : PttTransport {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var listener: PttTransportListener? = null

    private var controlChar: BluetoothGattCharacteristic? = null
    private var partialTextChar: BluetoothGattCharacteristic? = null
    private var finalTextChar: BluetoothGattCharacteristic? = null
    private var deviceInfoChar: BluetoothGattCharacteristic? = null

    override fun setListener(listener: PttTransportListener) {
        this.listener = listener
    }

    override fun startScanning() {
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BLEConstants.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    override fun connect(deviceId: String) {
        _connectionState.value = ConnectionState.CONNECTING
        val device = bluetoothAdapter.getRemoteDevice(deviceId)
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    override fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun send(message: PttMessage) {
        val json = BleMessageEncoder.encode(message)
        val data = json.toByteArray(Charsets.UTF_8)

        val characteristic = when (message.type) {
            "PTT_START" -> controlChar
            "PARTIAL" -> partialTextChar
            "FINAL" -> finalTextChar
            "HELLO" -> deviceInfoChar
            else -> null
        } ?: return

        characteristic.value = data
        val writeType = if (message.type == "PARTIAL") {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        characteristic.writeType = writeType
        bluetoothGatt?.writeCharacteristic(characteristic)
    }

    private fun sendHello() {
        send(PttMessage.hello(clientId, deviceModel, "Google"))
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(this)
            connect(result.device.address)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    listener?.onDisconnected()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val service = gatt.getService(BLEConstants.SERVICE_UUID) ?: return
            controlChar = service.getCharacteristic(BLEConstants.CONTROL_CHAR_UUID)
            partialTextChar = service.getCharacteristic(BLEConstants.PARTIAL_TEXT_CHAR_UUID)
            finalTextChar = service.getCharacteristic(BLEConstants.FINAL_TEXT_CHAR_UUID)
            deviceInfoChar = service.getCharacteristic(BLEConstants.DEVICE_INFO_CHAR_UUID)

            _connectionState.value = ConnectionState.CONNECTED
            listener?.onConnected()
            sendHello()
        }
    }
}
```

**Step 2: 빌드 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/android && ./gradlew compileDebugKotlin`
Expected: 빌드 성공

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/ptt/dictation/ble/BleCentralClient.kt
git commit -m "feat(android): implement BLE central client with GATT service discovery"
```

---

## Task 9: Android ViewModel BLE 전환

**Files:**
- Modify: `android/app/src/main/java/com/ptt/dictation/ui/PttViewModel.kt`
- Modify: `android/app/src/main/java/com/ptt/dictation/ui/PttScreen.kt`

**Step 1: PttViewModel — WebSocketClient → PttTransport 교체**

PttViewModel의 생성자에서 `wsClient: WebSocketClient` 파라미터를 `transport: PttTransport`로 변경.

주요 변경 사항:
- `wsClient.connect(url)` → `transport.startScanning()` (BLE 스캔으로 자동 연결)
- `wsClient.send(message)` → `transport.send(message)`
- `wsClient.disconnect()` → `transport.disconnect()`
- `wsClient.setListener(...)` → `transport.setListener(...)` (인터페이스 약간 다름)
- `serverHost`, `serverPort` 상태 제거 (BLE에서 불필요)
- Heartbeat 로직 제거 (BLE connection state로 대체)

**Step 2: PttScreen — 서버 주소 입력 UI 제거**

- `ServerHostField`, `ServerPortField` 제거
- `Connect` 버튼 → `Scan` 버튼으로 변경
- 연결 상태 표시는 유지

**Step 3: 기존 테스트 수정**

`PttViewModelTest`에서 `FakeBleTransport`를 사용하도록 변경. 기존 PTT 흐름 테스트(onPttPress, onPttRelease)는 transport 인터페이스만 바뀌므로 로직은 동일.

**Step 4: 모든 테스트 통과 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/android && ./gradlew test`
Expected: 모든 테스트 통과

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/ptt/dictation/ui/ android/app/src/test/
git commit -m "refactor(android): replace WebSocket with BLE transport in ViewModel and UI"
```

---

## Task 10: E2E 통합 테스트 (수동)

**전제 조건:**
- macOS에서 Bluetooth 활성화
- Android 실제 기기에서 Bluetooth + 위치 권한 허용

**Step 1: macOS 앱 실행**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/macos && swift run`
Expected: 메뉴바에 mic.slash 아이콘 표시

**Step 2: Android 앱 빌드 및 설치**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/android && ./gradlew installDebug`
Expected: 앱 설치 성공

**Step 3: 연결 테스트**

1. Android 앱에서 "Scan" 버튼 누르기
2. macOS 메뉴바 아이콘이 mic.slash → mic.fill로 변경 확인
3. 메뉴 드롭다운에서 연결된 기기 이름 확인

**Step 4: PTT 텍스트 전송 테스트**

1. macOS에서 텍스트 에디터 열기 (커서 포커스)
2. Android에서 PTT 버튼 누르고 "안녕하세요" 말하기
3. macOS 텍스트 에디터에 "안녕하세요" 텍스트 입력 확인

**Step 5: 결과 기록 후 Commit**

```bash
git commit --allow-empty -m "test: verify E2E BLE PTT dictation flow on real devices"
```

---

## Task 11: 레거시 코드 정리

**Files:**
- Remove: `android/app/src/main/java/com/ptt/dictation/ws/OkHttpWebSocketClient.kt`
- Remove: `android/app/src/main/java/com/ptt/dictation/ws/WebSocketManager.kt` (인터페이스는 PttTransport로 대체됨)
- Remove: `android/app/src/test/java/com/ptt/dictation/ws/WebSocketManagerTest.kt`
- Modify: `android/app/build.gradle.kts` — OkHttp 의존성 제거
- Modify: `android/app/src/main/AndroidManifest.xml` — `INTERNET` 권한 및 `usesCleartextTraffic` 제거
- Archive: `desktop/` — git에서 제거하되, 필요시 git history에서 복원 가능

**주의:** `desktop/` 폴더는 삭제하기 전 사용자에게 확인받기.

**Step 1: Android WebSocket 코드 제거**

```bash
rm android/app/src/main/java/com/ptt/dictation/ws/OkHttpWebSocketClient.kt
rm android/app/src/main/java/com/ptt/dictation/ws/WebSocketManager.kt
rm android/app/src/test/java/com/ptt/dictation/ws/WebSocketManagerTest.kt
```

**Step 2: build.gradle.kts에서 OkHttp 제거**

`implementation("com.squareup.okhttp3:okhttp:4.12.0")` 라인 제거

**Step 3: 모든 테스트 통과 확인**

Run: `cd /Users/youngho.jeon/datamaker/ptt-dictation/android && ./gradlew test`
Expected: 모든 테스트 통과 (WebSocket 관련 테스트 제거됨)

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: remove WebSocket transport layer (replaced by BLE)"
```

---

## Task 12: 문서 업데이트

**Files:**
- Modify: `docs/protocol-spec.md` — BLE 매핑 섹션 추가
- Modify: `README.md` — 아키텍처 변경 반영

**Step 1: protocol-spec.md에 BLE Transport 섹션 추가**

기존 WebSocket 섹션은 유지하되, "BLE Transport" 섹션을 추가:
- GATT 서비스/특성 UUID 표
- WebSocket 메시지 → BLE characteristic 매핑 표
- Heartbeat → BLE connection state 변경 설명

**Step 2: README.md 업데이트**

- 아키텍처 다이어그램을 BLE 기반으로 변경
- 빌드/실행 방법에 macOS Swift 앱 추가
- 연결 방법: "Bluetooth 페어링" 설명

**Step 3: Commit**

```bash
git add docs/ README.md
git commit -m "docs: update protocol spec and README for BLE architecture"
```

---

## Summary

| Task | 내용 | 예상 산출물 |
|------|------|-----------|
| 1 | macOS Swift 프로젝트 스캐폴딩 | 메뉴바 아이콘이 뜨는 최소 앱 |
| 2 | BLE 상수 + 프로토콜 모델 | BLEConstants, BLEMessage + 테스트 5개 |
| 3 | macOS CoreBluetooth Peripheral | GATT 서비스 광고 + 메시지 수신 + 테스트 3개 |
| 4 | macOS 텍스트 주입 | ClipboardTextInjector + 테스트 2개 |
| 5 | macOS 앱 통합 | BLE → 텍스트 주입 파이프라인 완성 |
| 6 | Android BLE 권한 + 상수 | Manifest 권한, BLEConstants.kt |
| 7 | Android BLE Transport | PttTransport 인터페이스 + 인코더 + 테스트 4개 |
| 8 | Android BLE Central 구현 | BleCentralClient (스캔, 연결, 전송) |
| 9 | Android ViewModel 전환 | WebSocket → BLE 교체, UI 업데이트 |
| 10 | E2E 통합 테스트 | 실제 기기 BLE PTT 딕테이션 검증 |
| 11 | 레거시 정리 | WebSocket 코드 제거, OkHttp 제거 |
| 12 | 문서 업데이트 | protocol-spec, README 갱신 |
