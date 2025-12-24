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
            object BiometricFailed : Message("Biometric is failed")
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
            
            object Init : Message("Init")
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
            object WaitingForStateResponse : Message("Waiting for state response")
        }
    }

    object StateResolver : LogTag("🧩MetaSecretStateResolver") {
        sealed class Message(text: String) : LogTag.Message<StateResolver>(text) {
            override val tag: StateResolver = StateResolver
            
            object ResolveState : Message("Resolve state")
        }
    }

    object AddSecretVM : LogTag("➕AddSecretViewModel") {
        sealed class Message(text: String) : LogTag.Message<AddSecretVM>(text) {
            override val tag: AddSecretVM = AddSecretVM
            
            object Init : Message("Init")
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
            object HideSecret : Message("hide secret")
            object StartRecovering : Message("Start recovering process")
            object RecoverFailed : Message("recover failed")
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
        }
    }
}

interface DebugLoggerInterface {
    fun <T : LogTag> log(message: LogTag.Message<T>, extra: String? = null, success: Boolean? = null, )

    fun testInfo()
}

class DebugLogger : DebugLoggerInterface {
    val isActive = true

    override fun <T : LogTag> log(message: LogTag.Message<T>, extra: String?, success: Boolean?) {
        if (!isActive) { return }

        val fullMessage = if (extra != null) "${message.text} $extra" else message.text
        val preMessage = if (success != null) if (success) "✅" else "❌" else ""

        println("$preMessage ${message.tag.displayName}: $fullMessage")
    }

    override fun testInfo() {
        TODO("Not yet implemented")
    }
}