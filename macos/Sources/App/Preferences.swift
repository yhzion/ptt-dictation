import Foundation

class Preferences {
    static let shared = Preferences()

    private let defaults = UserDefaults.standard

    var copyToClipboard: Bool {
        get { defaults.bool(forKey: "copyToClipboard") }
        set { defaults.set(newValue, forKey: "copyToClipboard") }
    }
}
