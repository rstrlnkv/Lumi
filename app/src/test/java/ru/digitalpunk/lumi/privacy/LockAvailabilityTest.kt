package ru.digitalpunk.lumi.privacy

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import kotlin.test.Test
import kotlin.test.assertEquals

class LockAvailabilityTest {
    @Test fun successMeansAvailable() {
        assertEquals(LockAvailability.Available, lockAvailability(BiometricManager.BIOMETRIC_SUCCESS))
    }

    @Test fun noneEnrolledMeansNoScreenLock() {
        assertEquals(LockAvailability.NoScreenLock, lockAvailability(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
    }

    @Test fun everyOtherCodeFailsClosed() {
        listOf(
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
            12345,
        ).forEach { assertEquals(LockAvailability.Unavailable, lockAvailability(it), "code $it") }
    }

    @Test fun authenticatorsDropStrongOnApi28And29() {
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, authenticators(28))
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, authenticators(29))
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, authenticators(30))
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, authenticators(36))
    }
}
