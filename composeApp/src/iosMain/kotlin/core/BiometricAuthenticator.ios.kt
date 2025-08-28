package core

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import org.jetbrains.compose.resources.ExperimentalResourceApi

class BiometricAuthenticatorIos(
    private val stringProvider: StringProviderInterface
) : BiometricAuthenticatorInterface {

    @OptIn(ExperimentalForeignApi::class)
    override fun isBiometricAvailable(): Boolean {
        val context = LAContext()
        val canEvaluate = context.canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics, 
            error = null
        )
        return canEvaluate
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: () -> Unit
    ) {
        if (!isBiometricAvailable()) {
            onError(stringProvider.biometricNotAvailable())
            return
        }
        
        val context = LAContext()
        context.localizedFallbackTitle = stringProvider.biometricFallback()

        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = stringProvider.biometricPromptReason()
        ) { success, error ->
            if (success) {
                onSuccess()
            } else if (error != null) {
                val errorDomain = error.domain
                val errorCode = error.code
                val errorDesc = error.localizedDescription.lowercase()
                
                when {
                    errorDesc.contains("fallback") -> onFallback()
                    errorDesc.contains("not available") -> onError(stringProvider.biometricNotAvailable())
                    errorDesc.contains("not enrolled") -> onError(stringProvider.biometricErrorNoEnrolled())
                    errorDesc.contains("permission") || errorDesc.contains("not authorized") -> 
                        onError(stringProvider.biometricPermissionSettings())
                    else -> onError(error.localizedDescription)
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun openAppSettings() {
        val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        if (settingsUrl != null) {
            UIApplication.sharedApplication.openURL(settingsUrl)
        }
    }
} 