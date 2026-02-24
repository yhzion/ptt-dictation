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
