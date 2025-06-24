//
//  SwiftBridge.swift
//  iosApp
//
//  Created by Dmitry Kuklin on 25.04.2025.
//  Copyright © 2025 orgName. All rights reserved.
//

import Foundation

@objc public class SwiftBridge: NSObject {
    // MARK: - MetaSecretCoreBridge API

    @_silgen_name("generate_master_key")
    private func c_generate_master_key() -> UnsafeMutablePointer<CChar>?

    @_silgen_name("init")
    private func c_init(_ master_key_ptr: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>?
    
    @_silgen_name("get_state")
    private func c_get_state() -> UnsafeMutablePointer<CChar>?
    
    @_silgen_name("generate_user_creds")
    private func c_generate_user_creds() -> UnsafeMutablePointer<CChar>?
    
    @_silgen_name("sign_up")
    private func c_sign_up() -> UnsafeMutablePointer<CChar>?

    @_silgen_name("free_string")
    private func c_free_string(_ ptr: UnsafeMutablePointer<CChar>?)
    
    // MARK: - MetaSecretCoreBridge
    
    @objc public var vaultName: String = ""
    
    @objc public func generateMasterKey() -> String {
        guard let cString = c_generate_master_key() else {
            return ""
        }
        
        let swiftString = String(cString: cString)
        c_free_string(cString)
        return swiftString
    }

    @objc public func initWithMasterKey(_ masterKey: String) -> String {
        cleanDB()
        
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
    
    @objc public func generateUserCreds(with vaultName: String) -> String {
        guard let cString = c_generate_user_creds() else {
            return ""
        }
        
        let resultString = String(cString: cString)
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
    
    // MARK: - KeyChain
    private let serviceName: String = "MetaSecret"
    
    @objc public func saveString(key: String, value: String) -> Bool {
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
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
    
    @objc public func containsKey(key: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnData as String: false
        ]
        
        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess
    }
    
    @objc public func clearAll() -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}

private extension SwiftBridge {
    func cleanDB() {
        print("CLEAN DB")
        let fileManager = FileManager.default
        let documentsPath = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
        let dbPath = documentsPath.appendingPathComponent("meta-secret.db")
        let isExists = fileManager.fileExists(atPath: dbPath.path)
        
        if isExists {
            _ = try? fileManager.removeItem(at: dbPath)
        }
    }
}
