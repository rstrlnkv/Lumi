package ru.digitalpunk.lumi.ui.settings

import ru.digitalpunk.lumi.data.library.HistoryEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class SiteRulesTest {
    private fun visit(url: String, at: Long) = HistoryEntity(url = url, title = "", visitedAt = at)

    @Test fun `one row per site, newest first`() {
        val rows = siteRows(listOf(visit("https://a.com/1", 10), visit("https://b.org/", 30), visit("https://a.com/2", 20)), "")
        assertEquals(listOf(SiteRow("b.org", 30), SiteRow("a.com", 20)), rows)
    }

    @Test fun `www and case fold into one site`() {
        val rows = siteRows(listOf(visit("https://www.example.com/", 1), visit("https://example.com/x", 2), visit("https://EXAMPLE.com/", 3)), "")
        assertEquals(listOf(SiteRow("example.com", 3)), rows)
    }

    @Test fun `pages with no site key are left out`() {
        val rows = siteRows(listOf(visit("about:blank", 1), visit("http://[::1]/", 2), visit("https://ok.net/", 3)), "")
        assertEquals(listOf(SiteRow("ok.net", 3)), rows)
    }

    @Test fun `the query filters by host`() {
        val history = listOf(visit("https://mail.yandex.ru/", 1), visit("https://github.com/", 2))
        assertEquals(listOf(SiteRow("mail.yandex.ru", 1)), siteRows(history, " YANDEX "))
        assertEquals(2, siteRows(history, "  ").size)
    }

    @Test fun `the newest visit wins`() {
        val rows = siteRows(listOf(visit("https://a.com/", 50), visit("https://a.com/old", 5)), "")
        assertEquals(50, rows.single().lastVisit)
    }
}
