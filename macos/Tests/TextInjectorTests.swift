import XCTest
@testable import PttDictation

final class TextInjectorTests: XCTestCase {
    func testProtocolExists() {
        let mock = MockTextInjector()
        mock.inject(text: "테스트")
        XCTAssertEqual(mock.lastInjectedText, "테스트")
    }

    func testEmptyTextIsIgnored() {
        let mock = MockTextInjector()
        mock.inject(text: "")
        XCTAssertNil(mock.lastInjectedText)
    }
}

class MockTextInjector: TextInjector {
    var lastInjectedText: String?

    func inject(text: String) {
        guard !text.isEmpty else { return }
        lastInjectedText = text
    }
}
