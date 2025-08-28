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

    private fun checkBiometricPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.USE_BIOMETRIC
            ) == PackageManager.PERMISSION_GRANTED
        } 

        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.USE_FINGERPRINT
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        return false
    }

    private fun requestBiometricPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.USE_BIOMETRIC),
                REQUEST_BIOMETRIC_PERMISSION
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.USE_FINGERPRINT),
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
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
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
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(stringProvider.biometricTitle())
            .setSubtitle(stringProvider.biometricSubtitle())
            .setDescription(stringProvider.biometricDescription())
            .setNegativeButtonText(stringProvider.biometricFallback())
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }
    
    companion object {
        private const val REQUEST_BIOMETRIC_PERMISSION = 1001
    }
} 