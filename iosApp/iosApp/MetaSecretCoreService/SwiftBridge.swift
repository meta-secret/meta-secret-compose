//
//  SwiftBridge.swift
//  iosApp
//
//  Created by Dmitry Kuklin on 25.04.2025.
//  Copyright © 2025 orgName. All rights reserved.
//

import Foundation
import UIKit
import ObjectiveC

@objc public class SwiftBridge: NSObject {
    // MARK: - MetaSecretCoreBridge API

    @_silgen_name("generate_master_key")
    private func c_generate_master_key() -> UnsafeMutablePointer<CChar>?

    @_silgen_name("init")
    private func c_init(_ master_key_ptr: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>?
    
    @_silgen_name("get_state")
    private func c_get_state() -> UnsafeMutablePointer<CChar>?
    
    @_silgen_name("sign_up")
    private func c_sign_up() -> UnsafeMutablePointer<CChar>?

    @_silgen_name("free_string")
    private func c_free_string(_ ptr: UnsafeMutablePointer<CChar>?)
    
    @_silgen_name("generate_user_creds")
    private func c_generate_user_creds(_ vault_name_ptr: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>?

    @_silgen_name("update_membership")
    private func c_update_memberships(_ candidate_ptr: UnsafePointer<CChar>?, _ action_update_ptr: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>?

    @_silgen_name("clean_up_database")
    private func c_clean_up_database() -> UnsafeMutablePointer<CChar>?
    
    @_silgen_name("split_secret")
    private func c_split_secret(_ secret_id_ptr: UnsafePointer<CChar>?, _ secret_ptr: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>?
    
    @_silgen_name("find_claim_by")
    private func c_find_claim_by(_ secret_id_ptr: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>?
    
    @_silgen_name("recover")
    private func c_recover(_ secret_id_ptr: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>?
    
    @_silgen_name("accept_recover")
    private func c_accept_recover(_ claim_id_ptr: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>?
    
    @_silgen_name("show_recovered")
    private func c_show_recovered(_ secret_id_ptr: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>?
    
    // MARK: - MetaSecretCoreBridge
    @objc public func generateMasterKey() -> String {
        guard let cString = c_generate_master_key() else {
            return ""
        }
        
        let swiftString = String(cString: cString)
        c_free_string(cString)
        return swiftString
    }

    @objc public func initWithMasterKey(_ masterKey: String) -> String {
        guard let cString = masterKey.cString(using: .utf8) else {
            return ""
        }
 
        guard let resultPtr = c_init(cString) else {
            return ""
        }
        
        let resultString = String(cString: resultPtr)
        c_free_string(resultPtr)
        return resultString
    }
    
    @objc public func getState() -> String {
        guard let cString = c_get_state() else {
            return ""
        }
        
        let resultString = String(cString: cString)
        c_free_string(cString)
        return resultString
    }
    
    @objc public func generateUserCreds(vaultName: String) -> String {
        print("🦅 Swift: generateUserCreds with \(vaultName)")
        guard let cVaultName = vaultName.cString(using: .utf8) else {
            print("🦅 Swift: generateUserCreds return #")
            return ""
        }
        
        guard let cString = c_generate_user_creds(cVaultName) else {
            print("🦅 Swift: generateUserCreds return ##")
            return ""
        }
        
        let resultString = String(cString: cString)
        print("🦅 Swift: generateUserCreds resultString \(resultString) ")
        c_free_string(cString)
        return resultString
    }
    
    @objc public func signUp() -> String {
        guard let cString = c_sign_up() else {
            return ""
        }
        
        let resultString = String(cString: cString)
        c_free_string(cString)
        return resultString
    }

    @objc public func updateMembership(_ candidate: String, _ actionUpdate: String) -> String {
        guard let candidateString = candidate.cString(using: .utf8),
              let actionUpdateString = actionUpdate.cString(using: .utf8)
         else {
            return ""
        }

        guard let resultPtr = c_update_memberships(candidateString, actionUpdateString) else {
            return ""
        }

        let resultString = String(cString: resultPtr)
        c_free_string(resultPtr)
        return resultString
    }
    
    @objc public func splitSecret(_ secretName: String, _ secret: String) -> String {
        guard let secretNameString = secretName.cString(using: .utf8),
              let secretString = secret.cString(using: .utf8)
         else {
            return ""
        }

        guard let resultPtr = c_split_secret(secretNameString, secretString) else {
            return ""
        }

        let resultString = String(cString: resultPtr)
        c_free_string(resultPtr)
        return resultString
    }
    
    @objc public func findClaim(_ secretId: String) -> String {
        guard let secretIdString = secretId.cString(using: .utf8) else { return ""}

        guard let resultPtr = c_find_claim_by(secretIdString) else { return "" }

        let resultString = String(cString: resultPtr)
        c_free_string(resultPtr)
        return resultString
    }
    
    @objc public func recover(_ secretId: String) -> String {
        print("🦅 Swift: recover secret ID \(secretId)")
        guard let secretIdString = secretId.cString(using: .utf8) else { return "" }

        guard let resultPtr = c_recover(secretIdString) else { return "" }

        let resultString = String(cString: resultPtr)
        c_free_string(resultPtr)
        return resultString
    }
    
    @objc public func acceptRecover(_ claimId: String) -> String {
        guard let claimIdString = claimId.cString(using: .utf8) else { return "" }

        guard let resultPtr = c_accept_recover(claimIdString) else { return "" }

        let resultString = String(cString: resultPtr)
        c_free_string(resultPtr)
        return resultString
    }
    
    @objc public func showRecovered(_ secretId: String) -> String {
        guard let secretIdString = secretId.cString(using: .utf8) else { return "" }

        guard let resultPtr = c_show_recovered(secretIdString) else { return "" }

        let resultString = String(cString: resultPtr)
        c_free_string(resultPtr)
        return resultString
    }
    
    // MARK: - KeyChain
    private let serviceName: String = "MetaSecret"
    
    @objc public func saveString(key: String, value: String) -> Bool {
        print("🦅 Swift: saveString key \(key) value: \(value)")
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
        print("🦅 Swift: getString key \(key)")
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
        print("🦅 Swift: removeKey key \(key)")
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
    
    @objc public func containsKey(key: String) -> Bool {
        print("🦅 Swift: containsKey key \(key)?")
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnData as String: false
        ]
        
        let status = SecItemCopyMatching(query as CFDictionary, nil)
        print("🦅 Swift: containsKey key \(key) \(status == errSecSuccess)")
        return status == errSecSuccess
    }
    
    @objc public func clearAll() -> Bool {
        print("🦅 Swift: clearAll keys - Starting cleanup process")

        if let ptr = c_clean_up_database() {
            c_free_string(ptr)
        }
        
        cleanDB()
        
        removeBackup()

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        let keychainCleared = status == errSecSuccess || status == errSecItemNotFound
        
        print("🦅 Swift: Step 5 - Verifying cleanup")
        let verificationResult = verifyCleanup()
        
        if verificationResult.allCleared {
            print("🦅 Swift: ✅ clearAll completed successfully - All data cleared")
        } else {
            print("🦅 Swift: ⚠️ clearAll completed with warnings - Some data may remain")
            if !verificationResult.keychainCleared {
                print("🦅 Swift: ⚠️ KeyChain items may still exist")
            }
            if !verificationResult.dbCleared {
                print("🦅 Swift: ⚠️ Local DB file may still exist")
            }
            if !verificationResult.backupCleared {
                print("🦅 Swift: ⚠️ Backup files may still exist")
            }
        }
        
        return keychainCleared
    }

    // MARK: - Backuping
    @MainActor
    @objc(presentBackupPickerWithInitialMessage:okTitle:warningMessage:warningOkTitle:warningCancelTitle:backupKey:)
    public func presentBackupPickerWithInitialMessage(
        initialMessage: String,
        okTitle: String,
        warningMessage: String,
        warningOkTitle: String,
        warningCancelTitle: String,
        backupKey: String
    ) {
        presentBackupPickerWithMessages(
            initialMessage: initialMessage,
            okTitle: okTitle,
            warningMessage: warningMessage,
            warningOkTitle: warningOkTitle,
            warningCancelTitle: warningCancelTitle,
            backupKey: backupKey
        )
    }

    @MainActor
    @objc private func presentBackupPickerWithMessages(
        initialMessage: String,
        okTitle: String,
        warningMessage: String,
        warningOkTitle: String,
        warningCancelTitle: String,
        backupKey: String
    ) {
        print("🦅 Swift: Present BackUp Alert")
        BackupUI.shared.presentBackupPicker(
            initialMessage: initialMessage,
            okTitle: okTitle,
            warningMessage: warningMessage,
            warningOkTitle: warningOkTitle,
            warningCancelTitle: warningCancelTitle,
            backupKey: backupKey
        )
    }

    @objc public func restoreBackupIfNeeded() {
        BackupWorker.restoreIfNeeded()
    }

    @objc public func backupIfChanged() {
        BackupWorker.backupIfChanged()
    }

    @objc public func removeBackup() {
        BackupWorker.removeBackup()
    }
}

private extension SwiftBridge {
    func cleanDB() {
        print("🦅 Swift: CleanDB")
        let fileManager = FileManager.default
        let documentsPath = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
        let dbPath = documentsPath.appendingPathComponent("meta-secret.db")
        let isExists = fileManager.fileExists(atPath: dbPath.path)

        print("🦅 Swift: is DB exists: \(isExists)")
        if isExists {
            _ = try? fileManager.removeItem(at: dbPath)
        }
    }
    
    func verifyCleanup() -> CleanupVerificationResult {
        print("🦅 Swift: Verifying cleanup results")
        
        // Check KeyChain
        let keychainCleared = !containsKey(key: "master_key")
        print("🦅 Swift: KeyChain cleared: \(keychainCleared)")
        
        // Check local DB
        let fileManager = FileManager.default
        let documentsPath = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
        let dbPath = documentsPath.appendingPathComponent("meta-secret.db")
        let dbCleared = !fileManager.fileExists(atPath: dbPath.path)
        print("🦅 Swift: Local DB cleared: \(dbCleared)")
        
        // Check backups
        let backupCleared = !BackupWorker.hasICloudBackup()
        print("🦅 Swift: Backups cleared: \(backupCleared)")
        
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
