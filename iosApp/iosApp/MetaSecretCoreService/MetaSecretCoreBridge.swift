//
//  MetaSecretCoreBridge.swift
//  iosApp
//
//  Created by Dmitry Kuklin on 25.04.2025.
//  Copyright © 2025 orgName. All rights reserved.
//

import Foundation

@objc public class MetaSecretCoreBridge: NSObject {
    @objc public var vaultName: String = ""
    
    @objc public func generateMasterKey() -> String {
        let cString = generate_master_key() ?? ""
        let swiftString = String(cString: cString)
        if cString != "" {
            free_string(cString)
        }
        return swiftString
    }

    @objc public func signUp() {
        print("DK: ")
    }
}
