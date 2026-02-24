import AppKit

class PreferencesWindow: NSWindow {
    private let autoEnterCheckbox = NSButton(checkboxWithTitle: "Auto Enter after dictation", target: nil, action: nil)

    init() {
        super.init(
            contentRect: NSRect(x: 0, y: 0, width: 300, height: 80),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )

        title = "Preferences"
        isReleasedWhenClosed = false
        center()

        let contentView = NSView(frame: NSRect(x: 0, y: 0, width: 300, height: 80))

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
}
