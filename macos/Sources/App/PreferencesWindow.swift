import AppKit

class PreferencesWindow: NSWindow {
    private let clipboardCheckbox = NSButton(checkboxWithTitle: "Copy to clipboard", target: nil, action: nil)

    init() {
        super.init(
            contentRect: NSRect(x: 0, y: 0, width: 300, height: 70),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )

        title = "Preferences"
        isReleasedWhenClosed = false
        center()

        let contentView = NSView(frame: NSRect(x: 0, y: 0, width: 300, height: 70))

        clipboardCheckbox.frame = NSRect(x: 20, y: 25, width: 260, height: 20)
        clipboardCheckbox.state = Preferences.shared.copyToClipboard ? .on : .off
        clipboardCheckbox.target = self
        clipboardCheckbox.action = #selector(clipboardToggled)
        contentView.addSubview(clipboardCheckbox)

        self.contentView = contentView
    }

    @objc private func clipboardToggled() {
        Preferences.shared.copyToClipboard = clipboardCheckbox.state == .on
    }
}
