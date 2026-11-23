package com.expensetracker.sms.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.expensetracker.sms.fmt

/** True while income amounts must be hidden. */
val LocalIncomeHidden = compositionLocalOf { false }

/** Ask for the phone's screen lock, then run the block. Runs it straight away if already unlocked. */
val LocalRevealIncome = compositionLocalOf<(() -> Unit) -> Unit> { { it() } }

const val MASK = "₹ ••••"

/** An income amount, or ₹ •••• while income is locked. */
@Composable
fun incomeAmount(v: Double): String = if (LocalIncomeHidden.current) MASK else fmt(v)

fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Shows the system lock prompt (PIN / pattern / password / fingerprint / face).
 * The OS checks it — the app never sees the PIN.
 */
fun unlockWithDeviceLock(activity: FragmentActivity, onSuccess: () -> Unit, onNoScreenLock: () -> Unit) {
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("View income")
        .setSubtitle("Use your phone's screen lock")
    if (Build.VERSION.SDK_INT >= 30) {
        val auth = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(activity).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            onNoScreenLock()
            return
        }
        info.setAllowedAuthenticators(auth)
    } else {
        val km = activity.getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isDeviceSecure) {
            onNoScreenLock()
            return
        }
        @Suppress("DEPRECATION")
        info.setDeviceCredentialAllowed(true)
    }
    BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    ).authenticate(info.build())
}
