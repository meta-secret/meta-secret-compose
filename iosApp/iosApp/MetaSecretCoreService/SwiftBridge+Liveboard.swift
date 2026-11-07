import Foundation

extension SwiftBridge {
    @objc public func clusterDistributionChecked(_ name: String, _ value: String) -> String {
        liveboardClusterDistribution(name, value)
    }

    @objc public func recoverChecked(_ secretId: String) -> String {
        liveboardRecover(secretId)
    }

    @objc public func decideJoinRequest(_ requestJson: String) -> String {
        liveboardDecideJoin(requestJson)
    }

    @objc public func decideRemoveMember(_ requestJson: String) -> String {
        liveboardDecideRemove(requestJson)
    }

    @objc public func deleteSecretChecked(_ secretId: String) -> String {
        liveboardDeleteSecret(secretId)
    }

    @objc public func removeMemberChecked(_ targetDeviceId: String) -> String {
        liveboardRemoveMember(targetDeviceId)
    }

    @objc public func reconcileProducerReceipt(_ reference: String) -> String {
        liveboardReconcile(reference)
    }

    @objc public func continueLocalRemoveChecked(_ reference: String) -> String {
        liveboardContinueRemove(reference)
    }
}

// Distinct top-level names avoid resolving the extension methods recursively.
private func liveboardClusterDistribution(_ name: String, _ value: String) -> String {
    clusterDistributionChecked(name: name, secret: value)
}

private func liveboardRecover(_ secretId: String) -> String {
    recoverChecked(secretId: secretId)
}

private func liveboardDecideJoin(_ requestJson: String) -> String {
    decideJoinRequest(requestJson: requestJson)
}

private func liveboardDecideRemove(_ requestJson: String) -> String {
    decideRemoveMember(requestJson: requestJson)
}

private func liveboardDeleteSecret(_ secretId: String) -> String {
    deleteSecretChecked(secretId: secretId)
}

private func liveboardRemoveMember(_ targetDeviceId: String) -> String {
    removeMemberChecked(targetDeviceId: targetDeviceId)
}

private func liveboardReconcile(_ reference: String) -> String {
    reconcileProducerReceipt(referenceJson: reference)
}

private func liveboardContinueRemove(_ reference: String) -> String {
    continueLocalRemoveChecked(referenceJson: reference)
}
