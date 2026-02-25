import AppKit

final class PreferencesWindow: NSWindow {
    private let store: PreferencesStore
    private let registry: PreferencesRegistry
    private var toggleHandlers: [ObjectIdentifier: (Bool) -> Void] = [:]

    init(
        store: PreferencesStore = .shared,
        registry: PreferencesRegistry = .shared,
    ) {
        self.store = store
        self.registry = registry

        super.init(
            contentRect: NSRect(x: 0, y: 0, width: 440, height: 320),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )

        title = "Preferences"
        isReleasedWhenClosed = false
        minSize = NSSize(width: 380, height: 240)
        center()

        buildContent()
    }

    private func buildContent() {
        let sections = registry.sections(store: store)

        let root = NSView(frame: NSRect(x: 0, y: 0, width: 440, height: 320))
        let scrollView = NSScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.drawsBackground = false
        scrollView.borderType = .noBorder
        scrollView.hasVerticalScroller = true

        let stack = NSStackView()
        stack.orientation = .vertical
        stack.alignment = .leading
        stack.distribution = .gravityAreas
        stack.spacing = 14
        stack.edgeInsets = NSEdgeInsets(top: 16, left: 16, bottom: 16, right: 16)

        for (index, section) in sections.enumerated() {
            if index > 0 {
                stack.addArrangedSubview(makeSeparator())
            }

            stack.addArrangedSubview(makeSectionTitle(section.title))
            for row in section.rows {
                stack.addArrangedSubview(makeRowView(row))
            }
        }

        let fitting = stack.fittingSize
        stack.frame = NSRect(origin: .zero, size: NSSize(width: max(380, fitting.width), height: fitting.height))
        scrollView.documentView = stack

        root.addSubview(scrollView)
        NSLayoutConstraint.activate([
            scrollView.leadingAnchor.constraint(equalTo: root.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: root.trailingAnchor),
            scrollView.topAnchor.constraint(equalTo: root.topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: root.bottomAnchor),
        ])

        contentView = root
    }

    private func makeSectionTitle(_ title: String) -> NSTextField {
        let label = NSTextField(labelWithString: title)
        label.font = .boldSystemFont(ofSize: 13)
        label.textColor = .labelColor
        return label
    }

    private func makeSeparator() -> NSView {
        let separator = NSBox()
        separator.boxType = .separator
        separator.translatesAutoresizingMaskIntoConstraints = false
        separator.widthAnchor.constraint(equalToConstant: 380).isActive = true
        return separator
    }

    private func makeRowView(_ row: PreferenceRow) -> NSView {
        switch row {
        case .toggle(let toggle):
            return makeToggleRowView(toggle)
        case .note(let note):
            let label = NSTextField(wrappingLabelWithString: note.text)
            label.font = .systemFont(ofSize: 11)
            label.textColor = .secondaryLabelColor
            label.maximumNumberOfLines = 3
            return label
        }
    }

    private func makeToggleRowView(_ row: PreferenceToggleRow) -> NSView {
        let checkbox = NSButton(checkboxWithTitle: row.title, target: self, action: #selector(toggleChanged(_:)))
        checkbox.state = row.isOn() ? .on : .off
        toggleHandlers[ObjectIdentifier(checkbox)] = row.set

        guard let subtitle = row.subtitle, !subtitle.isEmpty else {
            return checkbox
        }

        let container = NSStackView()
        container.orientation = .vertical
        container.alignment = .leading
        container.spacing = 2
        container.addArrangedSubview(checkbox)

        let subtitleLabel = NSTextField(wrappingLabelWithString: subtitle)
        subtitleLabel.font = .systemFont(ofSize: 11)
        subtitleLabel.textColor = .secondaryLabelColor
        subtitleLabel.maximumNumberOfLines = 2
        container.addArrangedSubview(subtitleLabel)

        return container
    }

    @objc private func toggleChanged(_ sender: NSButton) {
        let key = ObjectIdentifier(sender)
        guard let handler = toggleHandlers[key] else { return }
        handler(sender.state == .on)
    }
}
