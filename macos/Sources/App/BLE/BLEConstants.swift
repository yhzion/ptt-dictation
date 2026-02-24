import CoreBluetooth

enum BLEConstants {
    static let serviceUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567890")
    static let controlCharUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567891")
    static let partialTextCharUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567892")
    static let finalTextCharUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567893")
    static let deviceInfoCharUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567894")
    static let statusCharUUID = CBUUID(string: "A1B2C3D4-E5F6-7890-ABCD-EF1234567895")
}
