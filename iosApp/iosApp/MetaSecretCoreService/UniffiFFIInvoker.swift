import Foundation

/// Forwards to top-level UniFFI `public func` symbols from `mobile_uniffi.swift`.
/// All calls live here so `SwiftBridge` instance methods never shadow the same-named globals.
enum UniffiFFIInvoker {
    static func invokeGenerateMasterKey() -> String {
        generateMasterKey()
    }

    static func invokeGetState() -> String {
        getState()
    }

    static func invokeSignUp() -> String {
        signUp()
    }

    static func invokeInitIos(masterKey: String) -> String {
        initIos(masterKey: masterKey)
    }

    static func invokeGenerateUserCreds(vaultName: String) -> String {
        generateUserCreds(vaultName: vaultName)
    }

    static func invokeUpdateMembership(candidate: String, actionUpdate: String) -> String {
        updateMembership(candidate: candidate, actionUpdate: actionUpdate)
    }

    static func invokeSplitSecret(secretId: String, secret: String) -> String {
        splitSecret(secretId: secretId, secret: secret)
    }

    static func invokeFindClaimBy(secretId: String) -> String {
        findClaimBy(secretId: secretId)
    }

    static func invokeFindClaimIdBy(secretId: String) -> String {
        findClaimIdBy(secretId: secretId)
    }

    static func invokeRecover(secretId: String) -> String {
        recover(secretId: secretId)
    }

    static func invokeAcceptRecover(claimId: String) -> String {
        acceptRecover(claimId: claimId)
    }

    static func invokeDeclineRecover(claimId: String) -> String {
        declineRecover(claimId: claimId)
    }

    static func invokeSendDeclineCompletion(claimId: String) -> String {
        sendDeclineCompletion(claimId: claimId)
    }

    static func invokeShowRecovered(secretId: String) -> String {
        showRecovered(secretId: secretId)
    }

    static func invokeCleanUpDatabase() -> String {
        cleanUpDatabase()
    }

    static func invokeMetaWsStart() -> String {
        metaWsStart()
    }

    static func invokeMetaWsStop() -> String {
        metaWsStop()
    }

    static func invokeMetaWsWaitNextEvent(timeoutMs: UInt32) -> Bool {
        metaWsWaitNextEvent(timeoutMs: timeoutMs)
    }
}
