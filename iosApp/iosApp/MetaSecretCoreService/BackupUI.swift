//
//  BackupUI.swift
//  iosApp
//

import Foundation
import UIKit
import ObjectiveC

@MainActor
final class BackupUI: NSObject {
    static let shared = BackupUI()

    private let iCloudContainerId = "iCloud.metasecret.project.com.KotlinProject"

    func presentBackupPicker(
        initialMessage: String,
        okTitle: String,
        warningMessage: String,
        warningOkTitle: String,
        warningCancelTitle: String,
        backupKey: String,
        dbFileName: String
    ) {
        let backupExists = BackupWorker.hasICloudBackup(dbFileName: dbFileName)
        guard !backupExists else {
            SwiftLogger.shared.logInfo(tag: .backupUI, message: "iCloud backup Exists. Alert doesn't need")
            return
        }
        SwiftLogger.shared.logInfo(tag: .backupUI, message: "iCloud backup Not Exists")

        guard let presenter = self.topMostViewController() else { return }
        let alert = UIAlertController(title: nil, message: initialMessage, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: okTitle, style: .default) { [weak self] _ in
            guard let strongSelf = self else { return }
            guard let currentPresenter = strongSelf.topMostViewController() else { return }
            strongSelf.presentSystemPicker(
                from: currentPresenter,
                warningMessage: warningMessage,
                warningOkTitle: warningOkTitle,
                warningCancelTitle: warningCancelTitle,
                backupKey: backupKey,
                dbFileName: dbFileName
            )
        })
        presenter.present(alert, animated: true)
    }

    private func presentSystemPicker(
        from presenter: UIViewController,
        warningMessage: String,
        warningOkTitle: String,
        warningCancelTitle: String,
        backupKey: String,
        dbFileName: String
    ) {
        let fm = FileManager.default
        guard let documentsPath = fm.urls(for: .documentDirectory, in: .userDomainMask).first else {
            SwiftLogger.shared.logError(tag: .backupUI, message: "Documents directory not found")
            return
        }
        let src = documentsPath.appendingPathComponent(dbFileName)
        
        guard fm.fileExists(atPath: src.path) else {
            SwiftLogger.shared.logError(tag: .backupUI, message: "Database file does not exist at path: \(src.path)")
            let alert = UIAlertController(
                title: nil,
                message: "Database file not found. Please ensure the app has been initialized.",
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: warningOkTitle, style: .default))
            presenter.present(alert, animated: true)
            return
        }

        let picker: UIDocumentPickerViewController
        if #available(iOS 14.0, *) {
            picker = UIDocumentPickerViewController(forExporting: [src], asCopy: true)
        } else {
            picker = UIDocumentPickerViewController(url: src, in: .exportToService)
        }

        if let container = fm.url(forUbiquityContainerIdentifier: iCloudContainerId) {
            let docs = container.appendingPathComponent("Documents", isDirectory: true)
            picker.directoryURL = docs
        }

        let delegate = PickerDelegate(
            warningMessage: warningMessage,
            warningOkTitle: warningOkTitle,
            warningCancelTitle: warningCancelTitle,
            backupKey: backupKey,
            dbFileName: dbFileName
        )
        picker.delegate = delegate
        objc_setAssociatedObject(picker, Unmanaged.passUnretained(picker).toOpaque(), delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        presenter.present(picker, animated: true)
    }

    fileprivate func showWarningAlert(
        from presenter: UIViewController,
        warningMessage: String,
        warningOkTitle: String,
        warningCancelTitle: String,
        backupKey: String,
        dbFileName: String
    ) {
        let alert = UIAlertController(title: nil, message: warningMessage, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: warningOkTitle, style: .default) { _ in })
        alert.addAction(UIAlertAction(title: warningCancelTitle, style: .cancel) { _ in
            self.presentSystemPicker(
                from: presenter,
                warningMessage: warningMessage,
                warningOkTitle: warningOkTitle,
                warningCancelTitle: warningCancelTitle,
                backupKey: backupKey,
                dbFileName: dbFileName
            )
        })
        presenter.present(alert, animated: true)
    }
}

fileprivate extension BackupUI {
    @MainActor
    func topMostViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .flatMap { $0.windows }
        .first { $0.isKeyWindow }
        return topMostViewController(from: window?.rootViewController)
    }

    @MainActor
    func topMostViewController(from base: UIViewController?) -> UIViewController? {
        if let nav = base as? UINavigationController {
            return topMostViewController(from: nav.visibleViewController)
        }
        if let tab = base as? UITabBarController, let selected = tab.selectedViewController {
            return topMostViewController(from: selected)
        }
        if let presented = base?.presentedViewController {
            return topMostViewController(from: presented)
        }
        return base
    }
}

@MainActor
private final class PickerDelegate: NSObject, UIDocumentPickerDelegate {
    let warningMessage: String
    let warningOkTitle: String
    let warningCancelTitle: String
    let backupKey: String
    let dbFileName: String

    init(
        warningMessage: String,
        warningOkTitle: String,
        warningCancelTitle: String,
        backupKey: String,
        dbFileName: String
    ) {
        self.warningMessage = warningMessage
        self.warningOkTitle = warningOkTitle
        self.warningCancelTitle = warningCancelTitle
        self.backupKey = backupKey
        self.dbFileName = dbFileName
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        SwiftLogger.shared.logError(tag: .backupUI, message: "document Picker Was Cancelled")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self, weak controller] in
            guard let self = self else { return }
            controller?.dismiss(animated: true) {
                let presenter = BackupUI.shared.topMostViewController() ?? controller?.presentingViewController ?? controller?.parent ?? controller
                guard let presenter = presenter else { return }
                BackupUI.shared.showWarningAlert(
                    from: presenter,
                    warningMessage: self.warningMessage,
                    warningOkTitle: self.warningOkTitle,
                    warningCancelTitle: self.warningCancelTitle,
                    backupKey: self.backupKey,
                    dbFileName: self.dbFileName
                )
            }
        }
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        SwiftLogger.shared.logSuccess(tag: .backupUI, message: "document Picker Was Chosen")
        guard let url = urls.first else {
            guard let presenter = (controller.presentingViewController ?? controller.parent) else { return }
            BackupUI.shared.showWarningAlert(
                from: presenter,
                warningMessage: warningMessage,
                warningOkTitle: warningOkTitle,
                warningCancelTitle: warningCancelTitle,
                backupKey: backupKey,
                dbFileName: dbFileName
            )
            return
        }

        SwiftLogger.shared.logSuccess(tag: .backupUI, message: "target URL: \(url)")
        if !BackupWorker.isInICloudContainer(url: url) {
            SwiftLogger.shared.logSuccess(tag: .backupUI, message: "isInICloudContainer false")
            guard let presenter = (controller.presentingViewController ?? controller.parent) else { return }
            BackupUI.shared.showWarningAlert(
                from: presenter,
                warningMessage: warningMessage,
                warningOkTitle: warningOkTitle,
                warningCancelTitle: warningCancelTitle,
                backupKey: backupKey,
                dbFileName: dbFileName
            )
            return
        }

        SwiftLogger.shared.logSuccess(tag: .backupUI, message: "isInICloudContainer true")

        let needsScope = url.startAccessingSecurityScopedResource()
        defer { if needsScope { url.stopAccessingSecurityScopedResource() } }

        do {
            let bookmark = try url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil)
            let b64 = bookmark.base64EncodedString()
            _ = SwiftBridge().saveString(key: "bdBackUpBookmark", value: b64)
            SwiftLogger.shared.logSuccess(tag: .backupUI, message: "bdBackUpBookmark saved (\(bookmark.count) bytes)")
        } catch {
            SwiftLogger.shared.logError(tag: .backupUI, message: "bookmarkData error: \(error)")
        }

        _ = SwiftBridge().saveString(key: backupKey, value: url.path)
        SwiftLogger.shared.logSuccess(tag: .backupUI, message: "stored path for UI: \(url.path)")
    }
}

// MARK: - BackupWorker

final class BackupWorker {
    private static let containerId = "iCloud.metasecret.project.com.KotlinProject"



    static func isInICloudContainer(url: URL) -> Bool {
        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "isInICloudContainer url: \(url)")
        let fm = FileManager.default
        let u = url.standardizedFileURL

        if let container = fm.url(forUbiquityContainerIdentifier: containerId) {
            if u.path.hasPrefix(container.standardizedFileURL.path) { return true }
        }
        let path = u.path
        if path.contains("/Mobile Documents/") || path.contains("com~apple~CloudDocs") { return true }
        if let vals = try? u.resourceValues(forKeys: [.isUbiquitousItemKey]),
           vals.isUbiquitousItem == true { return true }
        return false
    }




    static func restoreIfNeeded(dbFileName: String) {
        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "restoreIfNeeded")
        let fileExists = SwiftBridge.hasLocalDatabaseFile(dbFileName: dbFileName)
        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "File exists: \(fileExists)")
        
        if fileExists {
            SwiftLogger.shared.logInfo(tag: .backupWorker, message: "DB file already exists with data - skipping restore")
            return
        }
        
        let fm = FileManager.default
        guard let documentsPath = fm.urls(for: .documentDirectory, in: .userDomainMask).first else {
            SwiftLogger.shared.logError(tag: .backupWorker, message: "restoreIfNeeded - localDBURL is nil")
            return
        }
        let dst = documentsPath.appendingPathComponent(dbFileName)

        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "Starting restore process...")
        if let urlFromBookmark = resolveBookmark() {
            let needs = urlFromBookmark.startAccessingSecurityScopedResource()
            defer { if needs { urlFromBookmark.stopAccessingSecurityScopedResource() } }
            _ = ensureDownloadedIfUbiquitous(urlFromBookmark)

            let coordinator = NSFileCoordinator(filePresenter: nil)
            var coordError: NSError?
            coordinator.coordinate(readingItemAt: urlFromBookmark, options: [], error: &coordError) { readURL in
                do { try FileManager.default.copyItem(at: readURL, to: dst) }
                catch { SwiftLogger.shared.logError(tag: .backupWorker, message: "restoreIfNeeded (bookmark) error \(error)") }
            }
            if let e = coordError { SwiftLogger.shared.logError(tag: .backupWorker, message: "restoreIfNeeded (bookmark) coordination error \(e)") }
            if SwiftBridge.hasLocalDatabaseFile(dbFileName: dbFileName) { return }
        }

        guard let container = fm.url(forUbiquityContainerIdentifier: containerId) else { return }
        let docs = container.appendingPathComponent("Documents", isDirectory: true)
        let srcURL = docs.appendingPathComponent(dbFileName, isDirectory: false)
        _ = ensureDownloadedIfUbiquitous(srcURL)

        let coordinator = NSFileCoordinator(filePresenter: nil)
        var coordError: NSError?
        coordinator.coordinate(readingItemAt: srcURL, options: [], error: &coordError) { readURL in
            do { try FileManager.default.copyItem(at: readURL, to: dst) }
            catch { SwiftLogger.shared.logError(tag: .backupWorker, message: "restoreIfNeeded error \(error)") }
        }
        if let e = coordError { SwiftLogger.shared.logError(tag: .backupWorker, message: "restoreIfNeeded coordination error \(e)") }
    }

    static func backupIfChanged(dbFileName: String) {
        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "backupIfChanged - Local DB updated, starting backup")
        let fm = FileManager.default
        let documentsPath = fm.urls(for: .documentDirectory, in: .userDomainMask).first
        guard let src = documentsPath?.appendingPathComponent(dbFileName), fm.fileExists(atPath: src.path) else { 
            SwiftLogger.shared.logError(tag: .backupWorker, message: "Local DB not found, skipping backup")
            return 
        }

        // Log DB modification time
        if let modificationDate = try? src.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate {
            SwiftLogger.shared.logInfo(tag: .backupWorker, message: "📅 Local DB last modified: \(modificationDate)")
        }

        if let dstURL = resolveBookmark() {
            let needs = dstURL.startAccessingSecurityScopedResource()
            defer { if needs { dstURL.stopAccessingSecurityScopedResource() } }
            _ = ensureDownloadedIfUbiquitous(dstURL)

            let srcDate = (try? src.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            let dstDate = (try? dstURL.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            
            if srcDate > dstDate {
                SwiftLogger.shared.logInfo(tag: .backupWorker, message: "🔄 Local DB is newer, copying to bookmark location")
                copyLocalDBTo(url: dstURL, dbFileName: dbFileName)
            } else {
                SwiftLogger.shared.logSuccess(tag: .backupWorker, message: "Bookmark backup is up to date")
            }
            return
        }

        guard let container = fm.url(forUbiquityContainerIdentifier: containerId) else { 
            SwiftLogger.shared.logError(tag: .backupWorker, message: "iCloud container not available")
            return 
        }
        let docs = container.appendingPathComponent("Documents", isDirectory: true)
        let dstURL = docs.appendingPathComponent(dbFileName, isDirectory: false)
        _ = ensureDownloadedIfUbiquitous(dstURL)
        
        let srcDate = (try? src.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
        let dstDate = (try? dstURL.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
        
        if srcDate > dstDate {
            SwiftLogger.shared.logInfo(tag: .backupWorker, message: "🔄 Local DB is newer, copying to iCloud")
            copyLocalDBToICloud(dbFileName: dbFileName)
        } else {
            SwiftLogger.shared.logSuccess(tag: .backupWorker, message: "iCloud backup is up to date")
        }
    }

    private static func copyLocalDBTo(url: URL, dbFileName: String) {
        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "copyLocalDBTo \(url.path)")
        let fm = FileManager.default
        let documentsPath = fm.urls(for: .documentDirectory, in: .userDomainMask).first
        guard let src = documentsPath?.appendingPathComponent(dbFileName), fm.fileExists(atPath: src.path) else { 
            SwiftLogger.shared.logError(tag: .backupWorker, message: "Source DB not found")
            return 
        }

        let needsScope = url.startAccessingSecurityScopedResource()
        defer { if needsScope { url.stopAccessingSecurityScopedResource() } }

        let coordinator = NSFileCoordinator(filePresenter: nil)
        var coordinationError: NSError?

        let exists = (try? url.checkResourceIsReachable()) ?? false
        let options: NSFileCoordinator.WritingOptions = exists ? .forReplacing : []

        coordinator.coordinate(writingItemAt: url, options: options, error: &coordinationError) { dst in
            do {
                let parent = dst.deletingLastPathComponent()
                let tmp = parent.appendingPathComponent(UUID().uuidString)
                if fm.fileExists(atPath: tmp.path) { try? fm.removeItem(at: tmp) }
                try fm.copyItem(at: src, to: tmp)

                if exists {
                    _ = try fm.replaceItemAt(dst, withItemAt: tmp)
                } else {
                    try fm.moveItem(at: tmp, to: dst)
                }
                SwiftLogger.shared.logSuccess(tag: .backupWorker, message: "DB copied to bookmark location successfully")
            } catch let e as NSError {
                SwiftLogger.shared.logError(tag: .backupWorker, message: "copyLocalDBTo error \(e)")
            }
        }
        if let e = coordinationError {
            SwiftLogger.shared.logError(tag: .backupWorker, message: "copyLocalDBTo coordination error \(e)")
        }
    }

    private static func copyLocalDBToICloud(dbFileName: String) {
        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "copyLocalDBToICloud")
        let fm = FileManager.default
        let documentsPath = fm.urls(for: .documentDirectory, in: .userDomainMask).first
        guard let src = documentsPath?.appendingPathComponent(dbFileName), fm.fileExists(atPath: src.path) else { 
            SwiftLogger.shared.logError(tag: .backupWorker, message: "Source DB not found")
            return 
        }
        guard let container = fm.url(forUbiquityContainerIdentifier: containerId) else { 
            SwiftLogger.shared.logError(tag: .backupWorker, message: "iCloud container not available")
            return 
        }
        let docs = container.appendingPathComponent("Documents", isDirectory: true)
        let dst = docs.appendingPathComponent(dbFileName, isDirectory: false)

        let coordinator = NSFileCoordinator(filePresenter: nil)
        var coordinationError: NSError?

        let exists = (try? dst.checkResourceIsReachable()) ?? false
        let options: NSFileCoordinator.WritingOptions = exists ? .forReplacing : []

        coordinator.coordinate(writingItemAt: dst, options: options, error: &coordinationError) { dstURL in
            do {
                let tmp = dstURL.deletingLastPathComponent().appendingPathComponent(UUID().uuidString)
                if fm.fileExists(atPath: tmp.path) { try? fm.removeItem(at: tmp) }
                try fm.copyItem(at: src, to: tmp)
                if exists {
                    _ = try fm.replaceItemAt(dstURL, withItemAt: tmp)
                } else {
                    try fm.moveItem(at: tmp, to: dstURL)
                }
                SwiftLogger.shared.logSuccess(tag: .backupWorker, message: "DB copied to iCloud successfully")
            } catch let e as NSError {
                SwiftLogger.shared.logError(tag: .backupWorker, message: "copyLocalDBToICloud error \(e)")
            }
        }
        if let e = coordinationError {
            SwiftLogger.shared.logError(tag: .backupWorker, message: "copyLocalDBToICloud coordination error \(e)")
        }
    }

    static func removeBackup(dbFileName: String) {
        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "removeBackup")

        if let url = resolveBookmark() {
            let needs = url.startAccessingSecurityScopedResource()
            defer { if needs { url.stopAccessingSecurityScopedResource() } }

            let coordinator = NSFileCoordinator(filePresenter: nil)
            var coordError: NSError?
            coordinator.coordinate(writingItemAt: url, options: .forDeleting, error: &coordError) { delURL in
                do { try FileManager.default.removeItem(at: delURL) }
                catch { SwiftLogger.shared.logError(tag: .backupWorker, message: "removeBackup (bookmark) error \(error)") }
            }
            if let e = coordError { SwiftLogger.shared.logError(tag: .backupWorker, message: "removeBackup (bookmark) coordination error \(e)") }
        }

        let fm = FileManager.default
        if let container = fm.url(forUbiquityContainerIdentifier: containerId) {
            let docs = container.appendingPathComponent("Documents", isDirectory: true)
            let srcURL = docs.appendingPathComponent(dbFileName, isDirectory: false)
            let coordinator = NSFileCoordinator(filePresenter: nil)
            var coordError: NSError?
            coordinator.coordinate(writingItemAt: srcURL, options: .forDeleting, error: &coordError) { delURL in
                do { try FileManager.default.removeItem(at: delURL) }
                catch { SwiftLogger.shared.logError(tag: .backupWorker, message: "removeBackup error \(error)") }
            }
            if let e = coordError { SwiftLogger.shared.logError(tag: .backupWorker, message: "removeBackup coordination error \(e)") }
        }
    }

    @discardableResult
    private static func ensureDownloadedIfUbiquitous(_ url: URL, timeout: TimeInterval = 10) -> Bool {
        let u = url

        if let rv = try? u.resourceValues(forKeys: [.isUbiquitousItemKey]),
           rv.isUbiquitousItem != true {
            SwiftLogger.shared.logInfo(tag: .backupWorker, message: "Not Ubiquitous")
            return true
        }

        func status() -> URLUbiquitousItemDownloadingStatus? {
            guard let s = (try? u.resourceValues(forKeys: [.ubiquitousItemDownloadingStatusKey]))?
                .allValues[.ubiquitousItemDownloadingStatusKey] as? String else { return nil }
            return URLUbiquitousItemDownloadingStatus(rawValue: s)
        }

        if let s = status(), s == .downloaded || s == .current { return true }

        do { try FileManager.default.startDownloadingUbiquitousItem(at: u) }
        catch { SwiftLogger.shared.logError(tag: .backupWorker, message: "startDownloadingUbiquitousItem error: \(error)") }

        // Use shorter intervals and fewer iterations to avoid blocking UI thread
        let deadline = Date().addingTimeInterval(min(timeout, 3.0)) // Max 3 seconds
        let checkInterval: TimeInterval = 0.1 // Check every 100ms
        var iterations = 0
        let maxIterations = Int(timeout / checkInterval)
        
        while Date() < deadline && iterations < maxIterations {
            if let s = status(), s == .downloaded || s == .current { 
                SwiftLogger.shared.logSuccess(tag: .backupWorker, message: "Download completed after \(iterations) iterations")
                return true 
            }
            
            // Use DispatchQueue.main.async to prevent blocking
            Thread.sleep(forTimeInterval: checkInterval)
            iterations += 1
            
            // Allow system to process other events
            if iterations % 10 == 0 {
                SwiftLogger.shared.logInfo(tag: .backupWorker, message: "Still downloading... iteration \(iterations)")
            }
        }

        SwiftLogger.shared.logError(tag: .backupWorker, message: "Download timeout for: \(u) after \(iterations) iterations")
        return false
    }

    private static func resolveBookmark() -> URL? {
        guard let b64 = SwiftBridge().getString(key: "bdBackUpBookmark") else {
            SwiftLogger.shared.logInfo(tag: .backupWorker, message: "No bookmark key in storage")
            return nil
        }
        guard let data = Data(base64Encoded: b64) else {
            SwiftLogger.shared.logError(tag: .backupWorker, message: "Bookmark base64 decode failed")
            return nil
        }
        var stale = false
        do {
            let url = try URL(
                resolvingBookmarkData: data,
                options: [],
                relativeTo: nil,
                bookmarkDataIsStale: &stale
            )
            if stale {
                if let newData = try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) {
                    let b64new = newData.base64EncodedString()
                    _ = SwiftBridge().saveString(key: "bdBackUpBookmark", value: b64new)
                    SwiftLogger.shared.logInfo(tag: .backupWorker, message: "Bookmark was stale – refreshed")
                }
            }
            SwiftLogger.shared.logInfo(tag: .backupWorker, message: "Resolved bookmark URL: \(url)")
            return url
        } catch {
            SwiftLogger.shared.logError(tag: .backupWorker, message: "resolveBookmark error: \(error)")
            let nsError = error as NSError
            if nsError.code == 257 { // Permission denied - typical after app reinstall
                SwiftLogger.shared.logInfo(tag: .backupWorker, message: "Bookmark lost permissions after app reinstall - clearing invalid bookmark")
                _ = SwiftBridge().saveString(key: "bdBackUpBookmark", value: "")
            } else if nsError.code == 260 { // File not found
                SwiftLogger.shared.logInfo(tag: .backupWorker, message: "Bookmark points to non-existent file - clearing")
                _ = SwiftBridge().saveString(key: "bdBackUpBookmark", value: "")
            }
            return nil
        }
    }

    static func hasICloudBackup(dbFileName: String) -> Bool {
        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "hasICloudBackup")
        let fm = FileManager.default

        if let url = resolveBookmark() {
            let needs = url.startAccessingSecurityScopedResource()
            defer { if needs { url.stopAccessingSecurityScopedResource() } }
            _ = ensureDownloadedIfUbiquitous(url)
            let existsReach = (try? url.checkResourceIsReachable()) ?? false
            let existsFS = fm.fileExists(atPath: url.path)
            SwiftLogger.shared.logInfo(tag: .backupWorker, message: "Bookmark check: reachable=\(existsReach) fileExists=\(existsFS)")
            if existsReach || existsFS {
                printFileDetails(url: url, source: "bookmark")
                SwiftLogger.shared.logSuccess(tag: .backupWorker, message: "Exists (bookmark)")
                return true
            }
        }

        if let container = fm.url(forUbiquityContainerIdentifier: containerId) {
            let docs = container.appendingPathComponent("Documents", isDirectory: true)
            let iCloudDB = docs.appendingPathComponent(dbFileName)
            let exists = ((try? iCloudDB.checkResourceIsReachable()) ?? false) || fm.fileExists(atPath: iCloudDB.path)
            if exists {
                printFileDetails(url: iCloudDB, source: "app container")
                SwiftLogger.shared.logSuccess(tag: .backupWorker, message: "Exists (app container)")
                return true
            }
        }

        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "Not Exists")
        return false
    }
    
    private static func printFileDetails(url: URL, source: String) {
        let fm = FileManager.default
        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "📁 File details (\(source)):")
        SwiftLogger.shared.logInfo(tag: .backupWorker, message: "📁 Path: \(url.path)")
        
        do {
            let attributes = try fm.attributesOfItem(atPath: url.path)
            let fileSize = attributes[.size] as? NSNumber ?? 0
            let creationDate = attributes[.creationDate] as? Date
            let modificationDate = attributes[.modificationDate] as? Date
            let fileType = attributes[.type] as? FileAttributeType
            
            SwiftLogger.shared.logInfo(tag: .backupWorker, message: "📁 Size: \(fileSize) bytes (\(ByteCountFormatter.string(fromByteCount: fileSize.int64Value, countStyle: .file)))")
            SwiftLogger.shared.logInfo(tag: .backupWorker, message: "📁 Created: \(creationDate?.description ?? "unknown")")
            SwiftLogger.shared.logInfo(tag: .backupWorker, message: "📁 Modified: \(modificationDate?.description ?? "unknown")")
            SwiftLogger.shared.logInfo(tag: .backupWorker, message: "📁 Type: \(fileType?.rawValue ?? "unknown")")
            
            // Check if it's ubiquitous (iCloud)
            if let resourceValues = try? url.resourceValues(forKeys: [
                .isUbiquitousItemKey,
                .ubiquitousItemDownloadingStatusKey
            ]) {
                let isUbiquitous = resourceValues.isUbiquitousItem ?? false
                let downloadStatus = resourceValues.ubiquitousItemDownloadingStatus?.rawValue ?? "unknown"
                let isDownloaded = downloadStatus == URLUbiquitousItemDownloadingStatus.current.rawValue
                
                SwiftLogger.shared.logInfo(tag: .backupWorker, message: "📁 iCloud file: \(isUbiquitous)")
                SwiftLogger.shared.logInfo(tag: .backupWorker, message: "📁 Download status: \(downloadStatus)")
                SwiftLogger.shared.logInfo(tag: .backupWorker, message: "📁 Downloaded: \(isDownloaded)")
            }
        } catch {
            SwiftLogger.shared.logError(tag: .backupWorker, message: "📁 Error getting file attributes: \(error)")
        }
    }
}
