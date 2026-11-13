package ru.digitalpunk.lumi.suggest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull
import ru.digitalpunk.lumi.address.PLAIN_HTTP_HOST
import ru.digitalpunk.lumi.data.search.SearchEngine

/** Time for the pipeline; a test drives it by hand. */
interface SuggestClock {
    suspend fun sleep(ms: Long)

    /** [block]'s result, or null when [ms] pass first. */
    suspend fun <T> timeout(ms: Long, block: suspend () -> T): T?

    object System : SuggestClock {
        override suspend fun sleep(ms: Long) = delay(ms)
        override suspend fun <T> timeout(ms: Long, block: suspend () -> T): T? = withTimeoutOrNull(ms) { block() }
    }
}

/** What the person typed (without the inline completion), the engine to ask (null: switch off or not read yet), the completed host. */
data class SuggestQuery(val typed: String, val engine: SearchEngine?, val pin: String? = null)

private const val DEBOUNCE_MS = 150L
private const val TIMEOUT_MS = 1500L
private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:\\S")
private val LOCAL_NAME = Regex("""(^|\.)(localhost|local|internal|lan)\.?$""", RegexOption.IGNORE_CASE)

/**
 * The address bar's suggestions per key: local rows at once, then engine rows after [DEBOUNCE_MS] if the engine answers
 * within [TIMEOUT_MS]. A new key cancels the wait and the request. The last engine rows stay while they still start with
 * the typed text; a failure leaves local rows only, silently. The only caller of [remote] (ADR 009).
 */
class SuggestionPipeline(
    private val local: (typed: String, pin: String?) -> List<Suggestion>,
    private val remote: suspend (SearchEngine, String) -> List<String>?,
    private val clock: SuggestClock = SuggestClock.System,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun run(queries: Flow<SuggestQuery>): Flow<List<Suggestion>> = flow {
        var stale = emptyList<String>()
        emitAll(
            queries.distinctUntilChanged().transformLatest { q ->
                val typed = q.typed.trim()
                if (typed.isEmpty()) return@transformLatest emit(emptyList())
                val rows = local(q.typed, q.pin)
                val engine = q.engine
                if (engine == null || !engineEligible(typed)) {
                    stale = emptyList()
                    return@transformLatest emit(mergeSuggestions(typed, rows, emptyList()))
                }
                emit(mergeSuggestions(typed, rows, stale.filter { it.startsWith(typed, ignoreCase = true) }))
                clock.sleep(DEBOUNCE_MS)
                val answer = try {
                    clock.timeout(TIMEOUT_MS) { remote(engine, typed) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                stale = answer.orEmpty()
                emit(mergeSuggestions(typed, rows, stale))
            },
        )
    }.flowOn(Dispatchers.Default)
}

/**
 * Whether [typed] may go to the engine: plain text of at most [SearchEngine.MAX_QUERY] characters. Never a scheme
 * (`http:`, `about:`, `localhost:8080`), and never one word with `/` or `@` (a pasted address or `user@host`), with `:`
 * (`host:port`), or that is a local or private-network host: an IP literal, `localhost`, or a `.local`, `.internal` or
 * `.lan` name. A bare word such as `nas` still goes: it cannot be told from a search.
 */
fun engineEligible(typed: String): Boolean {
    val t = typed.trim()
    if (t.isEmpty() || t.length > SearchEngine.MAX_QUERY || SCHEME.containsMatchIn(t)) return false
    if (t.any { it.isWhitespace() }) return true
    return '/' !in t && '@' !in t && ':' !in t && !t.startsWith('[') && !PLAIN_HTTP_HOST.matches(t) && !LOCAL_NAME.containsMatchIn(t)
}
