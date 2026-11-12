package ru.digitalpunk.lumi.privacy

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** Whether the device can lock the private tabs. Anything but [Available] means no private tabs (fail closed). */
enum class LockAvailability { Available, NoScreenLock, Unavailable }

/** [code] is a `BiometricManager.canAuthenticate` result. */
fun lockAvailability(code: Int): LockAvailability = when (code) {
    BiometricManager.BIOMETRIC_SUCCESS -> LockAvailability.Available
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> LockAvailability.NoScreenLock
    else -> LockAvailability.Unavailable
}

/**
 * The check for a screen lock, and the one prompt outside API 28-29. androidx.biometric 1.1.0 refuses a strong
 * biometric together with the credential on API 28-29, so there it is only the check: see [unlockStart].
 */
fun authenticators(sdk: Int): Int = if (sdk in 28..29) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_STRONG or DEVICE_CREDENTIAL

/** How an unlock starts. A weak biometric never unlocks. */
enum class UnlockStart { OnePrompt, StrongBiometric, DeviceCredential }

/**
 * API 28-29: a strong biometric first, whose "Use PIN" button goes to the device credential; without a strong
 * biometric, the device credential at once. Other versions: one prompt with [authenticators].
 */
fun unlockStart(sdk: Int, strongBiometricEnrolled: Boolean): UnlockStart = when {
    sdk !in 28..29 -> UnlockStart.OnePrompt
    strongBiometricEnrolled -> UnlockStart.StrongBiometric
    else -> UnlockStart.DeviceCredential
}
