import AppKit

class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private let bleManager = BLEPeripheralManager()
    private let textInjector: TextInjector = ClipboardTextInjector()

    private var deviceMenuItem: NSMenuItem!
    private var isDeviceConnected = false
    private var preferencesWindow: PreferencesWindow?

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
        menu.addItem(NSMenuItem(title: "Preferences...", action: #selector(openPreferences), keyEquivalent: ","))
        menu.addItem(NSMenuItem.separator())
        menu.addItem(NSMenuItem(title: "Quit", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
        statusItem.menu = menu
    }

    private func setupBLE() {
        bleManager.onDeviceConnected = { [weak self] device in
            DispatchQueue.main.async {
                self?.isDeviceConnected = true
                self?.updateStatusIcon(dotColor: .systemGreen)
                self?.deviceMenuItem.title = "\(device.deviceModel) — Connected"
            }
        }

        bleManager.onDeviceDisconnected = { [weak self] in
            DispatchQueue.main.async {
                self?.isDeviceConnected = false
                self?.updateStatusIcon(dotColor: nil)
                self?.deviceMenuItem.title = "No devices connected"
            }
        }

        bleManager.onPttStart = { [weak self] _ in
            DispatchQueue.main.async {
                self?.updateStatusIcon(dotColor: .systemRed)
            }
        }

        bleManager.onFinalText = { [weak self] text in
            DispatchQueue.main.async {
                self?.textInjector.inject(text: text)
                if self?.isDeviceConnected == true {
                    self?.updateStatusIcon(dotColor: .systemGreen)
                }
            }
        }

        bleManager.startAdvertising()
    }

    @objc private func openPreferences() {
        if preferencesWindow == nil {
            preferencesWindow = PreferencesWindow()
        }
        preferencesWindow?.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    private func updateStatusIcon(dotColor: NSColor?) {
        let symbolName = isDeviceConnected ? "mic.fill" : "mic.slash"
        guard let baseImage = NSImage(
            systemSymbolName: symbolName,
            accessibilityDescription: "PTT Dictation"
        ) else { return }

        if let color = dotColor {
            statusItem.button?.image = statusBarIcon(base: baseImage, dotColor: color)
        } else {
            statusItem.button?.image = baseImage
        }
    }

    private func statusBarIcon(base: NSImage, dotColor: NSColor) -> NSImage {
        let size = base.size
        let image = NSImage(size: size, flipped: false) { rect in
            // Draw SF Symbol and tint to menu bar foreground color
            base.draw(in: rect)
            NSColor.controlTextColor.setFill()
            rect.fill(using: .sourceAtop)

            // Small dot at top-right corner (outside the mic glyph area)
            let dotSize: CGFloat = 2.5
            let dotRect = NSRect(
                x: rect.maxX - dotSize,
                y: rect.maxY - dotSize,
                width: dotSize,
                height: dotSize
            )
            dotColor.setFill()
            NSBezierPath(ovalIn: dotRect).fill()
            return true
        }
        image.isTemplate = false
        return image
    }
}
