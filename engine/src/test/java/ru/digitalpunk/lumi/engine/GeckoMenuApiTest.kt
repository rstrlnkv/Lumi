package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoSession
import kotlin.test.Test
import kotlin.test.assertEquals

/** The engine calls these GeckoView members for the M5 menus; this fails on an upgrade that renames or removes one. */
class GeckoMenuApiTest {
    @Test fun gotoHistoryIndexTakesAnInt() {
        GeckoSession::class.java.getMethod("gotoHistoryIndex", Int::class.javaPrimitiveType)
    }

    @Test fun historyDelegateReportsTheHistoryList() {
        GeckoSession.HistoryDelegate::class.java.getMethod(
            "onHistoryStateChange",
            GeckoSession::class.java,
            GeckoSession.HistoryDelegate.HistoryList::class.java,
        )
        GeckoSession.HistoryDelegate.HistoryList::class.java.getMethod("getCurrentIndex")
    }

    @Test fun contextElementHasTheLinkFields() {
        val type = GeckoSession.ContentDelegate.ContextElement::class.java
        listOf("linkUri", "title", "linkText").forEach { assertEquals(String::class.java, type.getField(it).type, it) }
    }
}
