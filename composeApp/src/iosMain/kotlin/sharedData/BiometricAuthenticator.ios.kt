package sharedData

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.biometric_fallback
import kotlinproject.composeapp.generated.resources.biometric_not_available
import kotlinproject.composeapp.generated.resources.biometric_error_no_enrolled
import kotlinproject.composeapp.generated.resources.biometric_prompt
import kotlinproject.composeapp.generated.resources.biometric_permission_settings

class BiometricAuthenticatorIos : BiometricAuthenticatorInterface {

    @OptIn(ExperimentalResourceApi::class)
    private fun getStringResource(resource: StringResource): String {
        return runBlocking { getString(resource) }
    }

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
            onError(getStringResource(Res.string.biometric_not_available))
            return
        }
        
        val context = LAContext()
        context.localizedFallbackTitle = getStringResource(Res.string.biometric_fallback)

        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = getStringResource(Res.string.biometric_prompt)
        ) { success, error ->
            if (success) {
                onSuccess()
            } else if (error != null) {
                val errorDomain = error.domain
                val errorCode = error.code
                val errorDesc = error.localizedDescription.lowercase()
                
                when {
                    errorDesc.contains("fallback") -> onFallback()
                    errorDesc.contains("not available") -> onError(getStringResource(Res.string.biometric_not_available))
                    errorDesc.contains("not enrolled") -> onError(getStringResource(Res.string.biometric_error_no_enrolled))
                    errorDesc.contains("permission") || errorDesc.contains("not authorized") -> 
                        onError(getStringResource(Res.string.biometric_permission_settings))
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