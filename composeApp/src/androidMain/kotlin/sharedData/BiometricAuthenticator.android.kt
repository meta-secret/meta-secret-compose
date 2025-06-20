package sharedData

import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.biometric_title
import kotlinproject.composeapp.generated.resources.biometric_subtitle
import kotlinproject.composeapp.generated.resources.biometric_description
import kotlinproject.composeapp.generated.resources.biometric_fallback
import kotlinproject.composeapp.generated.resources.biometric_not_available
import kotlinproject.composeapp.generated.resources.biometric_error_no_hardware
import kotlinproject.composeapp.generated.resources.biometric_error_no_enrolled
import kotlinproject.composeapp.generated.resources.biometric_permission_required

class BiometricAuthenticatorAndroid (
    private val context: Context,
    private val activity: FragmentActivity
) : BiometricAuthenticatorInterface {

    private val executor = ContextCompat.getMainExecutor(context)

    @OptIn(ExperimentalResourceApi::class)
    private fun getStringResource(resource: StringResource): String {
        return runBlocking { getString(resource) }
    }

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
            onError(getStringResource(Res.string.biometric_permission_required))
            return
        }

        if (!isBiometricAvailable()) {
            onError(getStringResource(Res.string.biometric_not_available))
            return
        }

        val promptInfo = createPromptInfo()
        val biometricPrompt = createBiometricPrompt(onSuccess, onError, onFallback)
        
        biometricPrompt.authenticate(promptInfo)
    }

    override fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
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
                        BiometricPrompt.ERROR_HW_NOT_PRESENT -> onError(getStringResource(Res.string.biometric_error_no_hardware))
                        BiometricPrompt.ERROR_NO_BIOMETRICS -> onError(getStringResource(Res.string.biometric_error_no_enrolled))
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
            .setTitle(getStringResource(Res.string.biometric_title))
            .setSubtitle(getStringResource(Res.string.biometric_subtitle))
            .setDescription(getStringResource(Res.string.biometric_description))
            .setNegativeButtonText(getStringResource(Res.string.biometric_fallback))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }
    
    companion object {
        private const val REQUEST_BIOMETRIC_PERMISSION = 1001
    }
} 