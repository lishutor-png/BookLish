package com.example.ui.util

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    private const val TAG = "BiometricHelper"

    /**
     * Checks if biometric (fingerprint / face) or device credential (PIN/pattern) is available and enrolled.
     */
    fun canAuthenticate(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            } else {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            }
            biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking biometric capability", e)
            false
        }
    }

    /**
     * Shows the official Android biometric prompt (fingerprint sensor bottom sheet).
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Verifikasi Sidik Jari",
        subtitle: String = "Pindai sidik jari Anda untuk melanjutkan",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val promptInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()
            } else {
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText("Batal")
                    .build()
            }

            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            onError(errString.toString())
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onError("Sidik jari tidak cocok. Silakan coba lagi.")
                    }
                }
            )

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to launch BiometricPrompt", e)
            onError(e.localizedMessage ?: "Gagal memulai pemindai sidik jari")
        }
    }
}
