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
