//
//  MetaSecretCoreBridge.swift
//  iosApp
//
//  Created by Dmitry Kuklin on 25.04.2025.
//  Copyright © 2025 orgName. All rights reserved.
//

import Foundation

@objc public class MetaSecretCoreBridge: NSObject {

    private var service: InternalMetaSecretCoreService
    
    @objc public override init() {
        self.service = InternalMetaSecretCoreService()
    }
    
    @objc public var vaultName: String = ""

    @objc public func signUp() {
        
        let result = service.signUp(userName: vaultName)
        print("DK: \(result)")
    }
}
