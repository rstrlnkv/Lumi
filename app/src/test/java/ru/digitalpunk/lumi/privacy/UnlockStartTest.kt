package ru.digitalpunk.lumi.privacy

import kotlin.test.Test
import kotlin.test.assertEquals

class UnlockStartTest {
    @Test fun api28And29AskForAStrongBiometricFirstWhenOneIsEnrolled() {
        assertEquals(UnlockStart.StrongBiometric, unlockStart(28, strongBiometricEnrolled = true))
        assertEquals(UnlockStart.StrongBiometric, unlockStart(29, strongBiometricEnrolled = true))
    }

    @Test fun api28And29GoStraightToTheDeviceCredentialWithoutAStrongBiometric() {
        assertEquals(UnlockStart.DeviceCredential, unlockStart(28, strongBiometricEnrolled = false))
        assertEquals(UnlockStart.DeviceCredential, unlockStart(29, strongBiometricEnrolled = false))
    }

    @Test fun otherVersionsUseOnePromptWhateverIsEnrolled() {
        listOf(26, 27, 30, 36).forEach { sdk ->
            assertEquals(UnlockStart.OnePrompt, unlockStart(sdk, strongBiometricEnrolled = true), "sdk $sdk")
            assertEquals(UnlockStart.OnePrompt, unlockStart(sdk, strongBiometricEnrolled = false), "sdk $sdk")
        }
    }
}
