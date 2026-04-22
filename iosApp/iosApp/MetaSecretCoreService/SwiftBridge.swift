//
//  SwiftBridge.swift
//  iosApp
//
//  Created by Dmitry Kuklin on 25.04.2025.
//  Copyright © 2025 orgName. All rights reserved.
//

import Foundation
import UIKit

@objc public class SwiftBridge: NSObject {
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

struct CleanupVerificationResult {
    let keychainCleared: Bool
    let dbCleared: Bool
    let backupCleared: Bool
    let allCleared: Bool
}
