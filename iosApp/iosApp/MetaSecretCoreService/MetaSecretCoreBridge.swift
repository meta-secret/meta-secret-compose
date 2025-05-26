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
        return "masterKey"
    }

    @objc public func signUp() {
        let vaultInfo = "getAppInfo()"
        print("DK: \(vaultInfo)")
    }
}
