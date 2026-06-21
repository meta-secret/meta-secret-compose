//
//  SwiftBridge.swift
//  iosApp
//
//  Created by Dmitry Kuklin on 25.04.2025.
//  Copyright © 2025 orgName. All rights reserved.
//

import Foundation
import UIKit
import AuthenticationServices
import CryptoKit
import Security

@objcMembers public class SwiftBridge: NSObject {
    // MARK: - MetaSecretCoreBridge API

    @objc public func generateMasterKey() -> String {
        uniffiMobileGenerateMasterKey()
    }

    @objc public func initWithMasterKey(_ masterKey: String) -> String {
        uniffiMobileInitIos(masterKey: masterKey)
    }

    @objc public func initWithMasterKeyAndDevice(
        _ masterKey: String,
        deviceName: String,
        deviceType: String
    ) -> String {
        uniffiMobileInitIosWithDevice(
            masterKey: masterKey,
            deviceName: deviceName,
            deviceType: deviceType
        )
    }

    @objc public func getState() -> String {
        let resultString = uniffiMobileGetState()
        if resultString.isEmpty {
            SwiftLogger.shared.logError(tag: .swiftBridge, message: "getState: FFI returned empty string")
            return "{\"success\": false, \"message\": \"FFI getState returned empty string\"}"
        }
        if !resultString.contains("\"message\"") && !resultString.contains("\"success\"") {
            SwiftLogger.shared.logError(tag: .swiftBridge, message: "getState: FFI returned invalid JSON")
            return "{\"success\": false, \"message\": \"FFI getState returned invalid JSON\"}"
        }
        return resultString
    }

    @objc public func generateUserCreds(vaultName: String) -> String {
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "generateUserCreds with \(vaultName)")
        let resultString = uniffiMobileGenerateUserCreds(vaultName: vaultName)
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "generateUserCreds resultString \(resultString)")
        return resultString
    }

    @objc public func signUp() -> String {
        uniffiMobileSignUp()
    }

    @objc public func updateMembership(_ candidate: String, _ actionUpdate: String) -> String {
        uniffiMobileUpdateMembership(candidate: candidate, actionUpdate: actionUpdate)
    }

    @objc public func splitSecret(_ secretName: String, _ secret: String) -> String {
        uniffiMobileSplitSecret(secretId: secretName, secret: secret)
    }

    @objc public func findClaim(_ secretId: String) -> String {
        uniffiMobileFindClaimBy(secretId: secretId)
    }

    @objc public func findClaimIdBy(_ secretId: String) -> String {
        uniffiMobileFindClaimIdBy(secretId: secretId)
    }

    @objc public func recover(_ secretId: String) -> String {
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "recover secret ID \(secretId)")
        return uniffiMobileRecover(secretId: secretId)
    }

    @objc public func acceptRecover(_ claimId: String) -> String {
        uniffiMobileAcceptRecover(claimId: claimId)
    }

    @objc public func declineRecover(_ claimId: String) -> String {
        uniffiMobileDeclineRecover(claimId: claimId)
    }

    @objc public func sendDeclineCompletion(_ claimId: String) -> String {
        uniffiMobileSendDeclineCompletion(claimId: claimId)
    }

    @objc public func showRecovered(_ secretId: String) -> String {
        uniffiMobileShowRecovered(secretId: secretId)
    }

    // MARK: - KeyChain
    private let serviceName: String = "MetaSecret"
    @MainActor
    fileprivate static var appleEmailAuthDelegate: AppleEmailAuthDelegate?
    @MainActor
    fileprivate static var googleEmailAuthSession: GoogleEmailAuthSession?
    
    @objc public func saveString(key: String, value: String) -> Bool {
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "saveString key \(key) value: \(value)")
        guard let data = value.data(using: .utf8) else {
            return false
        }
        
        if containsKey(key: key) {
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: serviceName,
                kSecAttrAccount as String: key
            ]
            
            let attributes: [String: Any] = [
                kSecValueData as String: data
            ]
            
            let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
            return status == errSecSuccess
        } else {
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: serviceName,
                kSecAttrAccount as String: key,
                kSecValueData as String: data
            ]
            
            let status = SecItemAdd(query as CFDictionary, nil)
            return status == errSecSuccess
        }
    }
    
    @objc public func getString(key: String) -> String? {
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "getString key \(key)")
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnData as String: true
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        guard status == errSecSuccess, let data = result as? Data else {
            return nil
        }
        
        return String(data: data, encoding: .utf8)
    }
    
    @objc public func removeKey(key: String) -> Bool {
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "removeKey key \(key)")
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
    
    @objc public func containsKey(key: String) -> Bool {
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "containsKey key \(key)?")
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnData as String: false
        ]
        
        let status = SecItemCopyMatching(query as CFDictionary, nil)
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "containsKey key \(key) \(status == errSecSuccess)")
        return status == errSecSuccess
    }
    
    @objc public func clearAll(dbFileName: String) -> Bool {
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "clearAll keys - Starting cleanup process")

        _ = uniffiMobileCleanUpDatabase()
        
        cleanDB(dbFileName: dbFileName)
        
        removeBackup(dbFileName: dbFileName)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName
        ]

        let status = SecItemDelete(query as CFDictionary)
        let keychainCleared = status == errSecSuccess || status == errSecItemNotFound

        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "Step 5 - Verifying cleanup")
        let verificationResult = verifyCleanup(dbFileName: dbFileName)
        
        if verificationResult.allCleared {
            SwiftLogger.shared.logSuccess(tag: .swiftBridge, message: "clearAll completed successfully - All data cleared")
        } else {
            SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "⚠️ clearAll completed with warnings - Some data may remain")
            if !verificationResult.keychainCleared {
                SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "⚠️ KeyChain items may still exist")
            }
            if !verificationResult.dbCleared {
                SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "⚠️ Local DB file may still exist")
            }
            if !verificationResult.backupCleared {
                SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "⚠️ Backup files may still exist")
            }
        }
        
        return keychainCleared
    }

    // MARK: - Backuping
    @MainActor
    @objc(presentBackupPickerWithInitialMessage:okTitle:warningMessage:warningOkTitle:warningCancelTitle:backupKey:dbFileName:completion:)
    public func presentBackupPickerWithInitialMessage(
        initialMessage: String,
        okTitle: String,
        warningMessage: String,
        warningOkTitle: String,
        warningCancelTitle: String,
        backupKey: String,
        dbFileName: String,
        completion: @escaping (Bool) -> Void
    ) {
        presentBackupPickerWithMessages(
            initialMessage: initialMessage,
            okTitle: okTitle,
            warningMessage: warningMessage,
            warningOkTitle: warningOkTitle,
            warningCancelTitle: warningCancelTitle,
            backupKey: backupKey,
            dbFileName: dbFileName,
            completion: completion
        )
    }

    @MainActor
    @objc private func presentBackupPickerWithMessages(
        initialMessage: String,
        okTitle: String,
        warningMessage: String,
        warningOkTitle: String,
        warningCancelTitle: String,
        backupKey: String,
        dbFileName: String,
        completion: @escaping (Bool) -> Void = { _ in }
    ) {
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "Present BackUp Alert")
        BackupUI.shared.presentBackupPicker(
            initialMessage: initialMessage,
            okTitle: okTitle,
            warningMessage: warningMessage,
            warningOkTitle: warningOkTitle,
            warningCancelTitle: warningCancelTitle,
            backupKey: backupKey,
            dbFileName: dbFileName,
            completion: completion
        )
    }

    @objc public func restoreBackupIfNeeded(dbFileName: String) {
        BackupWorker.restoreIfNeeded(dbFileName: dbFileName)
    }

    @objc public func backupIfChanged(dbFileName: String) {
        BackupWorker.backupIfChanged(dbFileName: dbFileName)
    }

    @objc public func removeBackup(dbFileName: String) {
        BackupWorker.removeBackup(dbFileName: dbFileName)
    }

    @objc public func hasDatabaseFile(_ dbFileName: String) -> Bool {
        return SwiftBridge.hasLocalDatabaseFile(dbFileName: dbFileName)
    }
    
    static func hasLocalDatabaseFile(dbFileName: String) -> Bool {
        let fileManager = FileManager.default
        guard let documentsPath = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
            return false
        }
        let dbPath = documentsPath.appendingPathComponent(dbFileName)
        return fileManager.fileExists(atPath: dbPath.path)
    }
    
    // MARK: - Other
    @objc public func setiOSLogsVisibility(_ isVisible: Bool) {
        SwiftLogger.shared.updateActive(isVisible)
    }

    @objc public func requestGoogleEmail() -> String {
        return requestGoogleEmailImpl()
    }

    @objc public func requestAppleEmail() -> String {
        return requestAppleEmailImpl()
    }
    
}

private extension SwiftBridge {
    func cleanDB(dbFileName: String) {
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "CleanDB")
        let isExists = SwiftBridge.hasLocalDatabaseFile(dbFileName: dbFileName)

        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "is DB exists: \(isExists)")
        if isExists {
            let fileManager = FileManager.default
            guard let documentsPath = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
                return
            }
            let dbPath = documentsPath.appendingPathComponent(dbFileName)
            _ = try? fileManager.removeItem(at: dbPath)
        }
    }
    
    func verifyCleanup(dbFileName: String) -> CleanupVerificationResult {
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "Verifying cleanup results")
        
        // Check KeyChain
        let keychainCleared = !containsKey(key: "master_key")
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "KeyChain cleared: \(keychainCleared)")
        
        // Check local DB
        let dbCleared = !SwiftBridge.hasLocalDatabaseFile(dbFileName: dbFileName)
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "Local DB cleared: \(dbCleared)")
        
        // Check backups
        let backupCleared = !BackupWorker.hasICloudBackup(dbFileName: dbFileName)
        SwiftLogger.shared.logInfo(tag: .swiftBridge, message: "Backups cleared: \(backupCleared)")
        
        let allCleared = keychainCleared && dbCleared && backupCleared
        
        return CleanupVerificationResult(
            keychainCleared: keychainCleared,
            dbCleared: dbCleared,
            backupCleared: backupCleared,
            allCleared: allCleared
        )
    }

}

extension SwiftBridge {
    // MARK: - Email Auth
    fileprivate func requestGoogleEmailImpl() -> String {
        guard let clientID = (Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String)
            ?? (Bundle.main.object(forInfoDictionaryKey: "GOOGLE_CLIENT_ID") as? String),
              !clientID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return Self.encodeEmailResponse(kind: "error", message: "Google client ID is not configured")
        }
        return Self.requestGoogleEmailOAuthImpl(clientID: clientID)
    }

    fileprivate func requestAppleEmailImpl() -> String {
        let semaphore = DispatchSemaphore(value: 0)
        let result = EmailAuthResultBox(Self.encodeEmailResponse(kind: "error", message: "Apple sign-in failed"))
        let provider = ASAuthorizationAppleIDProvider()
        let request = provider.createRequest()
        request.requestedScopes = [.email]

        Task { @MainActor in
            let delegate = AppleEmailAuthDelegate(completion: { value in
                result.value = value
                semaphore.signal()
            })
            SwiftBridge.appleEmailAuthDelegate = delegate

            let authorizationController = ASAuthorizationController(authorizationRequests: [request])
            authorizationController.delegate = delegate
            authorizationController.presentationContextProvider = delegate
            authorizationController.performRequests()
        }

        semaphore.wait()
        return result.value
    }

    fileprivate static func requestGoogleEmailOAuthImpl(clientID: String) -> String {
        let semaphore = DispatchSemaphore(value: 0)
        let result = EmailAuthResultBox(Self.encodeEmailResponse(kind: "error", message: "Google sign-in failed"))

        Task { @MainActor in
            let redirectScheme = (Bundle.main.object(forInfoDictionaryKey: "GOOGLE_REVERSED_CLIENT_ID") as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

            guard !redirectScheme.isEmpty else {
                result.value = Self.encodeEmailResponse(kind: "error", message: "Google redirect scheme is not configured")
                semaphore.signal()
                return
            }

            let session = GoogleEmailAuthSession(
                clientID: clientID,
                redirectScheme: redirectScheme
            ) { value in
                result.value = value
                semaphore.signal()
            }
            SwiftBridge.googleEmailAuthSession = session
            session.start()
        }

        semaphore.wait()
        return result.value
    }

    @MainActor
    fileprivate static func topMostViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let keyWindow = scenes
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        var topController = keyWindow?.rootViewController
        while let presented = topController?.presentedViewController {
            topController = presented
        }
        return topController
    }

    fileprivate static func encodeEmailResponse(kind: String, email: String? = nil, message: String? = nil) -> String {
        let payload = EmailAuthResponse(kind: kind, email: email, message: message)
        guard let data = try? JSONEncoder().encode(payload),
              let json = String(data: data, encoding: .utf8) else {
            return "{\"kind\":\"error\",\"message\":\"Unable to encode response\"}"
        }
        return json
    }
}

@MainActor
private final class GoogleEmailAuthSession: NSObject, ASWebAuthenticationPresentationContextProviding {
    private let clientID: String
    private let redirectScheme: String
    private let completion: (String) -> Void
    private let codeVerifier: String
    private var session: ASWebAuthenticationSession?

    init(clientID: String, redirectScheme: String, completion: @escaping (String) -> Void) {
        self.clientID = clientID
        self.redirectScheme = redirectScheme
        self.completion = completion
        self.codeVerifier = GoogleEmailAuthSession.makeCodeVerifier()
    }

    func start() {
        guard let authURL = Self.makeAuthorizationURL(
            clientID: clientID,
            redirectScheme: redirectScheme,
            codeVerifier: codeVerifier
        ) else {
            completion(SwiftBridge.encodeEmailResponse(kind: "error", message: "Unable to build Google authorization URL"))
            SwiftBridge.googleEmailAuthSession = nil
            return
        }

        let session = ASWebAuthenticationSession(
            url: authURL,
            callbackURLScheme: redirectScheme
        ) { [weak self] callbackURL, error in
            guard let self else {
                return
            }

            Task { @MainActor in
                self.handleCallback(callbackURL: callbackURL, error: error)
            }
        }
        session.presentationContextProvider = self
        session.prefersEphemeralWebBrowserSession = false
        self.session = session

        if !session.start() {
            completion(SwiftBridge.encodeEmailResponse(kind: "error", message: "Unable to start Google authorization session"))
            SwiftBridge.googleEmailAuthSession = nil
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        SwiftBridge.topMostViewController()?.view.window ?? ASPresentationAnchor()
    }

    private func handleCallback(callbackURL: URL?, error: Error?) {
        defer {
            SwiftBridge.googleEmailAuthSession = nil
        }

        if let error = error as? ASWebAuthenticationSessionError,
           error.code == .canceledLogin {
            completion(SwiftBridge.encodeEmailResponse(kind: "cancelled", message: "Cancelled by user"))
            return
        }

        if let error = error {
            completion(SwiftBridge.encodeEmailResponse(kind: "error", message: error.localizedDescription))
            return
        }

        guard let callbackURL = callbackURL,
              let code = Self.authorizationCode(from: callbackURL) else {
            completion(SwiftBridge.encodeEmailResponse(kind: "error", message: "Google did not return an authorization code"))
            return
        }

        Task {
            let response = await Self.exchangeAuthorizationCode(
                code: code,
                clientID: clientID,
                redirectScheme: redirectScheme,
                codeVerifier: codeVerifier
            )
            completion(response)
        }
    }

    private static func makeAuthorizationURL(
        clientID: String,
        redirectScheme: String,
        codeVerifier: String
    ) -> URL? {
        let redirectURI = "\(redirectScheme):/oauth2redirect"
        let codeChallenge = makeCodeChallenge(for: codeVerifier)

        var components = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")
        components?.queryItems = [
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid email profile"),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "prompt", value: "select_account"),
            URLQueryItem(name: "access_type", value: "offline")
        ]
        return components?.url
    }

    private static func authorizationCode(from callbackURL: URL) -> String? {
        let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)
        return components?.queryItems?.first(where: { $0.name == "code" })?.value
    }

    private static func exchangeAuthorizationCode(
        code: String,
        clientID: String,
        redirectScheme: String,
        codeVerifier: String
    ) async -> String {
        do {
            let redirectURI = "\(redirectScheme):/oauth2redirect"
            guard let tokenURL = URL(string: "https://oauth2.googleapis.com/token") else {
                return SwiftBridge.encodeEmailResponse(kind: "error", message: "Unable to build Google token endpoint URL")
            }

            var request = URLRequest(url: tokenURL)
            request.httpMethod = "POST"
            request.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
            request.httpBody = Self.formURLEncodedBody([
                "client_id": clientID,
                "code": code,
                "code_verifier": codeVerifier,
                "grant_type": "authorization_code",
                "redirect_uri": redirectURI
            ])

            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                let body = String(data: data, encoding: .utf8) ?? "Google token exchange failed"
                return SwiftBridge.encodeEmailResponse(kind: "error", message: body)
            }

            guard 200..<300 ~= httpResponse.statusCode else {
                let body = String(data: data, encoding: .utf8) ?? "Google token exchange failed"
                return SwiftBridge.encodeEmailResponse(kind: "error", message: body)
            }

            let tokenResponse = try JSONDecoder().decode(GoogleOAuthTokenResponse.self, from: data)
            if let email = Self.email(fromIDToken: tokenResponse.idToken) {
                return SwiftBridge.encodeEmailResponse(kind: "success", email: email)
            }

            if let accessToken = tokenResponse.accessToken,
               let email = try await Self.emailFromUserInfo(accessToken: accessToken) {
                return SwiftBridge.encodeEmailResponse(kind: "success", email: email)
            }

            return SwiftBridge.encodeEmailResponse(kind: "error", message: "Google did not return an email address")
        } catch {
            return SwiftBridge.encodeEmailResponse(kind: "error", message: error.localizedDescription)
        }
    }

    private static func emailFromUserInfo(accessToken: String) async throws -> String? {
        guard let userInfoURL = URL(string: "https://openidconnect.googleapis.com/v1/userinfo") else {
            return nil
        }

        var request = URLRequest(url: userInfoURL)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              200..<300 ~= httpResponse.statusCode else {
            return nil
        }

        let json = try JSONSerialization.jsonObject(with: data, options: [])
        let dictionary = json as? [String: Any]
        let email = dictionary?["email"] as? String ?? ""
        return email.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func email(fromIDToken idToken: String?) -> String? {
        guard let idToken, !idToken.isEmpty else {
            return nil
        }

        let segments = idToken.split(separator: ".")
        guard segments.count >= 2 else {
            return nil
        }

        let payloadSegment = String(segments[1])
        guard let payloadData = Data(base64URLEncoded: payloadSegment),
              let payload = try? JSONSerialization.jsonObject(with: payloadData, options: []),
              let dictionary = payload as? [String: Any],
              let email = dictionary["email"] as? String else {
            return nil
        }

        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func makeCodeVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        if status != errSecSuccess {
            return UUID().uuidString.replacingOccurrences(of: "-", with: "") + UUID().uuidString.replacingOccurrences(of: "-", with: "")
        }

        return Data(bytes).base64URLEncodedString()
    }

    private static func makeCodeChallenge(for verifier: String) -> String {
        let digest = SHA256.hash(data: Data(verifier.utf8))
        return Data(digest).base64URLEncodedString()
    }

    private static func formURLEncodedBody(_ values: [String: String]) -> Data? {
        let body = values
            .sorted(by: { $0.key < $1.key })
            .map { key, value in
                "\(key.urlEncoded())=\(value.urlEncoded())"
            }
            .joined(separator: "&")
        return body.data(using: .utf8)
    }
}

private struct GoogleOAuthTokenResponse: Decodable {
    let accessToken: String?
    let idToken: String?

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case idToken = "id_token"
    }
}

@MainActor
private final class AppleEmailAuthDelegate: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private let completion: (String) -> Void

    init(completion: @escaping (String) -> Void) {
        self.completion = completion
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            completion(SwiftBridge.encodeEmailResponse(kind: "error", message: "Apple did not return a valid credential"))
            SwiftBridge.appleEmailAuthDelegate = nil
            return
        }

        let email = credential.email?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if email.isEmpty {
            completion(SwiftBridge.encodeEmailResponse(kind: "error", message: "Apple did not return an email address"))
            SwiftBridge.appleEmailAuthDelegate = nil
            return
        }

        if email.lowercased().hasSuffix("@privaterelay.appleid.com") {
            completion(SwiftBridge.encodeEmailResponse(kind: "private_relay", email: email))
            SwiftBridge.appleEmailAuthDelegate = nil
            return
        }

        completion(SwiftBridge.encodeEmailResponse(kind: "success", email: email))
        SwiftBridge.appleEmailAuthDelegate = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let nsError = error as NSError
        if nsError.domain == ASAuthorizationError.errorDomain, nsError.code == ASAuthorizationError.canceled.rawValue {
            completion(SwiftBridge.encodeEmailResponse(kind: "cancelled", message: "Cancelled by user"))
        } else {
            completion(SwiftBridge.encodeEmailResponse(kind: "error", message: error.localizedDescription))
        }
        SwiftBridge.appleEmailAuthDelegate = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        SwiftBridge.topMostViewController()?.view.window ?? ASPresentationAnchor()
    }
}

private final class EmailAuthResultBox: @unchecked Sendable {
    var value: String

    init(_ value: String) {
        self.value = value
    }
}

private struct EmailAuthResponse: Codable {
    let kind: String
    let email: String?
    let message: String?
}

private extension Data {
    init?(base64URLEncoded string: String) {
        var base64 = string.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let padding = base64.count % 4
        if padding > 0 {
            base64 += String(repeating: "=", count: 4 - padding)
        }
        self.init(base64Encoded: base64)
    }

    func base64URLEncodedString() -> String {
        self.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

private extension String {
    func urlEncoded() -> String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
        return addingPercentEncoding(withAllowedCharacters: allowed) ?? self
    }
}

struct CleanupVerificationResult {
    let keychainCleared: Bool
    let dbCleared: Bool
    let backupCleared: Bool
    let allCleared: Bool
}
