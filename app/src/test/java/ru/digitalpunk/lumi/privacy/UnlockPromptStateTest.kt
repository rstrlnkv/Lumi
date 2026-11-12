package ru.digitalpunk.lumi.privacy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnlockPromptStateTest {
    @Test fun aStopWhileOurPromptIsUpThenACancelLeavesNoAutoPrompt() {
        val state = UnlockPromptState()
        assertTrue(state.start())
        assertFalse(state.onBackground(), "our own credential screen is not leaving the app")
        state.end()
        assertFalse(state.autoPromptPending)
    }

    @Test fun aRealBackgroundWithNoPromptUpLocksAndArmsTheAutoPrompt() {
        val state = UnlockPromptState()
        assertTrue(state.onBackground())
        assertTrue(state.autoPromptPending)
    }

    @Test fun aSecondPromptDoesNotStartWhileOneIsUp() {
        val state = UnlockPromptState()
        assertTrue(state.start())
        assertFalse(state.start())
        state.end()
        assertTrue(state.start())
    }
}
