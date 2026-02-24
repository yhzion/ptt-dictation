import AppKit
import Carbon.HIToolbox

protocol TextInjector {
    func inject(text: String)
}

class ClipboardTextInjector: TextInjector {
    private let pasteboard = NSPasteboard.general

    func inject(text: String) {
        guard !text.isEmpty else { return }

        // 1. Backup clipboard
        let backup = pasteboard.string(forType: .string)

        // 2. Set text to clipboard
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)

        // 3. Simulate Cmd+V
        let source = CGEventSource(stateID: .hidSystemState)
        let keyDown = CGEvent(keyboardEventSource: source, virtualKey: CGKeyCode(kVK_ANSI_V), keyDown: true)
        let keyUp = CGEvent(keyboardEventSource: source, virtualKey: CGKeyCode(kVK_ANSI_V), keyDown: false)
        keyDown?.flags = .maskCommand
        keyUp?.flags = .maskCommand
        keyDown?.post(tap: .cghidEventTap)
        keyUp?.post(tap: .cghidEventTap)

        // 4. Restore clipboard after paste completes
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
            guard let self else { return }
            self.pasteboard.clearContents()
            if let backup {
                self.pasteboard.setString(backup, forType: .string)
            }
        }
    }
}
