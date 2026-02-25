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
        {"type":"FINAL","payload":{"sessionId":"s-1","text":"test text","confidence":0.95}}
        """
        manager.handleIncomingData(json.data(using: .utf8)!, characteristicUUID: BLEConstants.finalTextCharUUID)
        XCTAssertEqual(receivedText, "test text")
    }

    func testHandlePttEndCallsDelegate() {
        let manager = BLEPeripheralManager()
        var receivedSessionId: String?
        manager.onPttEnd = { sessionId in receivedSessionId = sessionId }

        let json = """
        {"type":"PTT_END","payload":{"sessionId":"s-1"}}
        """
        manager.handleIncomingData(json.data(using: .utf8)!, characteristicUUID: BLEConstants.controlCharUUID)
        XCTAssertEqual(receivedSessionId, "s-1")
    }

    func testSubscribeMarksConnectedWithoutHello() {
        let manager = BLEPeripheralManager()
        let centralId = UUID()

        manager.handleCentralSubscribed(centralId)

        XCTAssertEqual(manager.connectedDevices.count, 1)
        XCTAssertEqual(manager.connectedDevices.first?.deviceModel, "Unknown device")
    }

    func testHelloUpdatesPlaceholderDeviceInfo() {
        let manager = BLEPeripheralManager()
        let centralId = UUID()
        var connectedModels: [String] = []
        manager.onDeviceConnected = { device in connectedModels.append(device.deviceModel) }

        manager.handleCentralSubscribed(centralId)

        let json = """
        {"type":"HELLO","payload":{"deviceModel":"Galaxy S23","engine":"Google","capabilities":["BLE"]}}
        """
        manager.handleIncomingData(
            json.data(using: .utf8)!,
            characteristicUUID: BLEConstants.deviceInfoCharUUID,
            centralId: centralId
        )

        XCTAssertEqual(manager.connectedDevices.count, 1)
        XCTAssertEqual(manager.connectedDevices.first?.deviceModel, "Galaxy S23")
        XCTAssertEqual(manager.connectedDevices.first?.engine, "Google")
        XCTAssertEqual(connectedModels, ["Unknown device", "Galaxy S23"])
    }

    func testDisconnectAfterSubscribeClearsConnectedDevices() {
        let manager = BLEPeripheralManager()
        let centralId = UUID()
        var didDisconnect = false
        manager.onDeviceDisconnected = { didDisconnect = true }

        manager.handleCentralSubscribed(centralId)
        manager.handleCentralDisconnected(centralId)

        XCTAssertTrue(manager.connectedDevices.isEmpty)
        XCTAssertTrue(didDisconnect)
    }
}
