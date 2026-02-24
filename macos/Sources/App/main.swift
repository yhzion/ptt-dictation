import AppKit
import Foundation

// Kill any existing instance before launching
let currentPID = ProcessInfo.processInfo.processIdentifier
let processName = ProcessInfo.processInfo.processName
let task = Process()
task.executableURL = URL(fileURLWithPath: "/usr/bin/pgrep")
task.arguments = ["-x", processName]
let pipe = Pipe()
task.standardOutput = pipe
try? task.run()
task.waitUntilExit()
let output = String(data: pipe.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? ""
for line in output.split(separator: "\n") {
    if let pid = Int32(line), pid != currentPID {
        kill(pid, SIGTERM)
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.run()
