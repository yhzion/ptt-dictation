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
        {"type":"PARTIAL","payload":{"sessionId":"s-abc123","seq":3,"text":"hello","confidence":0.85}}
        """
        let message = try BLEMessage.decode(from: json.data(using: .utf8)!)
        guard case .partial(_, _, let text, _) = message else {
            XCTFail("Expected partial")
            return
        }
        XCTAssertEqual(text, "hello")
    }

    func testDecodeFinal() throws {
        let json = """
        {"type":"FINAL","payload":{"sessionId":"s-abc123","text":"hello nice to meet you","confidence":0.95}}
        """
        let message = try BLEMessage.decode(from: json.data(using: .utf8)!)
        guard case .final(_, let text, _) = message else {
            XCTFail("Expected final")
            return
        }
        XCTAssertEqual(text, "hello nice to meet you")
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
