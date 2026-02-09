package core

sealed class LogTag(val displayName: String) {
    abstract class Message<out T : LogTag>(val text: String) {
        abstract val tag: T
    }

    object SplashVM : LogTag("🚀SplashScreenViewModel") {
        sealed class Message(text: String) : LogTag.Message<SplashVM>(text) {
            override val tag: SplashVM = SplashVM

            object DataCleanupStart : Message("Starting complete data cleanup")
            object DataCleanupCompleted : Message("Data cleanup completed")
            object BiometricNeedRegistration : Message("BiometricState NeedRegistration")
            object BiometricAvailable : Message("Biometric is available")
            object BiometricApproved : Message("Biometric is approved")
            object BiometricFailed : Message("Biometric is failed:")
            object BiometricProhibited : Message("Biometric is prohibited")
            object MoveToMain : Message("Move to Main")
            object MoveToSignUp : Message("Move to Sign up")
            object MoveToOnboarding : Message("Move to Onboarding")
            object BiometricStateSuccess : Message("BiometricState Success")
            object AuthCheck : Message("Auth check")
        }
    }

    object DevicesVM : LogTag("📱DevicesScreenViewModel") {
        sealed class Message(text: String) : LogTag.Message<DevicesVM>(text) {
            override val tag: DevicesVM = DevicesVM
            
            object JoinRequestStateReceived : Message("New state for Join request has been gotten")
            object LoadDevicesList : Message("Need to load devices list")
            object UpdateCandidateStart : Message("Start Update candidate")
            object UpdateCandidateSuccess : Message("Update candidate")
            object UpdateCandidateFailed : Message("Update failed")
            object UpdateError : Message("Update error")
            object BiometricError: Message("Biometric error")
            object SelectDevice : Message("Select device with Id")
        }
    }

    object MainVM : LogTag("🧭MainScreenViewModel") {
        sealed class Message(text: String) : LogTag.Message<MainVM>(text) {
            override val tag: MainVM = MainVM
            
            object FollowResponsibleToAcceptJoin : Message("Start to follow RESPONSIBLE_TO_ACCEPT_JOIN")
            object ReadyToRecoverSignal : Message("READY_TO_RECOVER signal has been caught")
            object ReadyToRecoverExistingSecrets : Message("READY_TO_RECOVER existingSecretsIds")
            object ReadyToRecoverNewRequests : Message("READY_TO_RECOVER newRequests")
            object ReadyToRecoverNothing : Message("READY_TO_RECOVER nothing to handle")
            object ReadyToShowSecret : Message("READY_TO_SHOW secret by secretId")
            object ShowNextRecoverPrompt : Message("showNextRecoverPrompt")
            object RecoverDeclined : Message("Recover is declined")
            object DeclineRecoverCalled : Message("declineRecover called for claimId")
            object DeclineRecoverSuccess : Message("declineRecover success for claimId")
            object DeclineRecoverFailed : Message("declineRecover failed for claimId")
            object RecoverDeclinedOnSender : Message("Recover declined on sender device")
            object CompleteDeclinedClaimSuccess : Message("completeDeclinedClaim success for claimId")
            object CompleteDeclinedClaimFailed : Message("completeDeclinedClaim failed for claimId")
            object RecoverAccepted : Message("Recover is accepted")
            object BiometricAuthSuccess : Message("Biometric authentication successful")
            object AcceptRecoverCalled : Message("acceptRecover called for claimId")
            object AcceptRecoverFailed : Message("acceptRecover failed for claimId")
            object BiometricAuthFailed : Message("Biometric authentication failed")
            object BiometricAuthFallback : Message("Biometric authentication fallback")
            object ClearingSecretId : Message("Clearing secretIdToShow")
        }
    }

    object OnboardingVM : LogTag("🎯OnboardingViewModel") {
        sealed class Message(text: String) : LogTag.Message<OnboardingVM>(text) {
            override val tag: OnboardingVM = OnboardingVM
            
            object MoveToMain : Message("Move to Main")
            object MoveToSignUp : Message("Move to Sign Up")
        }
    }

    object SignInVM : LogTag("🔐SignInScreenViewModel") {
        sealed class Message(text: String) : LogTag.Message<SignInVM>(text) {
            override val tag: SignInVM = SignInVM
            
            object VaultStateDetected : Message("Vault state detected")
            object UserIsOutsider : Message("User is outsider, waiting for approval")
            object UserIsMember : Message("User is already a member, sign in completed")
            object NoVaultInfoIdle : Message("No vault info, going to idle")
            object WaitingForSignUp : Message("Waiting for SignUp")
            object StartListeningJoinAccept : Message("Start listening for Join accept signal")
            object StartSignUp : Message("Start Sign Up")
            object SignUpSuccess : Message("Sign up is successfull")
            object SignUpUnknownState : Message("Unknown state for sign up")
            object GeneratedMasterKey : Message("Generated master key")
            object SubscribeJoinResponse : Message("Subscribe SignIn screen for Join Response Signal")
            object GotDeclinedSignal : Message("Got Declined signal")
            object JoiningInProgress : Message("Joining still in progress")
            object JoiningNotFollowing : Message("Joining not following yet")
            object BiometricAuthSuccess : Message("Biometric authentication successful")
            object GotAcceptedSignal : Message("Got Accepted signal")
            object BiometricAuthFailed : Message("Biometric authentication failed")
            object BiometricAuthFallback : Message("Biometric authentication fallback")
            object NotPending : Message("It's not a pending")
        }
    }

    object AppManager : LogTag("🛠️MetaSecretAppManager") {
        sealed class Message(text: String) : LogTag.Message<AppManager>(text) {
            override val tag: AppManager = AppManager
            
            object MasterKeyExist : Message("is Master key exist")
            object IsInitiated : Message("is initiated")
            object InitError : Message("init error")
            object InitErrorNoMasterKey : Message("init error: No master key found")
            object CurrentState : Message("currentState is")
            object FailedToParseStateJson : Message("Failed to parse state JSON")
            object CachedDeviceId : Message("Cached deviceId")
            object CachedVaultName : Message("Cached vaultName")
            object VaultInfo : Message("vaultInfo is")
            object FailedToParseVaultInfoJson : Message("Failed to parse vaultInfo JSON")
            object VaultEvents : Message("vaultEvents is")
            object FailedToParseVaultEventsJson : Message("Failed to parse vaultEvents JSON")
            object RequestsCount : Message("requestsCount is")
            object FailedToParseRequestsCountJson : Message("Failed to parse requestsCount JSON")
            object GetJoinRequests : Message("getJoinRequests is")
            object FailedToParseGetJoinRequestsJson : Message("Failed to parse getJoinRequests JSON")
            object VaultSummary : Message("vaultSummary is")
            object FailedToParseVaultSummaryJson : Message("Failed to parse vaultSummary JSON")
            object UpdateCandidateResult : Message("update candidate result is")
            object FailedToParseUpdateCandidateJson : Message("Failed to parse updatecandidate candidate JSON")
            object GetUserDataByDeviceId : Message("getUserDataBy deviceId")
            object FailedToParseGetUserDataByIdJson : Message("Failed to parse getUserDataById JSON")
            object SplitSecretStarted : Message("split Secret started")
            object SplitSecretResult : Message("split Secret result is")
            object FailedToParseSplitSecretJson : Message("Failed to parse split Secret JSON")
            object FindClaimStarted : Message("find claim started")
            object FailedToParseFindClaimJson : Message("Failed to parse find claim JSON")
            object RecoverSecretIdNull : Message("recover secret Id is Null")
            object RecoverRequestResult : Message("recover request result is")
            object FailedToParseRecoverRequestJson : Message("Failed to parse recover request JSON")
            object AcceptRecoverStarted : Message("Accept recover started")
            object AcceptRecoverResult : Message("Accept recover result is")
            object FailedToParseAcceptRecoverJson : Message("Failed to parse Accept Recover JSON")
            object DeclineRecoverStarted : Message("Decline recover started")
            object DeclineRecoverResult : Message("Decline recover result is")
            object FailedToParseDeclineRecoverJson : Message("Failed to parse Decline Recover JSON")
            object CompleteDeclinedClaimStarted : Message("Complete declined claim started")
            object CompleteDeclinedClaimResult : Message("Complete declined claim result is")
            object FailedToParseCompleteDeclinedClaimJson : Message("Failed to parse Complete Declined Claim JSON")
            object ShowRecovered : Message("showRecovered")
            object ShowRecoveredSuccess : Message("showRecovered success")
            object FailedToParseShowRecoveredJson : Message("Failed to parse showRecovered JSON")
            object GetSecretsFromVaultResult : Message("getSecretsFromVault result is")
            object FailedToParseGetSecretsFromVaultJson : Message("Failed to parse getSecretsFromVault JSON")
        }
    }

    object SocketHandler : LogTag("🔌MetaSecretSocketHandler") {
        sealed class Message(text: String) : LogTag.Message<SocketHandler>(text) {
            override val tag: SocketHandler = SocketHandler
            
            object Init : Message("init")
            object UpdateActionsToFollow : Message("Update actions to follow")
            object ActualActionsToFollow : Message("Actual actions to follow")
            object TimerStarted : Message("Timer is started")
            object FireTimer : Message("Fire the timer!")
            object AppStateReceived : Message("AppState is")
            object NeedShowAskToJoin : Message("Need to show Ask to join pop up")
            object WaitingForJoinResponse : Message("Waiting for join response")
            object SocketEmmitOnStateResponse : Message("Emmit signal on state response")
            object SocketEmmitOnRecoverState : Message("Emmit signal on recover")
            object WaitingForShowSecret : Message("Waiting for show secret")
            object NoSubscriptions : Message("NO any subscriptions")
            object WaitingForRecoverRequest : Message("Waiting for recover request")
            object FoundClaims : Message("Found claims")
            object ReadyToRecover : Message("Ready to recover for passIds")
            object NothingToRecover : Message("Nothing to recover")
            object NoClaimsFound : Message("No claims found")
            object CheckingRecoverSentStatus : Message("Checking recover sent status")
            object CheckingRecoverSentStatusClaims : Message("Checking recover sent status claims")
            object CheckingRecoverSentStatusDetails : Message("Checking recover sent status")
            object CheckingRecoverSentStatusSentClaims : Message("Checking recover sent status sentRecoverClaims")
            object RecoverSentForSecretId : Message("Recover sent for secretId")
            object RecoverDeclinedForSecretId : Message("Recover declined for secretId")
            object MarkingClaimAsDeclined : Message("Marking claim as locally declined")
            object TimerStopped : Message("Timer is stopped")
            object TimerRestartSkipped : Message("Timer restart skipped (already restarting)")
            object PollingPaused : Message("Polling paused")
            object PollingResumed : Message("Polling resumed")
            object PollingSkippedWhilePaused : Message("Polling skipped while paused")
            object ErrorGettingState : Message("Error getting app state")
            object ErrorCheckingRecoverRequest : Message("Error checking recover request")
            object ErrorCheckingRecoverSentStatus : Message("Error checking recover sent status")
            object CheckingRecoverDeclinedStatus : Message("Checking recover declined status")
            object ErrorCheckingRecoverDeclinedStatus : Message("Error checking recover declined status")
        }
    }

    object StateResolver : LogTag("🧩MetaSecretStateResolver") {
        sealed class Message(text: String) : LogTag.Message<StateResolver>(text) {
            override val tag: StateResolver = StateResolver
            
            object FirstSignUp : Message("first sign up")
            object LocalState : Message("Local State")
            object VaultState : Message("Vault state")
            object MemberState : Message("Member State")
            object StartGetAppState : Message("Start get app state")
            object CurrentStateIsLocal : Message("Current state is LOCAL")
            object SwwWithLocalState : Message("SWW with LOCAL state")
            object StartGenerateNewCreds : Message("Start generate new creds")
            object CurrentStateIsVault : Message("Current state is VAULT")
            object CurrentStateIsOutsider : Message("Current state is OUTSIDER")
            object CurrentStateIsNonMember : Message("Current state is NON_MEMBER")
            object CurrentStateIsPending : Message("Current state is PENDING")
            object CurrentStateIsDeclined : Message("Current state is DECLINED")
            object SwwWithVaultState : Message("SWW with VAULT state")
            object StartSignUp : Message("Start SignUp")
            object CurrentStateIsMember : Message("Current state is MEMBER")
            object SwwWithMemberState : Message("SWW with MEMBER state")
        }
    }

    object AddSecretVM : LogTag("➕AddSecretViewModel") {
        sealed class Message(text: String) : LogTag.Message<AddSecretVM>(text) {
            override val tag: AddSecretVM = AddSecretVM
            
            object StartingAddSecret : Message("Starting add secret")
            object WaitingForAddSecret : Message("Waiting for AddSecret")
            object InProgress : Message("In progress")
            object AddedSuccessfully : Message("Added successfully")
            object AddingFailed : Message("Adding failed")
            object UnknownState : Message("Unknown state")
            object BiometricAuthSuccess : Message("Biometric auth success")
            object BiometricAuthFailed : Message("Biometric auth failed")
            object BiometricAuthFallback : Message("Biometric auth fallback")
        }
    }

    object SecretsVM : LogTag("🔐SecretsScreenViewModel") {
        sealed class Message(text: String) : LogTag.Message<SecretsVM>(text) {
            override val tag: SecretsVM = SecretsVM
            
            object FollowUpdateState : Message("Start to follow UPDATE_STATE")
            object SocketActionType : Message("Socket action type is")
            object NewStateForSecrets : Message("New state for secrets been gotten")
            object LoadingSecretsFromVault : Message("Loading secrets from vault in background")
            object SecretsSyncedSuccess : Message("Secrets synced successfully")
            object FailedToGetSecrets : Message("Failed to get secrets from vault")
            object ErrorLoadingSecrets : Message("Error loading secrets from vault")
        }
    }

    object ShowSecretVM : LogTag("👀ShowSecretViewModel") {
        sealed class Message(text: String) : LogTag.Message<ShowSecretVM>(text) {
            override val tag: ShowSecretVM = ShowSecretVM
            
            object HandleEvent : Message("need handle event")
            object RecoverSecretId : Message("recover secretId")
            object SecretIdMatches : Message("SecretId matches secretIdToShow, showing recovered secret")
            object SecretIdNotMatches : Message("SecretId does not match secretIdToShow, starting recover process")
            object IgnoringAutoRecovery : Message("Ignoring auto-recovery event, user did not request it")
            object HideSecret : Message("hide secret")
            object StartRecovering : Message("Start recovering process")
            object SingleDeviceMode : Message("Single device mode, showing secret directly")
            object ExistingClaimFound : Message("Existing claim found")
            object ClaimAlreadyUsed : Message("Claim already used (sender has DELIVERED status), need new request")
            object NoExistingClaim : Message("No existing claim, sending recover request")
            object PendingClaimExists : Message("Pending claim already exists for secret")
            object RecoverFailed : Message("recover failed")
            object PresentingFailed : Message("Secret presenting is failed")
            object StartShowingRecovered : Message("Start showing recovered secret")
            object RecoveredSecretLoaded : Message("Recovered secret loaded successfully")
            object FailedToRecoverSecret : Message("Failed to recover secret")
            object ShowRecoveredFailed : Message("showRecovered failed")
        }
    }

    object ProfileVM : LogTag("👤ProfileScreenViewModel") {
        sealed class Message(text: String) : LogTag.Message<ProfileVM>(text) {
            override val tag: ProfileVM = ProfileVM
            
            object FollowGetState : Message("Start to follow GET_STATE for profile updates")
            object SocketActionType : Message("Socket action type is")
            object NewStateReceived : Message("New state received, refreshing profile data")
            object LoadProfileData : Message("loadProfileData")
            object LoadProfileDataFailed : Message("loadProfileData failed")
        }
    }

    object VaultStatsProvider : LogTag("📊VaultStatsProvider") {
        sealed class Message(text: String) : LogTag.Message<VaultStatsProvider>(text) {
            override val tag: VaultStatsProvider = VaultStatsProvider
            
            object StartFollow : Message("Start to follow GET_STATE and RESPONSIBLE_TO_ACCEPT_JOIN for stats")
            object UpdateStateReceived : Message("UPDATE_STATE received, refreshing stats")
            object AskToJoinSignal : Message("ASK_TO_JOIN signal received, refreshing join requests count")
            object StatsUpdated : Message("Stats updated")
            object VaultSummaryNull : Message("VaultSummary is null during stats refresh")
            object FailedToRefreshStats : Message("Failed to refresh stats")
            object AppStateUpdated : Message("AppState updated from cache")
            object AppStateNull : Message("AppState from cache is null")
        }
    }

    object AppStateCacheProvider : LogTag("📦AppStateCacheProvider") {
        sealed class Message(text: String) : LogTag.Message<AppStateCacheProvider>(text) {
            override val tag: AppStateCacheProvider = AppStateCacheProvider

            object CacheUpdated : Message("Cache updated")
            object CacheCleared : Message("Cache cleared")
        }
    }

    object BackupCoordinator : LogTag("💾BackupCoordinator") {
        sealed class Message(text: String) : LogTag.Message<BackupCoordinator>(text) {
            override val tag: BackupCoordinator = BackupCoordinator

            object EnsureBackupDestinationSelected : Message("ensureBackupDestinationSelected")
            object RestoreIfNeeded : Message("restoreIfNeeded")
            object LocalDbExists : Message("local DB exists, skipping restore")
            object NoBackupUriSet : Message("no backup URI set")
            object BackupUriNotGoogleDrive : Message("backup URI is not Google Drive")
            object RestoreCompleted : Message("restore completed")
            object RestoreFailed : Message("restore failed")
            object RestoreException : Message("restore exception")
            object BackupIfChanged : Message("backupIfChanged - Local DB updated, starting backup")
            object LocalDbNotExist : Message("local DB does not exist")
            object LocalDbLastModified : Message("Local DB last modified")
            object BackupCompleted : Message("backup completed successfully")
            object BackupFailed : Message("backup failed")
            object BackupException : Message("backup exception")
            object TakePersistableUriPermissionFailed : Message("takePersistableUriPermission failed")
            object BackupUriSaved : Message("backup URI saved")
            object PathIs : Message("path is")
            object BackExists : Message("back exists")
        }
    }

    object KeyChainManager : LogTag("🗝️KeyChainManager") {
        sealed class Message(text: String) : LogTag.Message<KeyChainManager>(text) {
            override val tag: KeyChainManager = KeyChainManager

            object ErrorSaving : Message("Error saving to KeyChain")
            object ErrorReading : Message("Error reading from KeyChain")
            object ErrorRemoving : Message("Error removing from KeyChain")
            object ErrorChecking : Message("Error checking KeyChain")
            object StartingClearAll : Message("Starting clearAll process")
            object ClearAllCompleted : Message("clearAll completed successfully")
            object ErrorClearing : Message("Error clearing KeyChain")
            object DeletedKeystoreEntries : Message("Deleted keystore entries")
        }
    }

    object MetaSecretCoreService : LogTag("🔧MetaSecretCoreService") {
        sealed class Message(text: String) : LogTag.Message<MetaSecretCoreService>(text) {
            override val tag: MetaSecretCoreService = MetaSecretCoreService

            object LibraryLoaded : Message("Metasecret_mobile library has been loaded successfully")
            object LibraryLoadError : Message("Error during loading of the Metasecret_mobile library")
            object CallingGenerateMasterKey : Message("Calling generateMasterKey")
            object MasterKeyGenerated : Message("Master key")
            object MasterKeyGenerationError : Message("Master key generation error")
            object CallingInitAppManager : Message("Calling initAppManager")
            object AppManagerInitResult : Message("AppManager")
            object AppManagerInitError : Message("AppManager initialization error")
            object CallingGetState : Message("Calling getState")
            object AppStateResult : Message("App State")
            object CallingGenerateUserCreds : Message("Calling generateUserCreds")
            object GenerateUserCredsResult : Message("generateUserCreds")
            object GenerateUserCredsError : Message("generateUserCreds error")
            object CallingSignUp : Message("Calling signUp")
            object SignUpResult : Message("SignUp State")
            object SignUpError : Message("SignUp error")
            object CallingUpdateMembership : Message("Calling updateMembership")
            object FormattedUserDataJson : Message("Formatted userData Json")
            object FormattedActionUpdate : Message("Formatted actionUpdate")
            object UpdateMembershipResult : Message("updateMembership result")
            object UpdateMembershipError : Message("updateMembership error")
            object CallingSplitSecret : Message("Calling splitSecret")
            object SplitSecretResult : Message("splitSecret result")
            object SplitSecretError : Message("splitSecret error")
            object CallingFindClaim : Message("Calling findClaim")
            object FindClaimResult : Message("findClaim result")
            object FindClaimError : Message("findClaim error")
            object CallingRecover : Message("Calling recover")
            object RecoverResult : Message("recover result")
            object RecoverError : Message("recover error")
            object CallingAcceptRecover : Message("Calling acceptRecover")
            object AcceptRecoverResult : Message("acceptRecover result")
            object AcceptRecoverError : Message("acceptRecover error")
            object CallingDeclineRecover : Message("Calling declineRecover")
            object DeclineRecoverResult : Message("declineRecover result")
            object DeclineRecoverError : Message("declineRecover error")
            object CallingCompleteDeclinedClaim : Message("Calling completeDeclinedClaim")
            object CompleteDeclinedClaimResult : Message("completeDeclinedClaim result")
            object CompleteDeclinedClaimError : Message("completeDeclinedClaim error")
            object CallingShowRecovered : Message("Calling showRecovered")
            object ShowRecoveredResult : Message("showRecovered result")
            object ShowRecoveredError : Message("showRecovered error")
            object CleanDb : Message("CLEAN DB")
            object DbFileDeleted : Message("DB file deleted")
            object DbFileNotExist : Message("DB file does not exist")
            object ErrorCleaningDb : Message("Error cleaning DB")
        }
    }
}

data class CriticalComponentsState(
    val backupDbExists: Boolean = false,
    val appManagerCreated: Boolean = false,
    val masterKeyGenerated: Boolean = false,
    val vaultState: String? = null,
    val deviceId: String? = null,
    val joinRequestsCount: Int = 0,
    val pendingClaimsCount: Int = 0,
    val sentClaimsCount: Int = 0,
    val deliveredClaimsCount: Int = 0
)

interface DebugLoggerInterface {
    fun <T : LogTag> log(message: LogTag.Message<T>, extra: String? = null, success: Boolean? = null)

    fun setLoggerVisibility()
    fun testInfo()
    fun setBackupDbExists(exists: Boolean)
    fun setAppManagerCreated(created: Boolean)
    fun setMasterKeyGenerated(generated: Boolean)
    fun setVaultState(state: String?)
    fun setDeviceId(deviceId: String?)
    fun setClaimsStats(joinRequestsCount: Int, pendingClaimsCount: Int, sentClaimsCount: Int, deliveredClaimsCount: Int)
    fun setOuterLoggerVisibility(isVisible: Boolean)
}

open class DebugLogger(
    private val logFormatter: LogFormatterInterface
) : DebugLoggerInterface {
    val isCommonLogsActive = true
    val isIosLogsActive = false
    val isCriticalInfoLogsActive = false
    private var criticalState = CriticalComponentsState()
    override fun setLoggerVisibility() {
        setOuterLoggerVisibility(isIosLogsActive)
    }
    override fun <T : LogTag> log(message: LogTag.Message<T>, extra: String?, success: Boolean?) {
        if (!isCommonLogsActive) { return }
        val fullMessage = if (extra != null) "${message.text} $extra" else message.text
        val preMessage = if (success != null) if (success) "✅" else "❌" else ""

        val logMessage = "$preMessage ${message.tag.displayName}: $fullMessage"
        println(logFormatter.formatLogMessage(logMessage))
    }

    override fun setBackupDbExists(exists: Boolean) {
        criticalState = criticalState.copy(backupDbExists = exists)
        testInfo()
    }

    override fun setAppManagerCreated(created: Boolean) {
        criticalState = criticalState.copy(appManagerCreated = created)
        testInfo()
    }

    override fun setMasterKeyGenerated(generated: Boolean) {
        criticalState = criticalState.copy(masterKeyGenerated = generated)
        testInfo()
    }

    override fun setVaultState(state: String?) {
        criticalState = criticalState.copy(vaultState = state)
        testInfo()
    }

    override fun setDeviceId(deviceId: String?) {
        criticalState = criticalState.copy(deviceId = deviceId)
        testInfo()
    }

    override fun setClaimsStats(joinRequestsCount: Int, pendingClaimsCount: Int, sentClaimsCount: Int, deliveredClaimsCount: Int) {
        criticalState = criticalState.copy(
            joinRequestsCount = joinRequestsCount,
            pendingClaimsCount = pendingClaimsCount,
            sentClaimsCount = sentClaimsCount,
            deliveredClaimsCount = deliveredClaimsCount
        )
        testInfo()
    }

    override fun testInfo() {
        if (!isCriticalInfoLogsActive) { return }
        
        val backupStatus = if (criticalState.backupDbExists) "Enable" else "False"
        val appManagerStatus = if (criticalState.appManagerCreated) "True" else "False"
        val masterKeyStatus = if (criticalState.masterKeyGenerated) "True" else "False"
        val vaultStateStatus = criticalState.vaultState ?: "null"
        val deviceIdStatus = criticalState.deviceId ?: "null"

        println(logFormatter.formatLogMessage("☢\uFE0F☢\uFE0F☢\uFE0F☢\uFE0F☢\uFE0F☢\uFE0F"))
        println(logFormatter.formatLogMessage("  Critical Components State:"))
        println(logFormatter.formatLogMessage("  BackUpDB => $backupStatus"))
        println(logFormatter.formatLogMessage("  MetaSecretAppManager => $appManagerStatus"))
        println(logFormatter.formatLogMessage("  MasterKey => $masterKeyStatus"))
        println(logFormatter.formatLogMessage("  VaultState => $vaultStateStatus"))
        println(logFormatter.formatLogMessage("  DeviceId => $deviceIdStatus"))
        println(logFormatter.formatLogMessage("  JoinRequests => ${criticalState.joinRequestsCount}"))
        println(logFormatter.formatLogMessage("  PendingClaims => ${criticalState.pendingClaimsCount}"))
        println(logFormatter.formatLogMessage("  SentClaims => ${criticalState.sentClaimsCount}"))
        println(logFormatter.formatLogMessage("  DeliveredClaims => ${criticalState.deliveredClaimsCount}"))
        println(logFormatter.formatLogMessage("☢\uFE0F☢\uFE0F☢\uFE0F☢\uFE0F☢\uFE0F☢\uFE0F"))
    }
    override fun setOuterLoggerVisibility(isVisible: Boolean) { }

}
