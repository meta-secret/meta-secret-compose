//
//  MetaSecretCoreService.swift
//  iosApp
//
//  Created by Dmitry Kuklin on 25.04.2025.
//  Copyright © 2025 orgName. All rights reserved.
//

import Foundation

@_silgen_name("sign_up")
private func sign_up(_ userName: UnsafePointer<CChar>) -> UnsafeMutablePointer<CChar>?

@_silgen_name("free_string")
private func free_string(_ ptr: UnsafeMutablePointer<CChar>?)

class InternalMetaSecretCoreService {
    
    /// New vault registration
    /// - Parameter userName: Vault name
    /// - Returns: State as JSON
    func signUp(userName: String) -> String? {
        guard let cUserName = userName.cString(using: .utf8) else {
            return nil
        }

        let resultPtr = sign_up(cUserName)
        
        if let resultPtr = resultPtr {
            let result = String(cString: resultPtr)
            
            free_string(resultPtr)
            
            return result
        }
        
        return nil
    }
}
