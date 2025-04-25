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

class MetaSecretCoreService {
    
    /// Регистрация нового пользователя
    /// - Parameter userName: Имя пользователя
    /// - Returns: Результат операции в виде строки JSON
    func signUp(userName: String) -> String? {
        // Конвертация Swift строки в С-строку
        guard let cUserName = userName.cString(using: .utf8) else {
            return nil
        }
        
        // Вызов нативной C-функции
        let resultPtr = sign_up(cUserName)
        
        // Проверка на nil и преобразование результата в Swift строку
        if let resultPtr = resultPtr {
            let result = String(cString: resultPtr)
            
            // Освобождение памяти, выделенной в C
            free_string(resultPtr)
            
            return result
        }
        
        return nil
    }
}
