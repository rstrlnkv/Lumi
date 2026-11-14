package ru.digitalpunk.lumi.net

import ru.digitalpunk.lumi.data.icons.HttpGet
import ru.digitalpunk.lumi.data.icons.HttpResponse
import ru.digitalpunk.lumi.data.search.SuggestGet
import ru.digitalpunk.lumi.engine.BrowserEngine

/**
 * Site icons and suggestions through Gecko's network stack (ADR 014): the only place that sees both `:data` and `:engine`.
 * Null while the engine is not started, so a background icon request never starts Gecko.
 */
class GeckoHttp(private val engine: Lazy<BrowserEngine>) : HttpGet, SuggestGet {
    override suspend fun get(url: String, accept: String, maxBytes: Int, userAgent: String): HttpResponse? {
        if (!engine.isInitialized()) return null
        // An icon is stored on disk, so it is never fetched for a private tab (ADR 008, 014).
        return engine.value.fetchBytes(url, accept, null, maxBytes, htmlHead = accept.startsWith("text/html"), private = false, userAgent = userAgent)
            ?.let { HttpResponse(it.finalUrl, it.body) }
    }

    override suspend fun getCapped(url: String, accept: String, acceptLanguage: String, maxBytes: Int, private: Boolean): HttpResponse? {
        if (!engine.isInitialized()) return null
        return engine.value.fetchBytes(url, accept, acceptLanguage, maxBytes, htmlHead = false, private = private)
            ?.let { HttpResponse(it.finalUrl, it.body) }
    }
}
