import XCTest
@testable import PttDictation

final class PreferencesRegistryTests: XCTestCase {
    private struct DummyFeature: PreferencesFeature {
        let id: String
        let order: Int
        let title: String

        func makeSection(store: PreferencesStore) -> PreferenceSection? {
            PreferenceSection(
                id: id,
                title: title,
                rows: [
                    .note(
                        PreferenceNoteRow(
                            id: "\(id)-note",
                            text: "dummy",
                        )
                    )
                ]
            )
        }
    }

    private func makeStore() -> PreferencesStore {
        let suiteName = "PreferencesRegistryTests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        return PreferencesStore(defaults: defaults)
    }

    func testSectionsAreSortedByOrderThenID() {
        let registry = PreferencesRegistry(
            features: [
                DummyFeature(id: "beta", order: 200, title: "Beta"),
                DummyFeature(id: "alpha", order: 100, title: "Alpha"),
                DummyFeature(id: "charlie", order: 200, title: "Charlie"),
            ]
        )

        let ids = registry.sections(store: makeStore()).map(\.id)

        XCTAssertEqual(ids, ["alpha", "beta", "charlie"])
    }

    func testRegisterReplacesFeatureWithSameID() {
        let registry = PreferencesRegistry(
            features: [
                DummyFeature(id: "general", order: 100, title: "Old"),
            ]
        )

        registry.register(DummyFeature(id: "general", order: 50, title: "New"))

        let sections = registry.sections(store: makeStore())

        XCTAssertEqual(sections.count, 1)
        XCTAssertEqual(sections.first?.title, "New")
    }

    func testStoreDefaultValues() {
        let store = makeStore()

        XCTAssertFalse(store.copyToClipboard)
        XCTAssertTrue(store.showStatusDot)
        XCTAssertTrue(store.showConnectionDetails)
    }

    func testCategoryFeaturesCanBeComposed() {
        let registry = PreferencesRegistry(
            features: [
                GeneralPreferencesFeature(),
                ConnectionPreferencesFeature(),
                StartupPreferencesFeature(),
            ]
        )

        let ids = registry.sections(store: makeStore()).map(\.id)

        XCTAssertEqual(ids, ["general", "connection", "startup"])
    }
}
