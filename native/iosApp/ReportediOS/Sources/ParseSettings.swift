import Foundation

enum ParseSettings {
    private static let bundled: [String: String] = {
        guard let url = Bundle.main.url(forResource: "ParseConfig", withExtension: "plist"),
              let data = try? Data(contentsOf: url),
              let values = try? PropertyListDecoder().decode([String: String].self, from: data)
        else { return [:] }
        return values
    }()

    static func value(_ key: String) -> String {
        let value = ProcessInfo.processInfo.environment[key] ?? bundled[key] ?? ""
        precondition(!value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                     "Missing Parse configuration: \(key). Configure it before building.")
        return value
    }
}
