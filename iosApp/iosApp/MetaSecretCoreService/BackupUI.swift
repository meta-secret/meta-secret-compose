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
        backupKey: String
    ) {
        let backupExists = BackupWorker.hasICloudBackup()
        guard !backupExists else {
            print("🦅 Swift: iCloud backup Exists. Alert doesn't need")
            return
        }
        print("🦅 Swift: iCloud backup Not Exists")

        guard let presenter = self.topMostViewController() else { return }
        let alert = UIAlertController(title: nil, message: initialMessage, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: okTitle, style: .default) { _ in
            self.presentSystemPicker(
                from: presenter,
                warningMessage: warningMessage,
                warningOkTitle: warningOkTitle,
                warningCancelTitle: warningCancelTitle,
                backupKey: backupKey
            )
        })
        presenter.present(alert, animated: true)
    }

    private func presentSystemPicker(
        from presenter: UIViewController,
        warningMessage: String,
        warningOkTitle: String,
        warningCancelTitle: String,
        backupKey: String
    ) {
        let fm = FileManager.default
        let documentsPath = fm.urls(for: .documentDirectory, in: .userDomainMask).first!
        let src = documentsPath.appendingPathComponent("meta-secret.db")

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
            backupKey: backupKey
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
        backupKey: String
    ) {
        let alert = UIAlertController(title: nil, message: warningMessage, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: warningOkTitle, style: .default) { _ in })
        alert.addAction(UIAlertAction(title: warningCancelTitle, style: .cancel) { _ in
            self.presentSystemPicker(
                from: presenter,
                warningMessage: warningMessage,
                warningOkTitle: warningOkTitle,
                warningCancelTitle: warningCancelTitle,
                backupKey: backupKey
            )
        })
        presenter.present(alert, animated: true)
    }
}

private extension BackupUI {
    @MainActor
    private func topMostViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .flatMap { $0.windows }
        .first { $0.isKeyWindow }
        return topMostViewController(from: window?.rootViewController)
    }

    @MainActor
    private func topMostViewController(from base: UIViewController?) -> UIViewController? {
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

    init(
        warningMessage: String,
        warningOkTitle: String,
        warningCancelTitle: String,
        backupKey: String
    ) {
        self.warningMessage = warningMessage
        self.warningOkTitle = warningOkTitle
        self.warningCancelTitle = warningCancelTitle
        self.backupKey = backupKey
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        print("🦅❌SwiftUI: document Picker Was Cancelled")
        guard let presenter = (controller.presentingViewController ?? controller.parent) else { return }
        BackupUI.shared.showWarningAlert(
            from: presenter,
            warningMessage: warningMessage,
            warningOkTitle: warningOkTitle,
            warningCancelTitle: warningCancelTitle,
            backupKey: backupKey
        )
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        print("🦅✅SwiftUI: document Picker Was Chosen")
        guard let url = urls.first else {
            guard let presenter = (controller.presentingViewController ?? controller.parent) else { return }
            BackupUI.shared.showWarningAlert(
                from: presenter,
                warningMessage: warningMessage,
                warningOkTitle: warningOkTitle,
                warningCancelTitle: warningCancelTitle,
                backupKey: backupKey
            )
            return
        }

        print("🦅✅SwiftUI: target URL: \(url)")
        if !BackupWorker.isInICloudContainer(url: url) {
            print("🦅✅SwiftUI: isInICloudContainer false")
            guard let presenter = (controller.presentingViewController ?? controller.parent) else { return }
            BackupUI.shared.showWarningAlert(
                from: presenter,
                warningMessage: warningMessage,
                warningOkTitle: warningOkTitle,
                warningCancelTitle: warningCancelTitle,
                backupKey: backupKey
            )
            return
        }

        print("🦅✅SwiftUI: isInICloudContainer true")

        let needsScope = url.startAccessingSecurityScopedResource()
        defer { if needsScope { url.stopAccessingSecurityScopedResource() } }

        do {
            let bookmark = try url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil)
            let b64 = bookmark.base64EncodedString()
            _ = SwiftBridge().saveString(key: "bdBackUpBookmark", value: b64)
            print("🦅✅SwiftUI: bdBackUpBookmark saved (\(bookmark.count) bytes)")
        } catch {
            print("🦅❌SwiftUI: bookmarkData error: \(error)")
        }

        _ = SwiftBridge().saveString(key: backupKey, value: url.path)
        print("🦅✅SwiftUI: stored path for UI: \(url.path)")
    }
}

// MARK: - BackupWorker

final class BackupWorker {
    private static let containerId = "iCloud.metasecret.project.com.KotlinProject"

    private static func iCloudAvailable() -> Bool {
        return FileManager.default.ubiquityIdentityToken != nil
    }

    private static func iCloudBackupURL() -> URL? {
        print("🦅👷 BackupWorker: iCloudBackupURL")
        
        // Detailed iCloud diagnostics
        let hasToken = FileManager.default.ubiquityIdentityToken != nil
        print("🦅👷 iCloud identity token available: \(hasToken)")
        
        if !hasToken {
            print("🦅👷❌ iCloud unavailable - user not logged in or iCloud Drive disabled")
            print("🦅👷❌ Check: Settings → [Your Name] → iCloud → iCloud Drive")
            return nil
        }

        let fm = FileManager.default
        print("🦅👷 Trying container ID: \(containerId)")
        guard let container = fm.url(forUbiquityContainerIdentifier: containerId) else {
            print("🦅👷❌ iCloud container '\(containerId)' not available")
            print("🦅👷❌ Possible causes:")
            print("🦅👷❌ 1) App entitlements missing iCloud capability")
            print("🦅👷❌ 2) Container ID not configured in Apple Developer Console")
            print("🦅👷❌ 3) Settings → [Your Name] → iCloud → Apps Using iCloud → MetaSecret disabled")
            print("🦅👷❌ 4) iCloud storage full")
            return nil
        }
        
        print("🦅👷✅ iCloud container URL: \(container)")
        let docs = container.appendingPathComponent("Documents", isDirectory: true)
        print("🦅👷 Target docs path: \(docs.path)")

        if !fm.fileExists(atPath: docs.path) {
            print("🦅👷 BackupWorker: createDirectory \(docs.path)")
            do { 
                try fm.createDirectory(at: docs, withIntermediateDirectories: true)
                print("🦅👷✅ Documents directory created")
            }
            catch { 
                print("🦅👷❌ createDirectory error: \(error)")
                return nil 
            }
        } else {
            print("🦅👷✅ Documents directory already exists")
        }
        
        let dbURL = docs.appendingPathComponent("meta-secret.db", isDirectory: false)
        print("🦅👷 Final DB URL: \(dbURL.path)")
        return dbURL
    }

    static func isInICloudContainer(url: URL) -> Bool {
        print("🦅👷 BackupWorker: isInICloudContainer url: \(url)")
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

    private static func localDBURL() -> URL? {
        print("🦅👷BackupWorker: localDBURL")
        let fm = FileManager.default
        let documentsPath = fm.urls(for: .documentDirectory, in: .userDomainMask).first
        print("🦅👷BackupWorker: localDBURL documentsPath \(String(describing: documentsPath))")
        return documentsPath?.appendingPathComponent("meta-secret.db")
    }

    private static func copyLocalDBTo(url: URL) {
        print("🦅👷BackupWorker: copyLocalDBTo \(url.path)")
        guard let src = localDBURL(), FileManager.default.fileExists(atPath: src.path) else { return }

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
                if FileManager.default.fileExists(atPath: tmp.path) { try? FileManager.default.removeItem(at: tmp) }
                try FileManager.default.copyItem(at: src, to: tmp)

                if exists {
                    _ = try FileManager.default.replaceItemAt(dst, withItemAt: tmp)
                } else {
                    try FileManager.default.moveItem(at: tmp, to: dst)
                }
            } catch let e as NSError {
                print("🦅👷❌BackupWorker: copyLocalDBTo error \(e)")
            }
        }
        if let e = coordinationError {
            print("🦅👷❌BackupWorker: copyLocalDBTo coordination error \(e)")
        }
    }

    private static func copyLocalDBToICloud() {
        print("🦅👷BackupWorker: copyLocalDBToICloud")
        guard let src = localDBURL(), FileManager.default.fileExists(atPath: src.path) else { return }
        guard let dst = iCloudBackupURL() else { return }

        let coordinator = NSFileCoordinator(filePresenter: nil)
        var coordinationError: NSError?

        let exists = (try? dst.checkResourceIsReachable()) ?? false
        let options: NSFileCoordinator.WritingOptions = exists ? .forReplacing : []

        coordinator.coordinate(writingItemAt: dst, options: options, error: &coordinationError) { dstURL in
            do {
                let tmp = dstURL.deletingLastPathComponent().appendingPathComponent(UUID().uuidString)
                if FileManager.default.fileExists(atPath: tmp.path) { try? FileManager.default.removeItem(at: tmp) }
                try FileManager.default.copyItem(at: src, to: tmp)
                if exists {
                    _ = try FileManager.default.replaceItemAt(dstURL, withItemAt: tmp)
                } else {
                    try FileManager.default.moveItem(at: tmp, to: dstURL)
                }
                print("🦅👷 DB has been written to iCloud")
            } catch let e as NSError {
                print("🦅👷❌BackupWorker: copyLocalDBToICloud error \(e)")
            }
        }
        if let e = coordinationError {
            print("🦅👷❌BackupWorker: copyLocalDBToICloud coordination error \(e)")
        }
    }

    static func restoreIfNeeded() {
        print("🦅👷BackupWorker: restoreIfNeeded")
        guard let dst = localDBURL() else {
            print("🦅👷❌BackupWorker: restoreIfNeeded - localDBURL is nil")
            return
        }

        let fm = FileManager.default
        let fileExists = fm.fileExists(atPath: dst.path)
        print("🦅👷BackupWorker: File exists: \(fileExists)")
        
        if fileExists {
            print("🦅👷BackupWorker: DB file already exists with data - skipping restore")
            return
        }

        print("🦅👷BackupWorker: Starting restore process...")
        if let urlFromBookmark = resolveBookmark() {
            let needs = urlFromBookmark.startAccessingSecurityScopedResource()
            defer { if needs { urlFromBookmark.stopAccessingSecurityScopedResource() } }
            _ = ensureDownloadedIfUbiquitous(urlFromBookmark)

            let coordinator = NSFileCoordinator(filePresenter: nil)
            var coordError: NSError?
            coordinator.coordinate(readingItemAt: urlFromBookmark, options: [], error: &coordError) { readURL in
                do { try FileManager.default.copyItem(at: readURL, to: dst) }
                catch { print("🦅👷❌ restoreIfNeeded (bookmark) error \(error)") }
            }
            if let e = coordError { print("🦅👷❌ restoreIfNeeded (bookmark) coordination error \(e)") }
            if FileManager.default.fileExists(atPath: dst.path) { return }
        }

        guard let srcURL = iCloudBackupURL() else { return }
        _ = ensureDownloadedIfUbiquitous(srcURL)

        let coordinator = NSFileCoordinator(filePresenter: nil)
        var coordError: NSError?
        coordinator.coordinate(readingItemAt: srcURL, options: [], error: &coordError) { readURL in
            do { try FileManager.default.copyItem(at: readURL, to: dst) }
            catch { print("🦅👷❌ restoreIfNeeded error \(error)") }
        }
        if let e = coordError { print("🦅👷❌ restoreIfNeeded coordination error \(e)") }
    }

    static func backupIfChanged() {
        print("🦅👷BackupWorker: backupIfChanged")
        guard let src = localDBURL(), FileManager.default.fileExists(atPath: src.path) else { return }

        if let dstURL = resolveBookmark() {
            let needs = dstURL.startAccessingSecurityScopedResource()
            defer { if needs { dstURL.stopAccessingSecurityScopedResource() } }
            _ = ensureDownloadedIfUbiquitous(dstURL)

            let srcDate = (try? src.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            let dstDate = (try? dstURL.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            if srcDate > dstDate { copyLocalDBTo(url: dstURL) }
            return
        }

        guard let dstURL = iCloudBackupURL() else { return }
        _ = ensureDownloadedIfUbiquitous(dstURL)
        let srcDate = (try? src.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
        let dstDate = (try? dstURL.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
        if srcDate > dstDate { copyLocalDBToICloud() }
    }

    static func removeBackup() {
        print("🦅👷BackupWorker: removeBackup")

        if let url = resolveBookmark() {
            let needs = url.startAccessingSecurityScopedResource()
            defer { if needs { url.stopAccessingSecurityScopedResource() } }

            let coordinator = NSFileCoordinator(filePresenter: nil)
            var coordError: NSError?
            coordinator.coordinate(writingItemAt: url, options: .forDeleting, error: &coordError) { delURL in
                do { try FileManager.default.removeItem(at: delURL) }
                catch { print("🦅👷❌BackupWorker: removeBackup (bookmark) error \(error)") }
            }
            if let e = coordError { print("🦅👷❌BackupWorker: removeBackup (bookmark) coordination error \(e)") }
        }

        if let srcURL = iCloudBackupURL() {
            let coordinator = NSFileCoordinator(filePresenter: nil)
            var coordError: NSError?
            coordinator.coordinate(writingItemAt: srcURL, options: .forDeleting, error: &coordError) { delURL in
                do { try FileManager.default.removeItem(at: delURL) }
                catch { print("🦅👷❌BackupWorker: removeBackup error \(error)") }
            }
            if let e = coordError { print("🦅👷❌BackupWorker: removeBackup coordination error \(e)") }
        }
    }

    @discardableResult
    private static func ensureDownloadedIfUbiquitous(_ url: URL, timeout: TimeInterval = 10) -> Bool {
        let u = url

        if let rv = try? u.resourceValues(forKeys: [.isUbiquitousItemKey]),
           rv.isUbiquitousItem != true {
            print("🦅👷BackupWorker: Not Ubiquitous")
            return true
        }

        func status() -> URLUbiquitousItemDownloadingStatus? {
            guard let s = (try? u.resourceValues(forKeys: [.ubiquitousItemDownloadingStatusKey]))?
                .allValues[.ubiquitousItemDownloadingStatusKey] as? String else { return nil }
            return URLUbiquitousItemDownloadingStatus(rawValue: s)
        }

        if let s = status(), s == .downloaded || s == .current { return true }

        do { try FileManager.default.startDownloadingUbiquitousItem(at: u) }
        catch { print("🦅👷BackupWorker: startDownloadingUbiquitousItem error: \(error)") }

        // Use shorter intervals and fewer iterations to avoid blocking UI thread
        let deadline = Date().addingTimeInterval(min(timeout, 3.0)) // Max 3 seconds
        let checkInterval: TimeInterval = 0.1 // Check every 100ms
        var iterations = 0
        let maxIterations = Int(timeout / checkInterval)
        
        while Date() < deadline && iterations < maxIterations {
            if let s = status(), s == .downloaded || s == .current { 
                print("🦅👷BackupWorker: Download completed after \(iterations) iterations")
                return true 
            }
            
            // Use DispatchQueue.main.async to prevent blocking
            Thread.sleep(forTimeInterval: checkInterval)
            iterations += 1
            
            // Allow system to process other events
            if iterations % 10 == 0 {
                print("🦅👷BackupWorker: Still downloading... iteration \(iterations)")
            }
        }

        print("🦅👷BackupWorker: Download timeout for: \(u) after \(iterations) iterations")
        return false
    }

    private static func resolveBookmark() -> URL? {
        guard let b64 = SwiftBridge().getString(key: "bdBackUpBookmark") else {
            print("🦅👷 No bookmark key in storage")
            return nil
        }
        guard let data = Data(base64Encoded: b64) else {
            print("🦅👷 Bookmark base64 decode failed")
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
                    print("🦅👷 Bookmark was stale – refreshed")
                }
            }
            print("🦅👷 Resolved bookmark URL: \(url)")
            return url
        } catch {
            print("🦅👷 resolveBookmark error: \(error)")
            let nsError = error as NSError
            if nsError.code == 257 { // Permission denied - typical after app reinstall
                print("🦅👷 Bookmark lost permissions after app reinstall - clearing invalid bookmark")
                _ = SwiftBridge().saveString(key: "bdBackUpBookmark", value: "")
            } else if nsError.code == 260 { // File not found
                print("🦅👷 Bookmark points to non-existent file - clearing")
                _ = SwiftBridge().saveString(key: "bdBackUpBookmark", value: "")
            }
            return nil
        }
    }

    static func hasICloudBackup() -> Bool {
        print("🦅👷 BackupWorker: hasICloudBackup")
        let fm = FileManager.default

        if let url = resolveBookmark() {
            let needs = url.startAccessingSecurityScopedResource()
            defer { if needs { url.stopAccessingSecurityScopedResource() } }
            _ = ensureDownloadedIfUbiquitous(url)
            let existsReach = (try? url.checkResourceIsReachable()) ?? false
            let existsFS = fm.fileExists(atPath: url.path)
            print("🦅👷 Bookmark check: reachable=\(existsReach) fileExists=\(existsFS)")
            if existsReach || existsFS {
                printFileDetails(url: url, source: "bookmark")
                print("🦅👷 BackupWorker: Exists (bookmark)")
                return true
            }
        }

        if let container = fm.url(forUbiquityContainerIdentifier: containerId) {
            let docs = container.appendingPathComponent("Documents", isDirectory: true)
            let iCloudDB = docs.appendingPathComponent("meta-secret.db")
            let exists = ((try? iCloudDB.checkResourceIsReachable()) ?? false) || fm.fileExists(atPath: iCloudDB.path)
            if exists {
                printFileDetails(url: iCloudDB, source: "app container")
                print("🦅👷 BackupWorker: Exists (app container)")
                return true
            }
        }

        print("🦅👷 BackupWorker: Not Exists")
        return false
    }
    
    private static func printFileDetails(url: URL, source: String) {
        let fm = FileManager.default
        print("🦅👷📁 File details (\(source)):")
        print("🦅👷📁 Path: \(url.path)")
        
        do {
            let attributes = try fm.attributesOfItem(atPath: url.path)
            let fileSize = attributes[.size] as? NSNumber ?? 0
            let creationDate = attributes[.creationDate] as? Date
            let modificationDate = attributes[.modificationDate] as? Date
            let fileType = attributes[.type] as? FileAttributeType
            
            print("🦅👷📁 Size: \(fileSize) bytes (\(ByteCountFormatter.string(fromByteCount: fileSize.int64Value, countStyle: .file)))")
            print("🦅👷📁 Created: \(creationDate?.description ?? "unknown")")
            print("🦅👷📁 Modified: \(modificationDate?.description ?? "unknown")")
            print("🦅👷📁 Type: \(fileType?.rawValue ?? "unknown")")
            
            // Check if it's ubiquitous (iCloud)
            if let resourceValues = try? url.resourceValues(forKeys: [
                .isUbiquitousItemKey,
                .ubiquitousItemDownloadingStatusKey
            ]) {
                let isUbiquitous = resourceValues.isUbiquitousItem ?? false
                let downloadStatus = resourceValues.ubiquitousItemDownloadingStatus?.rawValue ?? "unknown"
                let isDownloaded = downloadStatus == URLUbiquitousItemDownloadingStatus.current.rawValue
                
                print("🦅👷📁 iCloud file: \(isUbiquitous)")
                print("🦅👷📁 Download status: \(downloadStatus)")
                print("🦅👷📁 Downloaded: \(isDownloaded)")
            }
        } catch {
            print("🦅👷📁 Error getting file attributes: \(error)")
        }
    }
}