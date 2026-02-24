import Foundation

class Preferences {
    static let shared = Preferences()

    private let defaults = UserDefaults.standard

    var autoEnter: Bool {
        get { defaults.bool(forKey: "autoEnter") }
        set { defaults.set(newValue, forKey: "autoEnter") }
    }

    var copyToClipboard: Bool {
        get { defaults.bool(forKey: "copyToClipboard") }
        set { defaults.set(newValue, forKey: "copyToClipboard") }
    }
}
