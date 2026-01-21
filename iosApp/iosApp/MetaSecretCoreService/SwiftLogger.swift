//
//  SwiftLogger.swift
//  iosApp
//
//  Created by Auto on 25.12.2025.
//  Copyright © 2025 orgName. All rights reserved.
//

import Foundation

private func getCurrentTimestamp() -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
    formatter.timeZone = TimeZone.current
    return formatter.string(from: Date())
}

enum SwiftLogTag: String, Sendable {
    case swiftBridge = "🦅SwiftBridge"
    case backupUI = "🦅BackupUI"
    case backupWorker = "🦅👷BackupWorker"
    
    var displayName: String {
        return self.rawValue
    }
}

final class SwiftLogger: @unchecked Sendable {
    nonisolated(unsafe) static let shared = SwiftLogger()
    
    private let stateLock = NSLock()
    private var isActive: Bool
    
    init(isActive: Bool = true) {
        self.isActive = isActive
    }
    
    nonisolated func updateActive(_ isActive: Bool) {
        stateLock.lock()
        self.isActive = isActive
        stateLock.unlock()
    }
    
    nonisolated func log(tag: SwiftLogTag, message: String, success: Bool? = nil) {
        stateLock.lock()
        let active = isActive
        stateLock.unlock()
        guard active else { return }
        
        let prefix: String
        if let success = success {
            prefix = success ? "✅" : "❌"
        } else {
            prefix = ""
        }
        
        let fullMessage = prefix.isEmpty ? message : "\(prefix) \(message)"
        let timestamp = getCurrentTimestamp()
        print("[\(timestamp)] \(tag.displayName): \(fullMessage)")
    }
    
    nonisolated func logInfo(tag: SwiftLogTag, message: String) {
        log(tag: tag, message: message, success: nil)
    }
    
    nonisolated func logSuccess(tag: SwiftLogTag, message: String) {
        log(tag: tag, message: message, success: true)
    }
    
    nonisolated func logError(tag: SwiftLogTag, message: String) {
        log(tag: tag, message: message, success: false)
    }
}

