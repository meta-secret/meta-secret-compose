// Backward-compatible aliases for existing SwiftBridge code.

public func uniffiMobileGenerateMasterKey() -> String { generateMasterKey() }
public func uniffiMobileInitIos(masterKey: String) -> String { initIos(masterKey: masterKey) }
public func uniffiMobileInitIosWithDevice(masterKey: String, deviceName: String, deviceType: String) -> String {
    initIosWithDevice(masterKey: masterKey, deviceName: deviceName, deviceType: deviceType)
}
public func uniffiMobileInitAndroid(masterKey: String) -> String { initAndroid(masterKey: masterKey) }
public func uniffiMobileInitAndroidWithDevice(masterKey: String, deviceName: String, deviceType: String) -> String {
    initAndroidWithDevice(masterKey: masterKey, deviceName: deviceName, deviceType: deviceType)
}
public func uniffiMobileGetState() -> String { getState() }
public func uniffiMobileGenerateUserCreds(vaultName: String) -> String { generateUserCreds(vaultName: vaultName) }
public func uniffiMobileSignUp() -> String { signUp() }
public func uniffiMobileUpdateMembership(candidate: String, actionUpdate: String) -> String {
    updateMembership(candidate: candidate, actionUpdate: actionUpdate)
}
public func uniffiMobileSplitSecret(secretId: String, secret: String) -> String {
    splitSecret(secretId: secretId, secret: secret)
}
public func uniffiMobileFindClaimBy(secretId: String) -> String { findClaimBy(secretId: secretId) }
public func uniffiMobileFindClaimIdBy(secretId: String) -> String { findClaimIdBy(secretId: secretId) }
public func uniffiMobileRecover(secretId: String) -> String { recover(secretId: secretId) }
public func uniffiMobileAcceptRecover(claimId: String) -> String { acceptRecover(claimId: claimId) }
public func uniffiMobileDeclineRecover(claimId: String) -> String { declineRecover(claimId: claimId) }
public func uniffiMobileSendDeclineCompletion(claimId: String) -> String { sendDeclineCompletion(claimId: claimId) }
public func uniffiMobileShowRecovered(secretId: String) -> String { showRecovered(secretId: secretId) }
public func uniffiMobileCleanUpDatabase() -> String { cleanUpDatabase() }
