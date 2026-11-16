import Foundation

enum E2ETestIdentityConfigurationError: Error, Equatable {
    case invalidBuildMarker
    case namespaceOnUnmarkedBuild
    case simulatorRequired
    case invalidNamespace
}

func resolveE2ETestIdentity(
    marker: Any?,
    namespace: String?,
    isSimulator: Bool
) throws -> String? {
    let mode = marker as? String
    guard marker == nil || mode == "NO" || mode == "YES" else {
        throw E2ETestIdentityConfigurationError.invalidBuildMarker
    }
    guard mode == "YES" else {
        guard namespace == nil else {
            throw E2ETestIdentityConfigurationError.namespaceOnUnmarkedBuild
        }
        return nil
    }
    guard isSimulator else {
        throw E2ETestIdentityConfigurationError.simulatorRequired
    }
    guard let namespace, namespace.hasPrefix("f2-"),
          let uuid = UUID(uuidString: String(namespace.dropFirst(3))),
          namespace == "f2-" + uuid.uuidString.lowercased() else {
        throw E2ETestIdentityConfigurationError.invalidNamespace
    }
    return "MetaSecret.E2E." + namespace
}
