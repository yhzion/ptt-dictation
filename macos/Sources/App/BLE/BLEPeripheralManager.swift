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
    private var subscribedCentrals: Set<UUID> = []
    private var devicesByCentral: [UUID: ConnectedDevice] = [:]

    private(set) var connectedDevices: [ConnectedDevice] = []
    private(set) var isAdvertising = false

    var onFinalText: ((String) -> Void)?
    var onPartialText: ((String) -> Void)?
    var onPttStart: ((String) -> Void)?
    var onPttEnd: ((String) -> Void)?
    var onDeviceConnected: ((ConnectedDevice) -> Void)?
    var onDeviceDisconnected: (() -> Void)?

    func startAdvertising() {
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }

    func stopAdvertising() {
        peripheralManager?.stopAdvertising()
        subscribedCentrals.removeAll()
        devicesByCentral.removeAll()
        connectedDevices.removeAll()
        isAdvertising = false
    }

    func handleIncomingData(_ data: Data, characteristicUUID: CBUUID, centralId: UUID? = nil) {
        guard let message = try? BLEMessage.decode(from: data) else { return }

        switch message {
        case .hello(let deviceModel, let engine):
            if let centralId {
                subscribedCentrals.insert(centralId)
                let previous = devicesByCentral[centralId]
                let device = ConnectedDevice(
                    deviceModel: deviceModel,
                    engine: engine,
                    connectedAt: previous?.connectedAt ?? Date()
                )
                let shouldNotify =
                    previous == nil ||
                    previous?.deviceModel != deviceModel ||
                    previous?.engine != engine
                devicesByCentral[centralId] = device
                connectedDevices = Array(devicesByCentral.values)
                if shouldNotify {
                    onDeviceConnected?(device)
                }
            } else {
                let device = ConnectedDevice(deviceModel: deviceModel, engine: engine, connectedAt: Date())
                connectedDevices = [device]
                onDeviceConnected?(device)
            }

        case .pttStart(let sessionId):
            onPttStart?(sessionId)

        case .pttEnd(let sessionId):
            onPttEnd?(sessionId)

        case .partial(_, _, let text, _):
            onPartialText?(text)

        case .final(_, let text, _):
            onFinalText?(text)

        default:
            break
        }
    }

    func handleCentralSubscribed(_ centralId: UUID) {
        subscribedCentrals.insert(centralId)
        guard devicesByCentral[centralId] == nil else { return }

        let placeholder = ConnectedDevice(
            deviceModel: Self.unknownDeviceModel,
            engine: "",
            connectedAt: Date()
        )
        devicesByCentral[centralId] = placeholder
        connectedDevices = Array(devicesByCentral.values)
        onDeviceConnected?(placeholder)
    }

    func handleCentralDisconnected(_ centralId: UUID) {
        subscribedCentrals.remove(centralId)
        devicesByCentral.removeValue(forKey: centralId)
        connectedDevices = Array(devicesByCentral.values)

        if connectedDevices.isEmpty {
            onDeviceDisconnected?()
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
        guard peripheral.state == .poweredOn else {
            subscribedCentrals.removeAll()
            devicesByCentral.removeAll()
            if !connectedDevices.isEmpty {
                connectedDevices.removeAll()
                onDeviceDisconnected?()
            }
            return
        }
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
            handleIncomingData(data, characteristicUUID: request.characteristic.uuid, centralId: request.central.identifier)
            peripheral.respond(to: request, withResult: .success)
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        guard characteristic.uuid == BLEConstants.statusCharUUID else { return }
        handleCentralSubscribed(central.identifier)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        guard characteristic.uuid == BLEConstants.statusCharUUID else { return }
        handleCentralDisconnected(central.identifier)
    }

    private static let unknownDeviceModel = "Unknown device"
}
