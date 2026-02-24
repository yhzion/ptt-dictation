import Foundation
import ServiceManagement

class Preferences {
    static let shared = Preferences()

    private let defaults = UserDefaults.standard

    var copyToClipboard: Bool {
        get { defaults.bool(forKey: "copyToClipboard") }
        set { defaults.set(newValue, forKey: "copyToClipboard") }
    }

    var launchAtLogin: Bool {
        get { SMAppService.mainApp.status == .enabled }
        set {
            if newValue {
                try? SMAppService.mainApp.register()
            } else {
                try? SMAppService.mainApp.unregister()
            }
        }
    }
}
