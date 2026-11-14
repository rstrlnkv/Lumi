package ru.digitalpunk.lumi.data.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ru.digitalpunk.lumi.data.icons.HttpResponse
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A suggestion request is cut off by the pipeline's timeout or a new keystroke: the cancel must reach the fetch, and no failure may turn into a list. */
class EngineSuggestClientCancelTest {
    @Test fun cancellingTheCallerCancelsTheFetchUnderneath() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val client = EngineSuggestClient(object : SuggestGet {
            override suspend fun getCapped(url: String, accept: String, acceptLanguage: String, maxBytes: Int, private: Boolean): HttpResponse? {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        })
        val job = async(start = CoroutineStart.UNDISPATCHED) { client.suggest(SearchEngine.Google, "git", "en", private = true) }
        withTimeout(5_000) { started.await() }
        job.cancel()
        withTimeout(5_000) { cancelled.await() }
        assertTrue(job.isCancelled)
    }

    @Test fun aFailureOfTheFetchIsNeverTurnedIntoAnAnswerList() = runBlocking<Unit> {
        val client = EngineSuggestClient(object : SuggestGet {
            override suspend fun getCapped(url: String, accept: String, acceptLanguage: String, maxBytes: Int, private: Boolean): HttpResponse? = throw IOException("down")
        })
        assertFailsWith<IOException> { client.suggest(SearchEngine.Google, "git", "en", private = false) }
    }

    @Test fun everyBuiltInEngineAsksInThePrivateContextWhenTheTabIsPrivate() = runBlocking<Unit> {
        val seen = mutableListOf<Boolean>()
        val client = EngineSuggestClient(object : SuggestGet {
            override suspend fun getCapped(url: String, accept: String, acceptLanguage: String, maxBytes: Int, private: Boolean): HttpResponse? {
                seen += private
                return HttpResponse(url, """["q",["a"]]""".toByteArray())
            }
        })
        for (engine in SearchEngine.values()) assertEquals(listOf("a"), client.suggest(engine, "q", "ru", private = true))
        assertEquals(List(SearchEngine.values().size) { true }, seen)
    }
}
