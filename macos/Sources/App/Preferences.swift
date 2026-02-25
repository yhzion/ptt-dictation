import Foundation
import ServiceManagement

extension Notification.Name {
    static let preferencesDidChange = Notification.Name("PreferencesStore.didChange")
}

enum PreferenceKeys {
    static let copyToClipboard = "preferences.copyToClipboard"
    static let showStatusDot = "preferences.showStatusDot"
    static let showConnectionDetails = "preferences.showConnectionDetails"
}

final class PreferencesStore {
    static let shared = PreferencesStore()

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.defaults.register(defaults: [
            PreferenceKeys.copyToClipboard: false,
            PreferenceKeys.showStatusDot: true,
            PreferenceKeys.showConnectionDetails: true,
        ])
    }

    var copyToClipboard: Bool {
        get { defaults.bool(forKey: PreferenceKeys.copyToClipboard) }
        set {
            let old = defaults.bool(forKey: PreferenceKeys.copyToClipboard)
            defaults.set(newValue, forKey: PreferenceKeys.copyToClipboard)
            if old != newValue {
                notifyDidChange(key: PreferenceKeys.copyToClipboard)
            }
        }
    }

    var showStatusDot: Bool {
        get { defaults.bool(forKey: PreferenceKeys.showStatusDot) }
        set {
            let old = defaults.bool(forKey: PreferenceKeys.showStatusDot)
            defaults.set(newValue, forKey: PreferenceKeys.showStatusDot)
            if old != newValue {
                notifyDidChange(key: PreferenceKeys.showStatusDot)
            }
        }
    }

    var launchAtLogin: Bool {
        get { SMAppService.mainApp.status == .enabled }
        set {
            do {
                if newValue {
                    try SMAppService.mainApp.register()
                } else {
                    try SMAppService.mainApp.unregister()
                }
                notifyDidChange(key: "launchAtLogin")
            } catch {
                NSLog("Failed to update launch-at-login setting: \(error)")
            }
        }
    }

    var showConnectionDetails: Bool {
        get { defaults.bool(forKey: PreferenceKeys.showConnectionDetails) }
        set {
            let old = defaults.bool(forKey: PreferenceKeys.showConnectionDetails)
            defaults.set(newValue, forKey: PreferenceKeys.showConnectionDetails)
            if old != newValue {
                notifyDidChange(key: PreferenceKeys.showConnectionDetails)
            }
        }
    }

    private func notifyDidChange(key: String) {
        NotificationCenter.default.post(
            name: .preferencesDidChange,
            object: self,
            userInfo: ["key": key],
        )
    }
}

final class Preferences {
    static let shared = Preferences()

    private let store: PreferencesStore

    init(store: PreferencesStore = .shared) {
        self.store = store
    }

    var copyToClipboard: Bool {
        get { store.copyToClipboard }
        set { store.copyToClipboard = newValue }
    }

    var launchAtLogin: Bool {
        get { store.launchAtLogin }
        set { store.launchAtLogin = newValue }
    }

    var showStatusDot: Bool {
        get { store.showStatusDot }
        set { store.showStatusDot = newValue }
    }

    var showConnectionDetails: Bool {
        get { store.showConnectionDetails }
        set { store.showConnectionDetails = newValue }
    }
}

struct PreferenceSection {
    let id: String
    let title: String
    let rows: [PreferenceRow]
}

enum PreferenceRow {
    case toggle(PreferenceToggleRow)
    case note(PreferenceNoteRow)
}

struct PreferenceToggleRow {
    let id: String
    let title: String
    let subtitle: String?
    let isOn: () -> Bool
    let set: (Bool) -> Void
}

struct PreferenceNoteRow {
    let id: String
    let text: String
}

protocol PreferencesFeature {
    var id: String { get }
    var order: Int { get }
    func makeSection(store: PreferencesStore) -> PreferenceSection?
}

final class PreferencesRegistry {
    static let shared = PreferencesRegistry(features: defaultFeatures())

    private var features: [PreferencesFeature]

    init(features: [PreferencesFeature] = []) {
        self.features = features
    }

    func register(_ feature: PreferencesFeature) {
        features.removeAll { $0.id == feature.id }
        features.append(feature)
    }

    func sections(store: PreferencesStore = .shared) -> [PreferenceSection] {
        features
            .sorted {
                if $0.order == $1.order {
                    return $0.id < $1.id
                }
                return $0.order < $1.order
            }
            .compactMap { $0.makeSection(store: store) }
    }

    private static func defaultFeatures() -> [PreferencesFeature] {
        [
            GeneralPreferencesFeature(),
            ConnectionPreferencesFeature(),
            StartupPreferencesFeature(),
        ]
    }
}

struct GeneralPreferencesFeature: PreferencesFeature {
    let id = "general"
    let order = 100

    func makeSection(store: PreferencesStore) -> PreferenceSection? {
        PreferenceSection(
            id: id,
            title: "General",
            rows: [
                .toggle(
                    PreferenceToggleRow(
                        id: "copy-to-clipboard",
                        title: "Keep dictated text in clipboard",
                        subtitle: "If disabled, clipboard is restored after paste.",
                        isOn: { store.copyToClipboard },
                        set: { store.copyToClipboard = $0 },
                    ),
                ),
                .toggle(
                    PreferenceToggleRow(
                        id: "show-status-dot",
                        title: "Show status color dot in menu bar",
                        subtitle: "Red while listening, green while connected.",
                        isOn: { store.showStatusDot },
                        set: { store.showStatusDot = $0 },
                    ),
                ),
            ],
        )
    }
}

struct ConnectionPreferencesFeature: PreferencesFeature {
    let id = "connection"
    let order = 150

    func makeSection(store: PreferencesStore) -> PreferenceSection? {
        PreferenceSection(
            id: id,
            title: "Connection",
            rows: [
                .toggle(
                    PreferenceToggleRow(
                        id: "show-connection-details",
                        title: "Show speech engine in connection status",
                        subtitle: "Adds the current Android engine label next to the device model.",
                        isOn: { store.showConnectionDetails },
                        set: { store.showConnectionDetails = $0 },
                    ),
                ),
            ],
        )
    }
}

struct StartupPreferencesFeature: PreferencesFeature {
    let id = "startup"
    let order = 200

    func makeSection(store: PreferencesStore) -> PreferenceSection? {
        PreferenceSection(
            id: id,
            title: "Startup",
            rows: [
                .toggle(
                    PreferenceToggleRow(
                        id: "launch-at-login",
                        title: "Launch at login",
                        subtitle: nil,
                        isOn: { store.launchAtLogin },
                        set: { store.launchAtLogin = $0 },
                    ),
                ),
                .note(
                    PreferenceNoteRow(
                        id: "startup-note",
                        text: "More features can append settings via PreferencesRegistry.register(_:).",
                    ),
                ),
            ],
        )
    }
}
