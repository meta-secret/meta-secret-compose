//
//  BackupUI.swift
//  iosApp
//
//  Extracted UI for backup picker from SwiftBridge
//

import Foundation
import UIKit
import ObjectiveC

@MainActor
final class BackupUI: NSObject {
    static let shared = BackupUI()

    private let iCloudContainerId = "iCloud.metasecret.project.com"

    func presentBackupPicker(initialMessage: String, okTitle: String, warningMessage: String, warningOkTitle: String, warningCancelTitle: String, backupKey: String) {
        let fm = FileManager.default
        let documentsPath = fm.urls(for: .documentDirectory, in: .userDomainMask).first!
        let src = documentsPath.appendingPathComponent("meta-secret.db")
        guard !fm.fileExists(atPath: src.path) else {
            print("🦅 Swift: presentSystemPicker local file Exists. Alert doesn't need")
            return
        }
        print("🦅 Swift: presentSystemPicker local file Not Exists")

        guard let presenter = self.topMostViewController() else { return }
        let alert = UIAlertController(title:nil, message:initialMessage, preferredStyle:.alert)
        alert.addAction(UIAlertAction(title: okTitle, style: .default) { _ in
            self.presentSystemPicker(from: presenter, warningMessage: warningMessage, warningOkTitle: warningOkTitle, warningCancelTitle: warningCancelTitle, backupKey: backupKey)
        })
        presenter.present(alert, animated: true)
    }

    private func presentSystemPicker(from presenter: UIViewController, warningMessage: String, warningOkTitle: String, warningCancelTitle: String, backupKey: String) {
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

    private func showWarningAlert(from presenter: UIViewController, warningMessage: String, warningOkTitle: String, warningCancelTitle: String, backupKey: String) {
        let alert = UIAlertController(title: nil, message: warningMessage, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: warningOkTitle, style: .default) { _ in })
        alert.addAction(UIAlertAction(title: warningCancelTitle, style: .cancel) { _ in
            self.presentSystemPicker(from: presenter, warningMessage: warningMessage, warningOkTitle: warningOkTitle, warningCancelTitle: warningCancelTitle, backupKey: backupKey)
        })
        presenter.present(alert, animated: true)
    }

    @MainActor
    private func topMostViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
        .compactMap {
            $0 as? UIWindowScene
        }
        .flatMap {
            $0.windows
        }
        .first {
            $0.isKeyWindow
        }
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
            guard let presenter = (controller.presentingViewController ?? controller.parent) else {
                return
            }
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
                guard let presenter = (controller.presentingViewController ?? controller.parent) else {
                    print("🦅✅SwiftUI: document Picker return")
                    return
                }
                BackupUI.shared.showWarningAlert(
                    from: presenter,
                    warningMessage: warningMessage,
                    warningOkTitle: warningOkTitle,
                    warningCancelTitle: warningCancelTitle,
                    backupKey: backupKey
                )
                print("🦅✅SwiftUI: document Picker can't save")
                return
            }

            print("🦅✅SwiftUI: target URL: \(url)")
            if !BackupWorker.isInICloudContainer(url: url) {
                print("🦅✅SwiftUI: isInICloudContainer false")
                guard let presenter = (controller.presentingViewController ?? controller.parent) else {
                    print("🦅✅SwiftUI: document Picker return2")
                    return
                }
                BackupUI.shared.showWarningAlert(
                    from: presenter,
                    warningMessage: warningMessage,
                    warningOkTitle: warningOkTitle,
                    warningCancelTitle: warningCancelTitle,
                    backupKey: backupKey
                )
                print("🦅✅SwiftUI: document Picker can't init")
                return
            }

            print("🦅✅SwiftUI: isInICloudContainer true")
            if let bookmark = try? url.bookmarkData(options: [],
                                                    includingResourceValuesForKeys: nil,
                                                    relativeTo: nil) {
                let b64 = bookmark.base64EncodedString()
                _ = SwiftBridge().saveString(key: "bdBackUpBookmark", value: b64)
            }
            _ = SwiftBridge().saveString(key: backupKey, value: url.path)
        }
    }
}

final class BackupWorker {
    private static let containerId = "iCloud.metasecret.project.com"

    private static func iCloudAvailable() -> Bool {
        return FileManager.default.ubiquityIdentityToken != nil
    }

    private static func iCloudBackupURL() -> URL? {
        print("🦅👷 BackupWorker: iCloudBackupURL")
        guard iCloudAvailable() else {
            print("🦅👷❌ iCloud unavailable (no ubiquityIdentityToken)")
            return nil
        }

        let fm = FileManager.default
        guard let container = fm.url(forUbiquityContainerIdentifier: containerId) else {
            print("🦅👷❌BackupWorker: iCloud container is not available")
            return nil
        }
        let docs = container.appendingPathComponent("Documents", isDirectory: true)

        if !fm.fileExists(atPath: docs.path) {
            print("🦅👷 BackupWorker: file DOESN'T Exist! createDirectory")
            do {
                try fm.createDirectory(at: docs, withIntermediateDirectories: true)
            } catch {
                print("🦅👷❌ createDirectory error: \(error)")
                return nil
            }
        }
        return docs.appendingPathComponent("meta-secret.db", isDirectory: false)
    }

    static func isInICloudContainer(url: URL) -> Bool {
        print("🦅👷 BackupWorker: isInICloudContainer url: \(url)")
        let fm = FileManager.default
        let u = url.standardizedFileURL

        if let container = fm.url(forUbiquityContainerIdentifier: containerId) {
            if u.path.hasPrefix(container.standardizedFileURL.path) {
               return true
           }
        }

        let path = u.path
        if path.contains("/Mobile Documents/") || path.contains("com~apple~CloudDocs") {
            return true
        }

        if let vals = try? u.resourceValues(forKeys: [.isUbiquitousItemKey]),
           vals.isUbiquitousItem == true {
            return true
        }

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
                if FileManager.default.fileExists(atPath: tmp.path) {
                    try? FileManager.default.removeItem(at: tmp)
                }
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
                if FileManager.default.fileExists(atPath: tmp.path) {
                    try? FileManager.default.removeItem(at: tmp)
                }
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
        guard let dst = localDBURL(), !FileManager.default.fileExists(atPath: dst.path) else { return }
        guard let srcURL = iCloudBackupURL() else { return }

        _ = ensureDownloadedIfUbiquitous(srcURL)

        let coordinator = NSFileCoordinator(filePresenter: nil)
        var coordError: NSError?

        coordinator.coordinate(readingItemAt: srcURL, options: [], error: &coordError) { readURL in
            do {
                try FileManager.default.copyItem(at: readURL, to: dst)
            } catch {
                print("🦅👷❌ restoreIfNeeded error \(error)")
            }
        }
        if let e = coordError {
            print("🦅👷❌ restoreIfNeeded coordination error \(e)")
        }
    }

    static func backupIfChanged() {
        print("🦅👷BackupWorker: backupIfChanged")
        guard let src = localDBURL(), FileManager.default.fileExists(atPath: src.path) else { return }

        if let b64 = SwiftBridge().getString(key: "bdBackUpBookmark"),
           let data = Data(base64Encoded: b64) {
            var stale = false
            if let dstURL = try? URL(resolvingBookmarkData: data, options: [], relativeTo: nil, bookmarkDataIsStale: &stale) {
                let needs = dstURL.startAccessingSecurityScopedResource()
                defer { if needs { dstURL.stopAccessingSecurityScopedResource() } }
                _ = ensureDownloadedIfUbiquitous(dstURL)

                let srcDate = (try? src.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                let dstDate = (try? dstURL.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                if srcDate > dstDate { copyLocalDBTo(url: dstURL) }
                return
            }
        }

        guard let dstURL = iCloudBackupURL() else { return }
        _ = ensureDownloadedIfUbiquitous(dstURL)
        let srcDate = (try? src.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
        let dstDate = (try? dstURL.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
        if srcDate > dstDate { copyLocalDBToICloud() }
    }

    static func removeBackup() {
        print("🦅👷BackupWorker: removeBackup")
        guard let srcURL = iCloudBackupURL() else { return }

        let coordinator = NSFileCoordinator(filePresenter: nil)
        var coordError: NSError?
        coordinator.coordinate(writingItemAt: srcURL, options: .forDeleting, error: &coordError) { delURL in
            do {
                try FileManager.default.removeItem(at: delURL)
            } catch {
                print("🦅👷❌BackupWorker: removeBackup error \(error)")
            }
        }
        if let e = coordError {
            print("🦅👷❌BackupWorker: removeBackup coordination error \(e)")
        }
    }

    @discardableResult
    private static func ensureDownloadedIfUbiquitous(_ url: URL, timeout: TimeInterval = 10) -> Bool {
        let u = url

        // Не iCloud — выходим
        if let rv = try? u.resourceValues(forKeys: [.isUbiquitousItemKey]),
           rv.isUbiquitousItem != true {
            return true
        }

        // Читаем статус как enum
        func status() -> URLUbiquitousItemDownloadingStatus? {
            guard let s = (try? u.resourceValues(forKeys: [.ubiquitousItemDownloadingStatusKey]))?
                .allValues[.ubiquitousItemDownloadingStatusKey] as? String else {
                return nil
            }
            return URLUbiquitousItemDownloadingStatus(rawValue: s)
        }

        // Уже скачан/актуален?
        if let s = status(), s == .downloaded || s == .current {
            return true
        }

        // Стартуем загрузку
        do { try FileManager.default.startDownloadingUbiquitousItem(at: u) }
        catch { print("🦅👷BackupWorker: startDownloadingUbiquitousItem error: \(error)") }

        // Ждём до timeout
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if let s = status(), s == .downloaded || s == .current {
                return true
            }
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.05))
        }

        print("🦅👷BackupWorker: Download timeout for: \(u)")
        return false
    }
}



