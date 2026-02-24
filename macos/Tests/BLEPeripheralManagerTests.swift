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
}
