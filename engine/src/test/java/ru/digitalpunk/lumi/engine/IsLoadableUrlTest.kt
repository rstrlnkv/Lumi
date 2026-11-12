package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class IsLoadableUrlTest {
    @Test fun refusesScriptFilePrivilegedAndEmptyUrls() {
        val refused = listOf(
            "javascript:alert(1)", "JAVASCRIPT:x", "file:///etc/passwd", "about:config",
            "data:text/html,x", "content://x", "",
        )
        refused.forEach { assertEquals(false, isLoadableUrl(it), "should refuse: '$it'") }
    }

    @Test fun allowsWebUrlsAndAllowedAboutPagesInAnyCase() {
        val allowed = listOf(
            "https://example.com", "HTTP://EXAMPLE.COM", "about:blank", "ABOUT:BLANK", "about:crashcontent",
        )
        allowed.forEach { assertEquals(true, isLoadableUrl(it), "should allow: '$it'") }
    }
}
