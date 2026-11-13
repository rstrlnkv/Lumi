package ru.digitalpunk.lumi.suggest

import ru.digitalpunk.lumi.data.library.SuggestPage
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalSuggestionsEdgeTest {
    private fun h(url: String, title: String = "", visits: Int = 1, last: Long = 0) = SuggestPage(url, title, visits, last, bookmark = false)
    private fun b(url: String, title: String = "", visits: Int = 0, last: Long = 0) = SuggestPage(url, title, visits, last, bookmark = true)
    private fun urls(typed: String, vararg pages: SuggestPage) =
        rankLocal(LocalIndex(pages.toList()), typed, null).map { (it as? Suggestion.Bookmark)?.url ?: (it as Suggestion.History).url }

    @Test fun moreVisitsFirstInATierWhenNothingIsBookmarked() {
        assertEquals(
            listOf("https://gitb.com/", "https://gita.com/"),
            urls("git", h("https://gita.com/", visits = 2, last = 99), h("https://gitb.com/", visits = 7, last = 1)),
        )
    }

    @Test fun theSameHostInAnotherCaseIsOneRow() {
        assertEquals(1, urls("git", h("https://GitHub.com/x"), h("https://github.com/x")).size)
    }

    @Test fun aHostPrefixInAnyCaseOutranksAnAddressThatOnlyContainsTheText() {
        assertEquals(
            listOf("https://github.com/", "https://e.com/git-notes"),
            urls("GIT", h("https://e.com/git-notes", visits = 99), h("https://github.com/", visits = 1)),
        )
    }

    @Test fun anAddressContainsTheTextInAnyCase() {
        assertEquals(1, urls("DIGIT", h("https://e.com/digit")).size)
    }

    @Test fun justWwwDotMatchesNothingNotEveryPage() {
        assertEquals(emptyList(), urls("www.", h("https://github.com/"), h("https://gitlab.com/")))
    }

    @Test fun typedTextWithSpacesAroundItStillMatches() {
        assertEquals(1, urls("  git ", h("https://github.com/")).size)
        assertEquals(emptyList(), urls("   ", h("https://github.com/")))
    }

    @Test fun aTrailingDotInTheHostIsTheSameHost() {
        assertEquals(listOf("example.com"), LocalIndex(listOf(h("https://example.com./"), h("https://example.com/a"))).hosts)
    }

    @Test fun withEqualVisitsABookmarkedHostComesBeforeAnUnmarkedOneEvenWhenItsNameSortsLater() {
        assertEquals(listOf("zzz.com", "aaa.com"), LocalIndex(listOf(h("https://aaa.com/", visits = 2), b("https://zzz.com/", visits = 2))).hosts)
    }

    @Test fun hostsOfOddAddressesAreReadWithoutThrowing() {
        assertEquals("[::1]", hostOf("https://[::1]:8080/x"))
        assertEquals("example.com", hostOf("http://user:pw@Example.COM:80/x?y=a@b"))
        assertEquals("example.com", hostOf("HTTPS://WWW.EXAMPLE.COM"))
        assertEquals("a.com", hostOf("https://a.com?x=y@z"))
        assertEquals("a.com", hostOf("https://a.com#u@v"))
        listOf("http://", "https:///x", "http://[", "https://@/", "https://...", "http://:80/", "", "https://", "about:blank", "https://\u0000").forEach {
            hostOf(it)
        }
        assertEquals("", hostOf("https://..."))
        assertEquals("", hostOf("ftp://files.example/"))
    }

    @Test fun anIndexOfOddAddressesRanksWithoutThrowing() {
        val pages = listOf("http://", "https:///x", "http://[", "https://@/", "https://...", "javascript:alert(1)", "").map { h(it, "t") }
        rankLocal(LocalIndex(pages), "t", null)
        rankLocal(LocalIndex(pages), "http", "x")
    }
}
