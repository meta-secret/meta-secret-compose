package core

import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthenticatorAndroid (
    private val context: Context,
    private val activity: FragmentActivity,
    private val stringProvider: StringProviderInterface
) : BiometricAuthenticatorInterface {

    private val executor = ContextCompat.getMainExecutor(context)

    private var resolvedAuthenticators: Int = BiometricManager.Authenticators.BIOMETRIC_STRONG

    private fun checkBiometricPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.USE_BIOMETRIC
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestBiometricPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.USE_BIOMETRIC),
                REQUEST_BIOMETRIC_PERMISSION
            )
        }
    }
    
    override fun isBiometricAvailable(): Boolean {
        if (!checkBiometricPermissions()) {
            requestBiometricPermissions()
            return false
        }

        val biometricManager = BiometricManager.from(context)

        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            resolvedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
            return true
        }
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
            resolvedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            return true
        }
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
            resolvedAuthenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL
            return true
        }
        return false
    }

    override fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: () -> Unit
    ) {
        if (!checkBiometricPermissions()) {
            requestBiometricPermissions()
            onError(stringProvider.biometricPermissionRequired())
            return
        }

        if (!isBiometricAvailable()) {
            onError(stringProvider.biometricNotAvailable())
            return
        }

        val promptInfo = createPromptInfo()
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
                        BiometricPrompt.ERROR_NO_BIOMETRICS -> onError(stringProvider.biometricErrorNoEnrolled())
                        else -> onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )
    }

    private fun createPromptInfo(): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(stringProvider.biometricTitle())
            .setSubtitle(stringProvider.biometricSubtitle())
            .setDescription(stringProvider.biometricDescription())
            .setAllowedAuthenticators(resolvedAuthenticators)

        val hasDeviceCredential = (resolvedAuthenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0
        if (!hasDeviceCredential) {
            builder.setNegativeButtonText(stringProvider.biometricFallback())
        }
        return builder.build()
    }
    
    companion object {
        private const val REQUEST_BIOMETRIC_PERMISSION = 1001
    }
} 