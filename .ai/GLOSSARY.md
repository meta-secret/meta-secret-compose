# Project Glossary — meta-secret-compose

Unified vocabulary for meta-secret-compose. All communication (AI, code, docs, user) uses these terms.

**Last updated:** 2026-06-05  
**Maintenance:** Monthly or when codebase grows significantly

---

## 1. Core Domain Terms

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **Vault** | Encrypted container owned by a user, holding secrets and members | Core feature | `VaultData`, `VaultManager`, `VaultSummary` |
| **VaultData** | Data class with `vaultName`, map of users, and list of secrets | API model | `AppStateModel.kt` |
| **VaultSummary** | Compact view of a vault: name, secret count, member info | UI display | Devices/Profile screen summary |
| **VaultFullInfo** | Sealed class representing vault in three states: `NotExists`, `Outsider`, `Member` | State machine | `AppStateModel.kt` |
| **VaultMember** | Pairing of a `UserData` member with a `VaultData` | Membership | Inside `VaultFullInfo.Member` |
| **VaultEvents** | Pending vault requests (join clusters) and state updates | Polling | `SocketActionModel` |
| **VaultRequest** | Request to join a cluster, with join request details | Membership flow | `AppStateModel.kt` |
| **VaultAvailability** | Enum: `AVAILABLE` or `UNAVAILABLE` — whether vault can be joined | Sign-in check | `SignInScreenViewModel` |
| **Secret** | Core entity: an encrypted piece of data with `SecretId` and `SecretName` | Core feature | `KeyValueStorageInterface.kt` |
| **SecretApiModel** | API representation of a secret: id, name, type, wordCount | API layer | `AppStateModel.kt` |
| **SecretModel** | Internal model: `SecretName` and encrypted value | Internal storage | `SecretModel.kt` |
| **SecretValueType** | Enum: `PASSWORD` or `SEED_PHRASE` — type of secret being stored | Add Secret dialog | `AddSecretDialog.kt` |
| **ParsedSecretValue** | Structured secret input: type, word list, word count | Add Secret dialog | `AddSecretDialog.kt` |
| **RecoveredSecretModel** | API response after recovery: success flag + recovered message | Recovery flow | `RecoveredSecretModel.kt` |
| **RecoveredSecretMessage** | Wrapper holding the revealed secret string | Recovery flow | `RecoveredSecretModel.kt` |
| **Claim** | A request to distribute or recover a secret among vault members | Secret sharing | `ClaimObject`, `ClaimModel` |
| **Device** | Registered hardware endpoint: `DeviceMake` + username | Core entity | `KeyValueStorageInterface.kt` |
| **UniFFI** | Interface layer bridging Kotlin/Swift to the Rust cryptography library | Technical | `MetaSecretCoreService` |
| **E2E (End-to-End)** | Encryption from sender to receiver; server never has access to plaintext | Feature property | All message/secret flows |
| **MVVM** | Architecture pattern: Model–View–ViewModel used throughout the app | Technical | All `*ViewModel` classes |
| **Coordinator** | Navigation controller managing screen transitions on iOS and Android | Technical | `AlertCoordinator`, `NotificationCoordinator` |

---

## 2. User & Membership

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **UserData** | Device info + `vaultName` for a registered user | API model | `AppStateModel.kt` |
| **UserInfo** | Display model: `DeviceId`, name, device type, UI category, membership status | UI | `DevicesScreen` |
| **UserMembership** | Sealed state: optional `Member` or `Outsider` status | State | `AppStateModel.kt` |
| **UserDataMember** | Member variant wrapping `UserData` | Membership | `AppStateModel.kt` |
| **UserDataOutsider** | Outsider variant wrapping `UserData` + `UserDataOutsiderStatus` | Membership | `AppStateModel.kt` |
| **UserDataOutsiderStatus** | Enum: `NON_MEMBER`, `PENDING`, `DECLINED` — outsider join progress | Sign-in flow | `AppStateModel.kt` |
| **UserStatus** | Enum: `MEMBER`, `PENDING`, `DECLINED`, `NON_MEMBER` | UI display | `DevicesScreen` |
| **UserMemberFullInfo** | Full member context: member info + vault + claims + events | Main screen state | `AppStateModel.kt` |
| **JoinClusterRequest** | Candidate user data sent when requesting to join a vault | Join flow | `AppStateModel.kt` |
| **JoinRequestAlertState** | Sealed UI state for join alerts: `Hidden`, `Visible(deviceId)`, `Processing(deviceId)` | Alert dialog | `AlertProvider.kt` |

---

## 3. Device Management

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **DeviceData** | Full device record: `DeviceId`, name, type, UI category, public keys | Core model | `AppStateModel.kt` |
| **DeviceUiCategory** | Enum: `Android`, `Iphone`, `Tablet`, `Desktop`, `Cli`, `Web`, `Other` | UI icons | `DevicesScreen` |
| **DeviceStatus** | String-valued enum for device-level status | Devices list | `AppStateModel.kt` |
| **DeviceCellModel** | Display model for a single device row in the UI | `DevicesScreen` | `DeviceCellModel.kt` |
| **DevicesQuantity** | Enum with associated amount — number of devices in a vault | Vault stats | `DevicesQuantity.kt` |
| **ClientDeviceInfo** | Current device's own info used during registration | Sign-up | `ClientDeviceInfo.kt` |
| **OpenBox** | Public key container with `dsaPk` (signing key) and `transportPk` (encryption key) | Cryptography | `AppStateModel.kt` |

---

## 4. Claims & Secret Sharing

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **ClaimObject** | API claim record: id, distribution type, list of receivers, status map | API layer | `AppStateModel.kt` |
| **ClaimModel** | Internal model: `ClaimId`, sender, distribution type, receivers, status | Internal | `ClaimModel.kt` |
| **ClaimStatus** | Enum: `PENDING`, `SENT`, `DELIVERED`, `ACCEPTED`, `DECLINED` | Claim lifecycle | `AppStateModel.kt` |
| **ClaimStatusInfo** | Map of `ClaimStatus` keyed by device — per-device delivery tracking | Claims detail | `AppStateModel.kt` |
| **SsClaims** | Map of secret-sharing claims, keyed by secret id | Vault state | `AppStateModel.kt` |
| **SearchClaimModel** | API response for a claim lookup: success, `SearchClaimMessage`, error | Recovery | `SearchClaimModel.kt` |
| **SearchClaimMessage** | Wrapper holding the found `ClaimObject` | Recovery | `SearchClaimModel.kt` |
| **DistributionType** | Enum: `SPLIT` (distribute secret) or `RECOVER` (reassemble secret) | Claim type | `AppStateModel.kt` |
| **DistClaimId** | Claim id paired with a `PassId` | Claim identity | `AppStateModel.kt` |
| **PassId** | Identifier + name for a password/secret reference | Claims | `AppStateModel.kt` |
| **RestoreData** | `ClaimId` + `SecretId` pair used to trigger recovery | Recovery flow | `SocketActionModel.kt` |
| **RecoveryRequestAlertState** | Sealed UI state for recovery alerts: `Hidden`, `Visible(RestoreData)`, `Processing(RestoreData)` | Alert dialog | `AlertProvider.kt` |

---

## 5. State & Authentication

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **AppState** | Interface — base for all application state types | State machine | `MetaSecretAppManager.kt` |
| **State** | Sealed class: `Local(DeviceData)` or `Vault(VaultFullInfo)` — top-level app state | App lifecycle | `AppStateModel.kt` |
| **LocalState** | State when user has a local device identity but is not yet in a vault | Sign-in flow | `MetaSecretAppManager.kt` |
| **MemberState** | AppState subtype for a user who is a vault member | Main screen | `MetaSecretAppManager.kt` |
| **OutsiderState** | AppState subtype for a user who has requested but not yet joined a vault | Pending join | `MetaSecretAppManager.kt` |
| **VaultState** | Represents either `Local` or `Vault` application state in the manager | State resolver | `MetaSecretAppManager.kt` |
| **AuthState** | Enum: `COMPLETED` or `NOT_YET_COMPLETED` — biometric auth status | Splash screen | `MetaSecretAppManager.kt` |
| **AppStateResult** | Parsed result of a state API call | State resolver | `AppStateModel.kt` |
| **BiometricState** | Sealed class: `Idle`, `Success`, `Error(message)` — biometric op result | Splash/ShowSecret | `BiometricAuthenticatorInterface.kt` |
| **InitResult** | Sealed class: `Success(result)` or `Error(message)` — manager init outcome | App startup | `MetaSecretAppManager.kt` |
| **SignInFlowState** | Internal enum tracking sign-in progress: `IDLE` → `CHECKING_VAULT` → `VAULT_AVAILABLE`/`NOT_AVAILABLE` → `JOINING` → `JOINED`/`DECLINED` | Sign-in UX | `SignInScreenViewModel.kt` |

---

## 6. Socket & Polling

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **SocketHandler** | Manages polling loop and emits `SocketActionModel` events to ViewModels | Core service | `MetaSecretSocketHandler.kt` |
| **SocketActionModel** | Sealed class of socket events: `NONE`, `ASK_TO_JOIN`, `JOIN_REQUEST_ACCEPTED/DECLINED/PENDING`, `UPDATE_STATE`, `READY_TO_RECOVER`, `RECOVER_SENT/DECLINED`, `DISMISS_RECOVERY_REQUEST` | Event bus | `SocketActionModel.kt` |
| **SocketRequestModel** | Enum of polling modes: `GET_STATE`, `WAIT_FOR_JOIN_RESPONSE`, `SHOW_SECRET`, `WAIT_FOR_RECOVER_REQUEST` | Polling config | `SocketRequestModel.kt` |

---

## 7. API & Response Models

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **AppStateModel** | Top-level API response: `message` + `success` flag wrapping `State` | API layer | `AppStateModel.kt` |
| **CommonResponseModel** | Generic API response: `success`, `message`, `error` | All API calls | `CommonResponseModel.kt` |
| **MasterKeyModel** | API response for master key ops: `success`, key value, error | Key management | `MasterKeyModel.kt` |
| **RecoveredSecretModel** | API response after secret recovery: `success` + `RecoveredSecretMessage` | Recovery | `RecoveredSecretModel.kt` |
| **SearchClaimModel** | API response for claim lookup: `success`, `SearchClaimMessage`, `error` | Recovery | `SearchClaimModel.kt` |

---

## 8. Internal App Models

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **SecretModel** | Internal model pairing `SecretName` with encrypted value | Local storage | `SecretModel.kt` |
| **ClaimModel** | Internal model: `ClaimId`, sender, `DistributionType`, receivers, `ClaimStatus` | Internal | `ClaimModel.kt` |
| **DeviceCellModel** | Row display model for devices list | `DevicesScreen` | `DeviceCellModel.kt` |
| **DevicesQuantity** | Enum with count — how many devices are in the vault | Vault stats | `DevicesQuantity.kt` |
| **StorageKeys** | Enum of local storage keys: `ONBOARDING_INFO`, `LOGIN_INFO`, `WARNING_INFO`, `SECRET_DATA`, `DEVICE_DATA`, `BIOMETRIC_ENABLED`, `CACHED_DEVICE_ID`, `CACHED_VAULT_NAME` | Key-value storage | `KeyValueStorageInterface.kt` |
| **AppErrors** | Enum of internal error types: `CreateLocalError`, `CredsGenerationError`, `SignUpError` | Error handling | `AppErrors.kt` |

---

## 9. Core Services & Managers

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **MetaSecretAppManager** | Main orchestrator for all app-level operations (sign-up, secrets, membership) | Core | `MetaSecretAppManager.kt` |
| **MetaSecretCore** | Rust FFI library providing cryptographic primitives (key gen, split, recover) | FFI | accessed via `MetaSecretCoreInterface` |
| **MetaSecretCoreInterface** | Kotlin interface combining `MetaSecretAccountInterface` and `MetaSecretSecretOperationsInterface` | FFI contract | `MetaSecretCoreInterface.kt` |
| **MetaSecretAccountInterface** | Interface for account operations (generate credentials, sign up) | FFI contract | `MetaSecretAccountInterface.kt` |
| **MetaSecretStateResolver** | Translates raw API state into typed `AppState` objects | State layer | `MetaSecretStateResolver.kt` |
| **AlertCoordinator** | Manages display of join-request and recovery-request alert dialogs | UI coordination | `AlertCoordinator.kt` |
| **NotificationCoordinator** | Manages in-app notification banners (success/error) | UI coordination | `NotificationCoordinator.kt` |
| **NotificationState** | Sealed class: `Hidden` or `Visible(message, isError)` | Notification display | `NotificationCoordinatorInterface.kt` |
| **KeyValueStorage** | Local key-value persistence for device, user, and secret data | Local storage | `KeyValueStorageImpl.kt` |
| **KeyChainManager** | Secure keychain/keystore storage for sensitive credentials | Security | `KeyChainInterface.kt` |
| **BiometricAuthenticator** | Platform-specific biometric (Face ID / fingerprint) handler | Security | `BiometricAuthenticatorInterface.kt` |
| **VaultStatsProvider** | Computes vault statistics (member count, secret count, device counts) | UI data | `VaultStatsProvider.kt` |
| **AppStateCacheProvider** | Caches the most recent `AppStateModel` to avoid redundant polling | Performance | `AppStateCacheProvider.kt` |
| **ErrorMapper** | Maps raw exceptions to typed `AppError` values | Error handling | `ErrorMapper.kt` |

---

## 10. UI Screens & Navigation

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **SplashScreen** | Initial loading screen; triggers biometric auth check | App launch | `SplashScreen.kt` |
| **OnboardingScreen** | Three-page onboarding flow shown on first launch | First run | `OnboardingScreen.kt` |
| **OnBoardingPage** | Sealed class: `First`, `Second`, `Third` — individual onboarding pages | Onboarding | `OnboardingScreen.kt` |
| **SignInScreen** | Screen for vault name entry, user registration, and vault join | Auth | `SignInScreen.kt` |
| **MainScreen** | Root screen with bottom tab navigation for the logged-in state | Navigation | `MainScreen.kt` |
| **SecretsScreen** | List of all secrets in the vault with add/remove/show actions | Core feature | `SecretsScreen.kt` |
| **DevicesScreen** | List of devices in the vault with accept/decline join controls | Membership | `DevicesScreen.kt` |
| **ProfileScreen** | User profile: vault name, stats, settings | User info | `ProfileScreen.kt` |
| **SecretsTab** | Bottom tab object routing to `SecretsScreen` | Navigation | `BottomTabs.kt` |
| **DevicesTab** | Bottom tab object routing to `DevicesScreen` | Navigation | `BottomTabs.kt` |
| **ProfileTab** | Bottom tab object routing to `ProfileScreen` | Navigation | `BottomTabs.kt` |

---

## 11. UI Dialogs & State

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **AddSecretDialog** | Modal for creating a new secret (password or seed phrase) | SecretsScreen | `AddSecretDialog.kt` |
| **AddSecretState** | Enum: `IDLE`, `IN_PROGRESS`, `ADDED_SUCCESSFULLY`, `ADDING_FAILURE` | Dialog lifecycle | `AddSecretDialog.kt` |
| **ShowSecretDialog** | Modal that reveals a recovered secret, gated by biometric auth | SecretsScreen | `ShowSecretDialog.kt` |
| **RevealedSecretContent** | Sealed class: `Hidden`, `FullSecret(secret)`, `PartialSecret(words)` | Secret reveal | `ShowSecretDialog.kt` |
| **RemoveSecretDialog** | Confirmation modal for deleting a secret from the vault | SecretsScreen | `RemoveSecretDialog.kt` |
| **AddDeviceDialog** | Modal for adding a new device to the vault | DevicesScreen | `AddDeviceDialog.kt` |
| **AlertType** | Enum: `JoinRequest` or `RecoveryRequest` — type of alert being shown | Alert system | `AlertProvider.kt` |

---

## 12. ViewModels & Events

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **CommonViewModel** | Abstract base ViewModel providing logger and shared event handling | All ViewModels | `CommonViewModel.kt` |
| **SplashScreenViewModel** | Controls splash logic: biometric trigger, auth completion | SplashScreen | `SplashScreenViewModel.kt` |
| **SplashViewEvents** | Enum: `START_BIOMETRIC`, `SKIP_BIOMETRIC`, `COMPLETE_SPLASH` | Splash events | `SplashScreenViewModel.kt` |
| **OnboardingViewModel** | Controls onboarding page progression | OnboardingScreen | `OnboardingViewModel.kt` |
| **OnboardingViewEvents** | Enum: `COMPLETE_ONBOARDING` | Onboarding events | `OnboardingViewModel.kt` |
| **SignInScreenViewModel** | Manages sign-in flow: vault check, join, credential generation | SignInScreen | `SignInScreenViewModel.kt` |
| **SignInViewEvents** | Sealed class: `StartSignInProcess`, `UpdateName`, `JoinExistingVault`, `CancelJoin` | Sign-in events | `SignInScreenViewModel.kt` |
| **MainScreenViewModel** | Manages main screen tab state and recovery alert routing | MainScreen | `MainScreenViewModel.kt` |
| **MainViewEvents** | Sealed class: `SetTabIndex`, `ShowWarning`, recovery-related events | Main screen events | `MainScreenViewModel.kt` |
| **SecretsScreenViewModel** | Manages secrets list, polling, and recovery initiation | SecretsScreen | `SecretsScreenViewModel.kt` |
| **SecretsEvents** | Sealed class: `GetSecret(index)`, `SetTabIndex(index)` | Secrets events | `SecretsScreenViewModel.kt` |
| **DevicesScreenViewModel** | Manages device list and accept/decline join actions | DevicesScreen | `DevicesScreenViewModel.kt` |
| **DeviceViewEvents** | Sealed class: `SelectDevice(deviceId)` | Device events | `DevicesScreenViewModel.kt` |
| **ProfileScreenViewModel** | Manages profile data display and user settings | ProfileScreen | `ProfileScreenViewModel.kt` |
| **ProfileEvents** | Sealed class of profile-related user events | Profile events | `ProfileScreenViewModel.kt` |
| **ShowSecretViewModel** | Controls biometric gate and secret reveal for `ShowSecretDialog` | Show secret | `ShowSecretViewModel.kt` |
| **ShowSecretEvents** | Sealed class: `ShowSecret`, `HideSecret` | Show secret events | `ShowSecretViewModel.kt` |
| **AddSecretViewModel** | Manages secret creation flow inside `AddSecretDialog` | Add secret | `AddSecretViewModel.kt` |
| **RemoveSecretViewModel** | Manages secret deletion confirmation | Remove secret | `RemoveSecretViewModel.kt` |
| **AddDeviceViewModel** | Manages device addition flow | Add device | `AddDeviceViewModel.kt` |

---

## 13. Error Handling

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **AppError** | Sealed class of typed errors: `NetworkError`, `FfiError`, `ParseError`, `ValidationError`, `UnknownError` | Error domain | `ErrorMapper.kt` |
| **AppErrors** | Enum of internal operation errors: `CreateLocalError`, `CredsGenerationError`, `SignUpError` | Sign-up flow | `AppErrors.kt` |
| **InitResult** | Sealed class: `Success(result)` or `Error(message)` — outcome of `MetaSecretAppManager` initialization | App startup | `MetaSecretAppManager.kt` |
| **ErrorMapper** | Maps raw exceptions and API error strings to `AppError` instances | Error layer | `ErrorMapper.kt` |

---

## 14. Logging & Debugging

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **LogTag** | Sealed class hierarchy of named log tags (e.g., `SplashVM`, `DevicesVM`, `MainVM`) | Debug logging | `DebugLoggerInterface.kt` |
| **LogTag.Message** | Abstract inner class pairing a log string with a `LogTag` reference | Log entry | `DebugLoggerInterface.kt` |
| **DebugLoggerInterface** | Contract for structured debug logging across platforms | Logging | `DebugLoggerInterface.kt` |
| **LogFormatterInterface** | Contract for formatting log output | Logging | `LogFormatterInterface.kt` |

---

## 15. Platform Abstractions

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **MetaSecretCoreService** | Platform-specific class (Android/iOS) that bridges Kotlin to Rust FFI via `MetaSecretCoreInterface` | FFI | `androidMain` / `iosMain` |
| **SwiftBridge** | Swift class on iOS that wraps the compiled Rust `.xcframework` for use by `MetaSecretCoreService` | iOS FFI | `SwiftBridge.swift` |
| **ClientDeviceInfoProvider** | Platform interface providing current device hardware info | Registration | per-platform |
| **DatabasePathProvider** | Platform interface returning the filesystem path for the local database | Storage | per-platform |
| **ScreenMetricsProvider** | Platform interface for screen dimensions and density | UI layout | per-platform |
| **StringProvider** | Interface for localized string resolution across platforms | i18n | `StringProviderInterface.kt` |
| **BackupCoordinator** | Platform interface for triggering device backup operations | Settings | per-platform |

---

## 16. User-Facing Concepts

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **Sync** | Retrieve latest state (secrets, devices, events) from the server | Feature | "Sync" triggered by polling |
| **Block Contact** | Prevent a specific user from joining the vault | Feature | Settings → Blocked Users |
| **Device Trust** | Verification that a device's public key matches the user's identity | Security | First-time setup |
| **Fingerprint** | Short hash of a public key used for out-of-band identity verification | Security | "Verify fingerprint over video call" |
| **Backup** | Encrypted copy of vault stored locally or in cloud (future feature) | Feature (future) | Settings → Backup Management |
| **Split** | Operation that encrypts and distributes a secret across vault members | Secret sharing | `DistributionType.SPLIT` |
| **Recover** | Operation that reassembles a secret from member shares | Secret sharing | `DistributionType.RECOVER` |

---

## 17. AI / Workflow Meta Terms

| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| **Payload** | Task description or issue context passed to a workflow command | AI meta | `run <payload>` |
| **Artifact** | Generated output file from a workflow stage | AI meta | `.ai/artifacts/run/MS-*.md` |
| **Stage** | One step in the 10-stage development workflow | AI meta | Stage 2 = "Grill Me" clarification |
| **Clarification Report** | Document produced by the requirements-clarifier agent in Stage 2 | AI meta | Output of Stage 2 |

---

## 18. Relationships

```
User
  ├── Device (registered hardware; carries OpenBox keys)
  ├── UserMembership → Member | Outsider
  └── Vault (one per user identity)

Vault
  ├── VaultData
  │    ├── Members (map of UserData)
  │    └── Secrets (list of SecretApiModel)
  ├── VaultEvents
  │    └── VaultRequests (pending JoinClusterRequests)
  └── SsClaims (map of Claims per secret)

Secret
  ├── SecretApiModel (server representation)
  ├── SecretModel (local encrypted copy)
  └── Claims
       ├── ClaimObject (SPLIT distribution)
       └── ClaimObject (RECOVER distribution)

Claim
  ├── DistributionType (SPLIT | RECOVER)
  ├── ClaimStatusInfo (per-device delivery map)
  └── PassId (links to Secret)

Device
  ├── DeviceData (full record with OpenBox)
  ├── DeviceUiCategory (visual icon selector)
  └── DeviceStatus

AppState (interface)
  ├── LocalState (device created, no vault)
  ├── MemberState (in vault, full access)
  └── OutsiderState (join requested, pending)

MetaSecretAppManager
  ├── MetaSecretCore (Rust FFI → split/recover/keys)
  ├── MetaSecretStateResolver (API → AppState)
  ├── MetaSecretSocketHandler (polling → SocketActionModel)
  ├── AlertCoordinator (join/recovery alerts)
  ├── NotificationCoordinator (in-app banners)
  ├── KeyValueStorage (local persistence)
  ├── KeyChainManager (secure credentials)
  └── BiometricAuthenticator (biometric gate)
```

---

## 19. Key Operations Vocabulary

| Operation | Method | Flow |
|-----------|--------|------|
| **Generate credentials** | `generateUserCreds(vaultName)` | Sign-up: creates keys in Rust |
| **Sign up** | `signUp()` | Sign-up: registers device with server |
| **Accept join** | `updateMembership(candidate, ACCEPT)` | Membership: approve join request |
| **Decline join** | `updateMembership(candidate, DECLINE)` | Membership: reject join request |
| **Split secret** | `splitSecret(secretName, secret)` | Secret sharing: distribute to members |
| **Request recovery** | `recover(secretId)` | Recovery: ask members to release shares |
| **Accept recovery** | `acceptRecover(claimId)` | Recovery: release your share |
| **Decline recovery** | `declineRecover(claimId)` | Recovery: refuse to release share |
| **Show recovered** | `showRecovered(secretId)` | Recovery: reveal reassembled secret |
| **Find claim** | `findClaim(secretId)` | Recovery: locate the active claim |
| **Get state** | `getAppState()` | Polling: fetch full state from Rust/server |
| **Check auth** | `checkAuth()` | Startup: verify biometric auth status |
| **Init manager** | `initAppManager(masterKey)` | Startup: bootstrap the app manager |

---

## 20. Rules for Usage

1. **Always capitalize** domain terms when referring to the concept (`Secret`, not `secret`, when it's the entity).
2. **In code:** class/variable names must match glossary terms exactly.
3. **In docs:** use glossary terms consistently — never invent synonyms.
4. **In AI communication:** all AI responses must use these terms.
5. **When unsure:** check this glossary before inventing new terminology.
6. **When adding features:** run `only-glossary-update <feature-description>` to extend this file.
