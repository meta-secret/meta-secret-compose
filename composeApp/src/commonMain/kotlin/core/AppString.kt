package core

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject
import kotlinproject.composeapp.generated.resources.*

enum class AppString {
    accept,
    accept_request_on_other_device,
    addDevice,
    addDeviceAdvice,
    addSecret,
    addText,
    backup_choose_path_message,
    backup_choose_path_warning,
    biometric_description,
    biometric_error_no_enrolled,
    biometric_error_no_hardware,
    biometric_fallback,
    biometric_not_available,
    biometric_permission_required,
    biometric_permission_settings,
    biometric_prompt,
    biometric_subtitle,
    biometric_title,
    cancel,
    copyPhrase,
    copySecret,
    decline,
    device,
    device_ui_category_android,
    device_ui_category_cli,
    device_ui_category_desktop,
    device_ui_category_iphone,
    device_ui_category_other,
    device_ui_category_tablet,
    device_ui_category_unknown,
    device_ui_category_web,
    devices_4,
    devices_5,
    devicesList,
    downloadMetasecret,
    emailSelectionApple,
    emailSelectionCouldNotGetEmail,
    emailSelectionConfirmDescription,
    emailSelectionConfirmTitle,
    emailSelectionContinue,
    emailSelectionDescription,
    emailSelectionErrorTitle,
    emailSelectionGoogle,
    emailSelectionInvalidEmail,
    emailSelectionManual,
    emailSelectionManualDescription,
    emailSelectionManualHint,
    emailSelectionManualHintLine1,
    emailSelectionManualHintLine2,
    emailSelectionManualTitle,
    emailSelectionPrivateRelayMessage,
    emailSelectionPrivateRelayTitle,
    emailSelectionTitle,
    emailSelectionTryAgain,
    emailSelectionBackToOptions,
    emailSelectionChange,
    enable_biometric_required,
    errorBiometricAuthFailed,
    errorInternal,
    errorNetwork,
    errorParse,
    errorRecoverDeclined,
    errorSecretAddFailed,
    errorUnknownPrefix,
    errorValidation,
    yes,
    no,
    fromAllDevices,
    goto_devices_tab,
    join,
    joining,
    lackOfDevices,
    lackOfDevices_end,
    lackOfDevices_start,
    level_1,
    level_2,
    level_3,
    name_occupied_join_prompt,
    next,
    nickname,
    nicknameError,
    noSecrets,
    noSecretsHeader,
    ok,
    onBoardingDescription1,
    onBoardingDescription2,
    onBoardingDescription3,
    onBoardingSubTitle1,
    onBoardingSubTitle2,
    onBoardingSubTitle3,
    onBoardingTitle1,
    onBoardingTitle2,
    onBoardingTitle3,
    orText,
    orUseQR,
    passwordType,
    pasteSeedHint,
    pasteSeedPhrase,
    poweredBy,
    profile,
    recoverPendingExists,
    recoverRequestSent,
    remove,
    removeDevice,
    removeSecret,
    removeSecretConfirmation,
    secret,
    secretAdded,
    secretAddFailed,
    secretCapital,
    secretEncryptedSubtitle,
    secretEncryptedTitle,
    secretName,
    secretRemoved,
    secrets_4,
    secrets_5,
    secretsHeader,
    seedPhraseType,
    seedWordCount,
    show,
    showSecret,
    signOut,
    skip,
    start,
    useMetaSecret,
    version,
    wanna_join,
    wanna_recover,
    wordPlaceholder,
    unexpected_login,
    reject_join,
    resetAllData,
    resetAllDataWarning,
    biometric_error
}

fun AppString.resource(): StringResource = when (this) {
    AppString.accept -> Res.string.accept
    AppString.accept_request_on_other_device -> Res.string.accept_request_on_other_device
    AppString.addDevice -> Res.string.addDevice
    AppString.addDeviceAdvice -> Res.string.addDeviceAdvice
    AppString.addSecret -> Res.string.addSecret
    AppString.addText -> Res.string.addText
    AppString.backup_choose_path_message -> Res.string.backup_choose_path_message
    AppString.backup_choose_path_warning -> Res.string.backup_choose_path_warning
    AppString.biometric_description -> Res.string.biometric_description
    AppString.biometric_error_no_enrolled -> Res.string.biometric_error_no_enrolled
    AppString.biometric_error_no_hardware -> Res.string.biometric_error_no_hardware
    AppString.biometric_fallback -> Res.string.biometric_fallback
    AppString.biometric_not_available -> Res.string.biometric_not_available
    AppString.biometric_permission_required -> Res.string.biometric_permission_required
    AppString.biometric_permission_settings -> Res.string.biometric_permission_settings
    AppString.biometric_prompt -> Res.string.biometric_prompt
    AppString.biometric_subtitle -> Res.string.biometric_subtitle
    AppString.biometric_title -> Res.string.biometric_title
    AppString.cancel -> Res.string.cancel
    AppString.copyPhrase -> Res.string.copyPhrase
    AppString.copySecret -> Res.string.copySecret
    AppString.decline -> Res.string.decline
    AppString.device -> Res.string.device
    AppString.device_ui_category_android -> Res.string.device_ui_category_android
    AppString.device_ui_category_cli -> Res.string.device_ui_category_cli
    AppString.device_ui_category_desktop -> Res.string.device_ui_category_desktop
    AppString.device_ui_category_iphone -> Res.string.device_ui_category_iphone
    AppString.device_ui_category_other -> Res.string.device_ui_category_other
    AppString.device_ui_category_tablet -> Res.string.device_ui_category_tablet
    AppString.device_ui_category_unknown -> Res.string.device_ui_category_unknown
    AppString.device_ui_category_web -> Res.string.device_ui_category_web
    AppString.devices_4 -> Res.string.devices_4
    AppString.devices_5 -> Res.string.devices_5
    AppString.devicesList -> Res.string.devicesList
    AppString.downloadMetasecret -> Res.string.downloadMetasecret
    AppString.emailSelectionApple -> Res.string.emailSelectionApple
    AppString.emailSelectionBackToOptions -> Res.string.emailSelectionBackToOptions
    AppString.emailSelectionChange -> Res.string.emailSelectionChange
    AppString.emailSelectionConfirmDescription -> Res.string.emailSelectionConfirmDescription
    AppString.emailSelectionConfirmTitle -> Res.string.emailSelectionConfirmTitle
    AppString.emailSelectionContinue -> Res.string.emailSelectionContinue
    AppString.emailSelectionCouldNotGetEmail -> Res.string.emailSelectionCouldNotGetEmail
    AppString.emailSelectionDescription -> Res.string.emailSelectionDescription
    AppString.emailSelectionErrorTitle -> Res.string.emailSelectionErrorTitle
    AppString.emailSelectionGoogle -> Res.string.emailSelectionGoogle
    AppString.emailSelectionInvalidEmail -> Res.string.emailSelectionInvalidEmail
    AppString.emailSelectionManual -> Res.string.emailSelectionManual
    AppString.emailSelectionManualDescription -> Res.string.emailSelectionManualDescription
    AppString.emailSelectionManualHint -> Res.string.emailSelectionManualHint
    AppString.emailSelectionManualHintLine1 -> Res.string.emailSelectionManualHintLine1
    AppString.emailSelectionManualHintLine2 -> Res.string.emailSelectionManualHintLine2
    AppString.emailSelectionManualTitle -> Res.string.emailSelectionManualTitle
    AppString.emailSelectionPrivateRelayMessage -> Res.string.emailSelectionPrivateRelayMessage
    AppString.emailSelectionPrivateRelayTitle -> Res.string.emailSelectionPrivateRelayTitle
    AppString.emailSelectionTitle -> Res.string.emailSelectionTitle
    AppString.emailSelectionTryAgain -> Res.string.emailSelectionTryAgain
    AppString.enable_biometric_required -> Res.string.enable_biometric_required
    AppString.errorBiometricAuthFailed -> Res.string.errorBiometricAuthFailed
    AppString.errorInternal -> Res.string.errorInternal
    AppString.errorNetwork -> Res.string.errorNetwork
    AppString.errorParse -> Res.string.errorParse
    AppString.errorRecoverDeclined -> Res.string.errorRecoverDeclined
    AppString.errorSecretAddFailed -> Res.string.errorSecretAddFailed
    AppString.errorUnknownPrefix -> Res.string.errorUnknownPrefix
    AppString.errorValidation -> Res.string.errorValidation
    AppString.yes -> Res.string.yes
    AppString.no -> Res.string.no
    AppString.fromAllDevices -> Res.string.fromAllDevices
    AppString.goto_devices_tab -> Res.string.goto_devices_tab
    AppString.join -> Res.string.join
    AppString.joining -> Res.string.joining
    AppString.lackOfDevices -> Res.string.lackOfDevices
    AppString.lackOfDevices_end -> Res.string.lackOfDevices_end
    AppString.lackOfDevices_start -> Res.string.lackOfDevices_start
    AppString.level_1 -> Res.string.level_1
    AppString.level_2 -> Res.string.level_2
    AppString.level_3 -> Res.string.level_3
    AppString.name_occupied_join_prompt -> Res.string.name_occupied_join_prompt
    AppString.next -> Res.string.next
    AppString.nickname -> Res.string.nickname
    AppString.nicknameError -> Res.string.nicknameError
    AppString.noSecrets -> Res.string.noSecrets
    AppString.noSecretsHeader -> Res.string.noSecretsHeader
    AppString.ok -> Res.string.ok
    AppString.onBoardingDescription1 -> Res.string.onBoardingDescription1
    AppString.onBoardingDescription2 -> Res.string.onBoardingDescription2
    AppString.onBoardingDescription3 -> Res.string.onBoardingDescription3
    AppString.onBoardingSubTitle1 -> Res.string.onBoardingSubTitle1
    AppString.onBoardingSubTitle2 -> Res.string.onBoardingSubTitle2
    AppString.onBoardingSubTitle3 -> Res.string.onBoardingSubTitle3
    AppString.onBoardingTitle1 -> Res.string.onBoardingTitle1
    AppString.onBoardingTitle2 -> Res.string.onBoardingTitle2
    AppString.onBoardingTitle3 -> Res.string.onBoardingTitle3
    AppString.orText -> Res.string.or
    AppString.orUseQR -> Res.string.orUseQR
    AppString.passwordType -> Res.string.passwordType
    AppString.pasteSeedHint -> Res.string.pasteSeedHint
    AppString.pasteSeedPhrase -> Res.string.pasteSeedPhrase
    AppString.poweredBy -> Res.string.poweredBy
    AppString.profile -> Res.string.profile
    AppString.recoverPendingExists -> Res.string.recoverPendingExists
    AppString.recoverRequestSent -> Res.string.recoverRequestSent
    AppString.reject_join -> Res.string.reject_join
    AppString.resetAllData -> Res.string.resetAllData
    AppString.resetAllDataWarning -> Res.string.resetAllDataWarning
    AppString.remove -> Res.string.remove
    AppString.removeDevice -> Res.string.removeDevice
    AppString.removeSecret -> Res.string.removeSecret
    AppString.removeSecretConfirmation -> Res.string.removeSecretConfirmation
    AppString.secret -> Res.string.secret
    AppString.secretAdded -> Res.string.secretAdded
    AppString.secretAddFailed -> Res.string.secretAddFailed
    AppString.secretCapital -> Res.string.secretCapital
    AppString.secretEncryptedSubtitle -> Res.string.secretEncryptedSubtitle
    AppString.secretEncryptedTitle -> Res.string.secretEncryptedTitle
    AppString.secretName -> Res.string.secretName
    AppString.secretRemoved -> Res.string.secretRemoved
    AppString.secrets_4 -> Res.string.secrets_4
    AppString.secrets_5 -> Res.string.secrets_5
    AppString.secretsHeader -> Res.string.secretsHeader
    AppString.seedPhraseType -> Res.string.seedPhraseType
    AppString.seedWordCount -> Res.string.seedWordCount
    AppString.show -> Res.string.show
    AppString.showSecret -> Res.string.showSecret
    AppString.signOut -> Res.string.signOut
    AppString.skip -> Res.string.skip
    AppString.start -> Res.string.start
    AppString.unexpected_login -> Res.string.unexpected_login
    AppString.useMetaSecret -> Res.string.useMetaSecret
    AppString.version -> Res.string.version
    AppString.wanna_join -> Res.string.wanna_join
    AppString.wanna_recover -> Res.string.wanna_recover
    AppString.wordPlaceholder -> Res.string.wordPlaceholder
    AppString.biometric_error -> Res.string.biometric_error
}

fun StringProviderInterface.string(key: AppString): String = getString(key)

@Composable
fun appString(key: AppString): String {
    val stringProvider: StringProviderInterface = koinInject()
    return stringProvider.getString(key)
}
