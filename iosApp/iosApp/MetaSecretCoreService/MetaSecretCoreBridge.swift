//
//  MetaSecretCoreBridge.swift
//  iosApp
//
//  Created by Dmitry Kuklin on 25.04.2025.
//  Copyright © 2025 orgName. All rights reserved.
//

import Foundation

@_silgen_name("generate_master_key")
private func c_generate_master_key() -> UnsafeMutablePointer<CChar>?

@_silgen_name("free_string")
private func c_free_string(_ ptr: UnsafeMutablePointer<CChar>?)

@objc public class MetaSecretCoreBridge: NSObject {
    @objc public var vaultName: String = ""
    
    @objc public func generateMasterKey() -> String {
        let cString = c_generate_master_key() ?? nil
        guard let cString = cString else {
            return ""
        }
        
        let swiftString = String(cString: cString)
        c_free_string(cString)
        return swiftString
    }

    @objc public func signUp() {
        print("DK: ")
    }
}
