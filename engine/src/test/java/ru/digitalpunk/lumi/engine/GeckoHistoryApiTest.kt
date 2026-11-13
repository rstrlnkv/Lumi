package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import kotlin.test.Test
import kotlin.test.assertEquals

/** The engine overrides `onVisited` for global history; this fails on an upgrade that changes its shape. */
class GeckoHistoryApiTest {
    @Test fun onVisitedTakesSessionUrlLastUrlAndFlagsAndReturnsAResult() {
        val method = GeckoSession.HistoryDelegate::class.java.getMethod(
            "onVisited",
            GeckoSession::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
        )
        assertEquals(GeckoResult::class.java, method.returnType)
    }

    @Test fun geckoResultCanBeMadeFromAValue() {
        GeckoResult::class.java.getMethod("fromValue", Any::class.java)
    }
}
