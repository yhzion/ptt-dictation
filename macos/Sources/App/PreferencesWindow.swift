import AppKit

class PreferencesWindow: NSWindow {
    private let clipboardCheckbox = NSButton(checkboxWithTitle: "Copy to clipboard", target: nil, action: nil)
    private let launchAtLoginCheckbox = NSButton(checkboxWithTitle: "Launch at login", target: nil, action: nil)

    init() {
        super.init(
            contentRect: NSRect(x: 0, y: 0, width: 300, height: 100),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )

        title = "Preferences"
        isReleasedWhenClosed = false
        center()

        let contentView = NSView(frame: NSRect(x: 0, y: 0, width: 300, height: 100))

        clipboardCheckbox.frame = NSRect(x: 20, y: 25, width: 260, height: 20)
        clipboardCheckbox.state = Preferences.shared.copyToClipboard ? .on : .off
        clipboardCheckbox.target = self
        clipboardCheckbox.action = #selector(clipboardToggled)
        contentView.addSubview(clipboardCheckbox)

        launchAtLoginCheckbox.frame = NSRect(x: 20, y: 55, width: 260, height: 20)
        launchAtLoginCheckbox.state = Preferences.shared.launchAtLogin ? .on : .off
        launchAtLoginCheckbox.target = self
        launchAtLoginCheckbox.action = #selector(launchAtLoginToggled)
        contentView.addSubview(launchAtLoginCheckbox)

        self.contentView = contentView
    }

    @objc private func clipboardToggled() {
        Preferences.shared.copyToClipboard = clipboardCheckbox.state == .on
    }

    @objc private func launchAtLoginToggled() {
        Preferences.shared.launchAtLogin = launchAtLoginCheckbox.state == .on
    }
}
