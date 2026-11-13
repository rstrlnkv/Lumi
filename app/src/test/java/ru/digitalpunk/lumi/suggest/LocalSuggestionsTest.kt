package ru.digitalpunk.lumi.suggest

import ru.digitalpunk.lumi.data.library.SuggestPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalSuggestionsTest {
    private fun h(url: String, title: String = "", visits: Int = 1, last: Long = 0) = SuggestPage(url, title, visits, last, bookmark = false)
    private fun b(url: String, title: String = "", visits: Int = 0, last: Long = 0) = SuggestPage(url, title, visits, last, bookmark = true)
    private fun rank(typed: String, vararg pages: SuggestPage, pin: String? = null) = rankLocal(LocalIndex(pages.toList()), typed, pin)

    @Test fun aHostPrefixBeatsATitleWordWhichBeatsAnAddressThatContainsTheText() {
        assertEquals(
            listOf(Suggestion.History("", "https://github.com/"), Suggestion.Bookmark("Learn Git", "https://learn.example/"), Suggestion.History("", "https://e.com/digit")),
            rank("git", h("https://e.com/digit", visits = 99), b("https://learn.example/", "Learn Git"), h("https://github.com/"), h("https://other.org/")),
        )
    }

    @Test fun insideATierABookmarkComesFirstThenVisitsThenTheNewestVisit() {
        assertEquals(
            listOf("https://gitlab.com/", "https://github.com/", "https://gitea.io/"),
            rank("git", h("https://gitea.io/", visits = 5, last = 1), h("https://github.com/", visits = 5, last = 2), b("https://gitlab.com/"))
                .map { (it as? Suggestion.Bookmark)?.url ?: (it as Suggestion.History).url },
        )
    }

    @Test fun theSamePageUnderAnotherSchemeOrWwwShowsOnceAsTheBookmarkAndAtMostThreeRowsCome() {
        val rows = rank("git", h("https://www.github.com/", visits = 50), b("http://github.com"), h("https://git-scm.com/"), h("https://gitea.io/"), h("https://gitee.com/"))
        assertEquals(3, rows.size)
        assertEquals(Suggestion.Bookmark("", "http://github.com"), rows[0])
        assertTrue(rows.none { it is Suggestion.History && it.url.contains("github") })
    }

    @Test fun typedTextIsLiteralAndCaseIsIgnoredWithoutLowercasing() {
        assertEquals(emptyList(), rank("a.c", h("https://abc.com/")))
        assertEquals(emptyList(), rank("(.*", h("https://abc.com/")))
        assertEquals(1, rank("%20", h("https://a.com/x%20y")).size)
        assertEquals(1, rank("istanbul", h("https://a.com/", "İstanbul guide")).size)
        assertEquals(1, rank("GIT", h("https://github.com/")).size)
        assertEquals(1, rank("www.git", h("https://github.com/")).size)
    }

    @Test fun aTitleMatchesOnlyAtTheStartOfAWord() {
        assertEquals(emptyList(), rank("git", h("https://a.com/", "Digital")))
        assertEquals(1, rank("git", h("https://a.com/", "my-git notes")).size)
    }

    @Test fun thePinnedHostComesFirstSoTheListAgreesWithTheInlineCompletion() {
        val rows = rank("git", b("https://gitlab.com/"), h("https://github.com/", visits = 40), pin = "github.com")
        assertEquals(Suggestion.History("", "https://github.com/"), rows.first())
    }

    @Test fun completionHostsAreBareAndTheMostVisitedComesFirst() {
        val index = LocalIndex(
            listOf(
                h("https://www.github.com/a", visits = 3), h("https://github.com/b", visits = 3), h("https://gitlab.com/", visits = 5),
                b("https://gitea.io/"), h("https://gitee.com/"), h("about:blank"), h("https://user@Host.example:8443/x"),
            ),
        )
        assertEquals(listOf("github.com", "gitlab.com", "gitee.com", "host.example", "gitea.io"), index.hosts)
    }

    @Test fun fiveThousandPagesRankQuickly() {
        val index = LocalIndex((1..5000).map { h("https://site$it.example/path/$it", "Title number $it", visits = it % 7) })
        val start = System.nanoTime()
        repeat(20) { rankLocal(index, "site4", null) }
        assertTrue(System.nanoTime() - start < 1_000_000_000L)
    }
}
