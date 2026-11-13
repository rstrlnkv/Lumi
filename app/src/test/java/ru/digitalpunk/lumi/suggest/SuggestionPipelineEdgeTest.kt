package ru.digitalpunk.lumi.suggest

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ru.digitalpunk.lumi.data.search.SearchEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestionPipelineEdgeTest {
    /** Sleeps pass at once; every wait is written down, so the test can read the debounce and the timeout. */
    private class RecordingClock : SuggestClock {
        val sleeps = mutableListOf<Long>()
        val timeouts = mutableListOf<Long>()
        override suspend fun sleep(ms: Long) { sleeps += ms }
        override suspend fun <T> timeout(ms: Long, block: suspend () -> T): T? { timeouts += ms; return block() }
    }

    /** A clock whose sleep waits for the test to release it. */
    private class GateClock : SuggestClock {
        val sleeps = Channel<CompletableDeferred<Unit>>(Channel.UNLIMITED)
        override suspend fun sleep(ms: Long) = CompletableDeferred<Unit>().also { sleeps.send(it) }.await()
        override suspend fun <T> timeout(ms: Long, block: suspend () -> T): T? = block()
    }

    private val calls = Channel<String>(Channel.UNLIMITED)
    private var answer: suspend (String) -> List<String>? = { listOf("$it one") }
    private val pins = mutableListOf<String?>()

    private fun pipeline(clock: SuggestClock) = SuggestionPipeline(
        local = { t, pin -> pins += pin; listOf(Suggestion.History("", "https://$t.com/")) },
        remote = { _, q -> calls.send(q); answer(q) },
        clock = clock,
    )

    private fun scenario(clock: SuggestClock, block: suspend (send: suspend (SuggestQuery) -> Unit, next: suspend () -> List<Suggestion>) -> Unit) = runBlocking<Unit> {
        val queries = Channel<SuggestQuery>(Channel.UNLIMITED)
        val results = Channel<List<Suggestion>>(Channel.UNLIMITED)
        val job = launch { pipeline(clock).run(queries.consumeAsFlow()).collect { results.send(it) } }
        try {
            withTimeout(5_000) { block({ queries.send(it) }, { results.receive() }) }
        } finally {
            job.cancel()
        }
    }

    private fun local(t: String) = Suggestion.History("", "https://$t.com/")
    private val g = SearchEngine.Google

    @Test fun theDebounceIs150MillisecondsAndTheEngineGets1500() {
        val clock = RecordingClock()
        scenario(clock) { send, next ->
            send(SuggestQuery("git", g)); next(); next()
        }
        assertEquals(listOf(150L), clock.sleeps)
        assertEquals(listOf(1500L), clock.timeouts)
    }

    @Test fun theRealClockGivesUpOnASlowBlockAndReturnsAFastOne() = runBlocking<Unit> {
        val start = System.nanoTime()
        assertNull(SuggestClock.System.timeout(50) { delay(10_000); "late" })
        assertEquals("fast", SuggestClock.System.timeout(5_000) { "fast" })
        assertTrue(System.nanoTime() - start < 3_000_000_000L)
    }

    @Test fun theEngineGetsTheTrimmedTextNotTheRawOne() {
        scenario(RecordingClock()) { send, next ->
            send(SuggestQuery("  git  ", g)); next(); next()
            assertEquals("git", calls.receive())
        }
    }

    @Test fun theSameQueryTwiceInARowDoesNotRestartTheList() {
        scenario(RecordingClock()) { send, next ->
            send(SuggestQuery("git", g)); next(); next()
            send(SuggestQuery("git", g))
            send(SuggestQuery("gi", g))
            assertEquals(listOf(local("gi"), Suggestion.Search("git one")), next())
        }
    }

    @Test fun turningTheSwitchOffWhileARequestIsInFlightDropsItsAnswer() {
        val clock = GateClock()
        val gate = CompletableDeferred<List<String>?>()
        answer = { gate.await() }
        scenario(clock) { send, next ->
            send(SuggestQuery("git", g)); next()
            clock.sleeps.receive().complete(Unit)
            assertEquals("git", calls.receive())
            send(SuggestQuery("git", null))
            assertEquals(listOf(local("git")), next())
            gate.complete(listOf("late answer"))
            send(SuggestQuery("gix", null))
            assertEquals(listOf(local("gix")), next())
        }
    }

    @Test fun engineRowsFromBeforeTheSwitchWentOffDoNotComeBackWhenItIsTurnedOnAgain() {
        scenario(RecordingClock()) { send, next ->
            send(SuggestQuery("gi", g)); next(); next()
            send(SuggestQuery("gi", null))
            assertEquals(listOf(local("gi")), next())
            send(SuggestQuery("gi", g))
            assertEquals(listOf(local("gi")), next())
        }
    }

    @Test fun theCompletedHostReachesTheLocalRanking() {
        scenario(RecordingClock()) { send, next ->
            send(SuggestQuery("git", null, pin = "github.com")); next()
            assertEquals("github.com", pins.last())
        }
    }

    @Test fun textOfExactlyAHundredCharactersGoesToTheEngineAndOneMoreDoesNot() {
        assertTrue(engineEligible("q".repeat(100)))
        assertFalse(engineEligible("q".repeat(101)))
    }

    @Test fun blankTextGivesNoRowsAndNoWait() {
        val clock = RecordingClock()
        scenario(clock) { send, next ->
            send(SuggestQuery("   ", g))
            assertEquals(emptyList(), next())
        }
        assertEquals(emptyList(), clock.sleeps)
    }
}
