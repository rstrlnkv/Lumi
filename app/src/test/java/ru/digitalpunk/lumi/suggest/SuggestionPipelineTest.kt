package ru.digitalpunk.lumi.suggest

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ru.digitalpunk.lumi.data.search.SearchEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A fake clock whose sleeps the test releases, and a fake engine: no real time, no network. */
class SuggestionPipelineTest {
    private class GateClock : SuggestClock {
        val sleeps = Channel<CompletableDeferred<Unit>>(Channel.UNLIMITED)
        var expire = false
        override suspend fun sleep(ms: Long) = CompletableDeferred<Unit>().also { sleeps.send(it) }.await()
        override suspend fun <T> timeout(ms: Long, block: suspend () -> T): T? = if (expire) null else block()
    }

    private val clock = GateClock()
    private val calls = Channel<String>(Channel.UNLIMITED)
    private var answer: suspend (String) -> List<String>? = { listOf("$it one", "$it two") }
    private val pipeline = SuggestionPipeline(
        local = { typed, _ -> listOf(Suggestion.History("", "https://$typed.com/")) },
        remote = { _, q -> calls.send(q); answer(q) },
        clock = clock,
    )
    private val google = SearchEngine.Google

    private fun scenario(block: suspend Scenario.() -> Unit) = runBlocking<Unit> {
        val queries = Channel<SuggestQuery>(Channel.UNLIMITED)
        val results = Channel<List<Suggestion>>(Channel.UNLIMITED)
        val job = launch { pipeline.run(queries.consumeAsFlow()).collect { results.send(it) } }
        withTimeout(5_000) { Scenario(queries, results).block() }
        job.cancel()
    }

    private inner class Scenario(val queries: Channel<SuggestQuery>, val results: Channel<List<Suggestion>>) {
        suspend fun type(text: String, engine: SearchEngine? = google) = queries.send(SuggestQuery(text, engine))
        suspend fun next() = results.receive()
        suspend fun releaseSleep() = clock.sleeps.receive().complete(Unit)
    }

    private fun local(t: String) = Suggestion.History("", "https://$t.com/")

    @Test fun localRowsComeAtOnceAndEngineRowsJoinAfterTheDebounce() = scenario {
        type("git")
        assertEquals(listOf(local("git")), next())
        releaseSleep()
        assertEquals(listOf(local("git"), Suggestion.Search("git one"), Suggestion.Search("git two")), next())
        assertEquals("git", calls.receive())
    }

    @Test fun threeQuickKeysMakeOneRequest() = scenario {
        type("g"); next(); val first = clock.sleeps.receive()
        type("gi"); next(); val second = clock.sleeps.receive()
        type("git"); next()
        first.complete(Unit); second.complete(Unit)
        releaseSleep()
        next()
        assertEquals("git", calls.receive())
        assertTrue(calls.tryReceive().isFailure)
    }

    @Test fun aNewKeyCancelsTheRequestInFlightAndItsAnswerIsNeverShown() = scenario {
        val gate = CompletableDeferred<List<String>?>()
        answer = { if (it == "gi") gate.await() else listOf("git hub") }
        type("gi"); next(); releaseSleep()
        assertEquals("gi", calls.receive())
        type("git")
        assertEquals(listOf(local("git")), next())
        gate.complete(listOf("gi old"))
        releaseSleep()
        assertEquals(listOf(local("git"), Suggestion.Search("git hub")), next())
    }

    @Test fun aTimeoutAnErrorOrNoAnswerLeavesLocalRowsOnly() = scenario {
        clock.expire = true
        type("a"); next(); releaseSleep()
        assertEquals(listOf(local("a")), next())
        clock.expire = false
        answer = { error("offline") }
        type("ab"); next(); releaseSleep()
        assertEquals(listOf(local("ab")), next())
        answer = { null }
        type("abc"); next(); releaseSleep()
        assertEquals(listOf(local("abc")), next())
    }

    @Test fun withTheSwitchOffOrAnIneligibleTextNoRequestGoes() = scenario {
        type("git", engine = null)
        assertEquals(listOf(local("git")), next())
        type("https://x.com")
        assertEquals(listOf(local("https://x.com")), next())
        type("")
        assertEquals(emptyList(), next())
        assertTrue(clock.sleeps.tryReceive().isFailure)
        assertTrue(calls.tryReceive().isFailure)
    }

    @Test fun theLastEngineRowsStayWhileTheyStillMatchAndGoAfterAFailure() = scenario {
        type("gi"); next(); releaseSleep(); next()
        type("git")
        assertEquals(listOf(local("git")), next())
        clock.sleeps.receive() // "git" waits; the next key cancels it
        type("gi")
        assertEquals(listOf(local("gi"), Suggestion.Search("gi one"), Suggestion.Search("gi two")), next())
        answer = { null }
        releaseSleep(); next()
        type("g")
        assertEquals(listOf(local("g")), next())
    }

    @Test fun onlyPlainTextUnderAHundredCharactersGoesToTheEngine() {
        listOf("git", "re: hello", "kotlin flow", "a/b c", "100% sure").forEach { assertTrue(engineEligible(it), it) }
        listOf("", "  ", "http://x", "about:config", "javascript:alert(1)", "localhost:8080", "github.com/x", "me@mail.ru", "q".repeat(101))
            .forEach { assertFalse(engineEligible(it), it) }
    }

    @Test fun aLocalOrPrivateNetworkAddressNeverGoesToTheEngine() {
        listOf("nas", "kotlin 2.0", "1.5", "c++").forEach { assertTrue(engineEligible(it), it) }
        listOf(
            "192.168.1.1:8080", "10.0.0.5", "10.0.0.5?x", "[::1]", "[fe80::1]:80", "nas:5000", "foo.bar:", "nas:", "localhost",
            "dev.localhost", "nas.local", "jira.corp.internal", "router.lan", "Router.LAN.",
        ).forEach { assertFalse(engineEligible(it), it) }
    }
}
