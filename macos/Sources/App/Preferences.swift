import Foundation

class Preferences {
    static let shared = Preferences()

    private let defaults = UserDefaults.standard

    var autoEnter: Bool {
        get { defaults.bool(forKey: "autoEnter") }
        set { defaults.set(newValue, forKey: "autoEnter") }
    }
}
