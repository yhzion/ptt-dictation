import AppKit

class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private let bleManager = BLEPeripheralManager()
    private let textInjector: TextInjector = ClipboardTextInjector()

    private var deviceMenuItem: NSMenuItem!
    private var isDeviceConnected = false
    private var preferencesWindow: PreferencesWindow?
    private var pttActiveWatchdog: DispatchWorkItem?
    private var isPttActive = false
    private var lastPttActivityAt: Date?

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
                self?.cancelPttWatchdog()
                self?.isDeviceConnected = false
                self?.updateStatusIcon(dotColor: nil)
                self?.deviceMenuItem.title = "No devices connected"
            }
        }

        bleManager.onPttStart = { [weak self] _ in
            DispatchQueue.main.async {
                self?.setPttActive()
            }
        }

        bleManager.onPartialText = { [weak self] _ in
            DispatchQueue.main.async {
                self?.notePttActivity()
            }
        }

        bleManager.onPttEnd = { [weak self] _ in
            DispatchQueue.main.async {
                self?.setPttInactive()
            }
        }

        bleManager.onFinalText = { [weak self] text in
            DispatchQueue.main.async {
                self?.textInjector.inject(text: text)
                self?.setPttInactive()
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

    private func setPttActive() {
        isPttActive = true
        lastPttActivityAt = Date()
        updateStatusIcon(dotColor: .systemRed)
        schedulePttWatchdog()
    }

    private func setPttInactive() {
        isPttActive = false
        lastPttActivityAt = nil
        cancelPttWatchdog()
        if isDeviceConnected {
            updateStatusIcon(dotColor: .systemGreen)
        } else {
            updateStatusIcon(dotColor: nil)
        }
    }

    private func notePttActivity() {
        guard isPttActive else { return }
        lastPttActivityAt = Date()
    }

    private func schedulePttWatchdog() {
        cancelPttWatchdog()
        let watchdog =
            DispatchWorkItem { [weak self] in
                guard let self else { return }
                guard self.isPttActive else { return }

                let now = Date()
                let lastActivity = self.lastPttActivityAt ?? now
                let elapsed = now.timeIntervalSince(lastActivity)

                if elapsed >= Self.pttStaleTimeoutSec {
                    self.setPttInactive()
                } else {
                    self.schedulePttWatchdog()
                }
            }
        pttActiveWatchdog = watchdog
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.pttWatchdogCheckIntervalSec, execute: watchdog)
    }

    private func cancelPttWatchdog() {
        pttActiveWatchdog?.cancel()
        pttActiveWatchdog = nil
    }

    private static let pttStaleTimeoutSec: TimeInterval = 20
    private static let pttWatchdogCheckIntervalSec: TimeInterval = 2
}
