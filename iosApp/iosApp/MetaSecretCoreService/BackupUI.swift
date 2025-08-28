//
//  BackupUI.swift
//  iosApp
//
//  Extracted UI for backup picker from SwiftBridge
//

import Foundation
import UIKit
import ObjectiveC

final class BackupUI: NSObject {
    static let shared = BackupUI()

    private let backupKey = "bdBackUp"

    func presentBackupPicker(initialMessage: String, okTitle: String, warningMessage: String, warningOkTitle: String, warningCancelTitle: String) {
        DispatchQueue.main.async {
            guard let presenter = self.topMostViewController() else { return }
            let alert = UIAlertController(title: nil, message: initialMessage, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: okTitle, style: .default) { _ in
                self.presentSystemPicker(from: presenter, warningMessage: warningMessage, warningOkTitle: warningOkTitle, warningCancelTitle: warningCancelTitle)
            })
            presenter.present(alert, animated: true)
        }
    }

    private func presentSystemPicker(from presenter: UIViewController, warningMessage: String, warningOkTitle: String, warningCancelTitle: String) {
        let fm = FileManager.default
        let documentsPath = fm.urls(for: .documentDirectory, in: .userDomainMask).first!
        let src = documentsPath.appendingPathComponent("meta-secret.db")
        guard fm.fileExists(atPath: src.path) else { return }
        let picker: UIDocumentPickerViewController
        if #available(iOS 14.0, *) {
            picker = UIDocumentPickerViewController(forExporting: [src], asCopy: true)
        } else {
            picker = UIDocumentPickerViewController(url: src, in: .exportToService)
        }
        let delegate = PickerDelegate(
            warningMessage: warningMessage,
            warningOkTitle: warningOkTitle,
            warningCancelTitle: warningCancelTitle,
            displayPathKey: backupKey
        )
        picker.delegate = delegate
        objc_setAssociatedObject(picker, Unmanaged.passUnretained(picker).toOpaque(), delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        presenter.present(picker, animated: true)
    }

    private func showWarningAlert(from presenter: UIViewController, warningMessage: String, warningOkTitle: String, warningCancelTitle: String) {
        let alert = UIAlertController(title: nil, message: warningMessage, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: warningOkTitle, style: .default) { _ in })
        alert.addAction(UIAlertAction(title: warningCancelTitle, style: .cancel) { _ in
            self.presentSystemPicker(from: presenter, warningMessage: warningMessage, warningOkTitle: warningOkTitle, warningCancelTitle: warningCancelTitle)
        })
        presenter.present(alert, animated: true)
    }

    private func topMostViewController(base: UIViewController? = {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        return window?.rootViewController
    }()) -> UIViewController? {
        if let nav = base as? UINavigationController { return topMostViewController(base: nav.visibleViewController) }
        if let tab = base as? UITabBarController, let selected = tab.selectedViewController { return topMostViewController(base: selected) }
        if let presented = base?.presentedViewController { return topMostViewController(base: presented) }
        return base
    }

    private final class PickerDelegate: NSObject, UIDocumentPickerDelegate {
        let warningMessage: String
        let warningOkTitle: String
        let warningCancelTitle: String
        let displayPathKey: String

        init(warningMessage: String, warningOkTitle: String, warningCancelTitle: String, displayPathKey: String) {
            self.warningMessage = warningMessage
            self.warningOkTitle = warningOkTitle
            self.warningCancelTitle = warningCancelTitle
            self.displayPathKey = displayPathKey
        }

        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
            guard let presenter = (controller.presentingViewController ?? controller.parent) else { return }
            BackupUI.shared.showWarningAlert(from: presenter, warningMessage: warningMessage, warningOkTitle: warningOkTitle, warningCancelTitle: warningCancelTitle)
        }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let url = urls.first else {
                guard let presenter = (controller.presentingViewController ?? controller.parent) else { return }
                BackupUI.shared.showWarningAlert(from: presenter, warningMessage: warningMessage, warningOkTitle: warningOkTitle, warningCancelTitle: warningCancelTitle)
                return
            }
            // Save display path
            _ = SwiftBridge().saveString(key: displayPathKey, value: url.path)
            // Immediately copy DB to chosen location
            BackupWorker.copyLocalDBTo(url: url)
        }
    }
}

final class BackupWorker {
    static func localDBURL() -> URL? {
        let fm = FileManager.default
        let documentsPath = fm.urls(for: .documentDirectory, in: .userDomainMask).first
        return documentsPath?.appendingPathComponent("meta-secret.db")
    }

    static func copyLocalDBTo(url: URL) {
        guard let src = localDBURL(), FileManager.default.fileExists(atPath: src.path) else { return }
        let coordinator = NSFileCoordinator(filePresenter: nil)
        var coordinationError: NSError?
        var innerError: NSError?
        coordinator.coordinate(writingItemAt: url, options: .forReplacing, error: &coordinationError) { dst in
            do {
                let tmp = dst.deletingLastPathComponent().appendingPathComponent(UUID().uuidString)
                if FileManager.default.fileExists(atPath: tmp.path) {
                    try? FileManager.default.removeItem(at: tmp)
                }
                try FileManager.default.copyItem(at: src, to: tmp)
                _ = try FileManager.default.replaceItemAt(dst, withItemAt: tmp)
            } catch let e as NSError {
                innerError = e
            }
        }
    }

    static func restoreIfNeeded() {
        guard let dst = localDBURL(), !FileManager.default.fileExists(atPath: dst.path) else { return }
        guard let path = SwiftBridge().getString(key: "bdBackUp") else { return }
        let srcURL = URL(fileURLWithPath: path)
        do { try FileManager.default.copyItem(at: srcURL, to: dst) } catch { /* ignore */ }
    }

    static func backupIfChanged() {
        guard let src = localDBURL(), FileManager.default.fileExists(atPath: src.path) else { return }
        guard let path = SwiftBridge().getString(key: "bdBackUp") else { return }
        let dstURL = URL(fileURLWithPath: path)
        let srcDate = (try? src.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
        let dstDate = (try? dstURL.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
        if srcDate > (dstDate) {
            copyLocalDBTo(url: dstURL)
        }
    }
}



