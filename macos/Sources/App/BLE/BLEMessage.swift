import Foundation

enum BLEMessage {
    case hello(deviceModel: String, engine: String)
    case pttStart(sessionId: String)
    case pttEnd(sessionId: String)
    case partial(sessionId: String, seq: Int, text: String, confidence: Double)
    case final(sessionId: String, text: String, confidence: Double)

    // Desktop -> Phone
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
        case "PTT_END":
            return .pttEnd(sessionId: payload["sessionId"] as! String)
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
