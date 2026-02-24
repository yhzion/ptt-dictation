import AppKit

class PreferencesWindow: NSWindow {
    private let autoEnterCheckbox = NSButton(checkboxWithTitle: "Auto Enter after dictation", target: nil, action: nil)
    private let clipboardCheckbox = NSButton(checkboxWithTitle: "Copy to clipboard", target: nil, action: nil)

    init() {
        super.init(
            contentRect: NSRect(x: 0, y: 0, width: 300, height: 110),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )

        title = "Preferences"
        isReleasedWhenClosed = false
        center()

        let contentView = NSView(frame: NSRect(x: 0, y: 0, width: 300, height: 110))

        clipboardCheckbox.frame = NSRect(x: 20, y: 60, width: 260, height: 20)
        clipboardCheckbox.state = Preferences.shared.copyToClipboard ? .on : .off
        clipboardCheckbox.target = self
        clipboardCheckbox.action = #selector(clipboardToggled)
        contentView.addSubview(clipboardCheckbox)

        autoEnterCheckbox.frame = NSRect(x: 20, y: 30, width: 260, height: 20)
        autoEnterCheckbox.state = Preferences.shared.autoEnter ? .on : .off
        autoEnterCheckbox.target = self
        autoEnterCheckbox.action = #selector(autoEnterToggled)
        contentView.addSubview(autoEnterCheckbox)

        self.contentView = contentView
    }

    @objc private func autoEnterToggled() {
        Preferences.shared.autoEnter = autoEnterCheckbox.state == .on
    }

    @objc private func clipboardToggled() {
        Preferences.shared.copyToClipboard = clipboardCheckbox.state == .on
    }
}
