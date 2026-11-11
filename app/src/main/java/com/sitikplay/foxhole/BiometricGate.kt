package com.sitikplay.foxhole

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object BiometricGate {

    @Volatile
    private var unlockedUntil = 0L

    fun active(ctx: Context): Boolean = Settings.confirmDevice(ctx)

    fun hasDeviceLock(ctx: Context): Boolean {
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return km.isKeyguardSecure
    }

    fun unlocked(): Boolean = System.currentTimeMillis() < unlockedUntil

    fun windowSec(ctx: Context): Int = Settings.confirmWindowSec(ctx).coerceIn(15, 600)

    fun confirm(
        activity: FragmentActivity,
        title: String,
        reason: String,
        onPass: () -> Unit
    ) {
        if (!active(activity) || unlocked()) {
            onPass()
            return
        }
        if (!hasDeviceLock(activity)) {
            val warn = MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.t340))
                .setMessage(activity.getString(R.string.t341))
                .setPositiveButton(activity.getString(R.string.t342), null)
                .create()

            if (Settings.flagSecureScreens(activity)) {
                warn.window?.setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                )
            }
            warn.show()
            return
        }

        val manager = BiometricManager.from(activity)
        val canBiometric = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
        val authenticators = if (canBiometric) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errCode: Int, errString: CharSequence) {

                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlockedUntil = System.currentTimeMillis() + windowSec(activity) * 1000L
                    onPass()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Foxhole")
            .setDescription(reason)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}
