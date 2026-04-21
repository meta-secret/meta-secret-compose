package core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.app.KeyguardManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthenticatorAndroid (
    private val context: Context,
    private val activity: FragmentActivity,
    private val stringProvider: StringProviderInterface
) : BiometricAuthenticatorInterface {

    private val executor = ContextCompat.getMainExecutor(context)
    private val biometricManager = BiometricManager.from(context)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)

    override fun isBiometricAvailable(): Boolean {
        val promptConfig = resolvePromptConfig()
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                biometricManager.canAuthenticate(promptConfig.authenticators) == BiometricManager.BIOMETRIC_SUCCESS
            }
            promptConfig.deviceCredentialAllowed -> true
            else -> biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    override fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: () -> Unit
    ) {
        val promptConfig = resolvePromptConfig()
        val isAuthAvailable = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                biometricManager.canAuthenticate(promptConfig.authenticators) == BiometricManager.BIOMETRIC_SUCCESS
            }
            promptConfig.deviceCredentialAllowed -> true
            else -> biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
        if (!isAuthAvailable) {
            onError(stringProvider.biometricNotAvailable())
            return
        }

        val promptInfo = createPromptInfo(promptConfig)
        val biometricPrompt = createBiometricPrompt(onSuccess, onError, onFallback)

        biometricPrompt.authenticate(promptInfo)
    }

    override fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
//            Intent.setData = Uri.fromParts("package", context.packageName, null) // TODO:
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun createBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: () -> Unit
    ): BiometricPrompt {
        return BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onFallback()
                        BiometricPrompt.ERROR_HW_NOT_PRESENT -> onError(stringProvider.biometricErrorNoHardware())
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> onError(stringProvider.biometricErrorNoEnrolled())
                        else -> onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )
    }

    private fun createPromptInfo(promptConfig: PromptConfig): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(stringProvider.biometricTitle())
            .setSubtitle(stringProvider.biometricSubtitle())
            .setDescription(stringProvider.biometricDescription())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(promptConfig.authenticators)
        } else {
            builder.setDeviceCredentialAllowed(promptConfig.deviceCredentialAllowed)
        }

        if (promptConfig.requiresNegativeButton) {
            builder.setNegativeButtonText(stringProvider.biometricFallback())
        }
        return builder.build()
    }

    private fun resolvePromptConfig(): PromptConfig {
        val isDeviceSecure = keyguardManager?.isDeviceSecure == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val credentialOrStrongBiometric =
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (biometricManager.canAuthenticate(credentialOrStrongBiometric) == BiometricManager.BIOMETRIC_SUCCESS) {
                return PromptConfig(
                    authenticators = credentialOrStrongBiometric,
                    deviceCredentialAllowed = true,
                    requiresNegativeButton = false
                )
            }

            if (isDeviceSecure &&
                biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
            ) {
                return PromptConfig(
                    authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    deviceCredentialAllowed = true,
                    requiresNegativeButton = false
                )
            }

            return PromptConfig(
                authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG,
                deviceCredentialAllowed = false,
                requiresNegativeButton = true
            )
        }

        return PromptConfig(
            authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG,
            deviceCredentialAllowed = isDeviceSecure,
            requiresNegativeButton = !isDeviceSecure
        )
    }

    private data class PromptConfig(
        val authenticators: Int,
        val deviceCredentialAllowed: Boolean,
        val requiresNegativeButton: Boolean
    )
} 