package ru.digitalpunk.lumi.suggest

private const val MAX_LOCAL = 3
private const val MAX_ENGINE = 5

/**
 * The list under the address bar, best first: the local rows unchanged, then at most [MAX_ENGINE] engine queries in the
 * engine's order, without blanks, the typed text itself or repeats (case ignored). At most eight rows.
 */
fun mergeSuggestions(typed: String, local: List<Suggestion>, engine: List<String>): List<Suggestion> {
    val t = typed.trim()
    val queries = ArrayList<String>()
    for (raw in engine) {
        if (queries.size == MAX_ENGINE) break
        val s = raw.trim()
        if (s.isEmpty() || s.equals(t, ignoreCase = true) || queries.any { it.equals(s, ignoreCase = true) }) continue
        queries += s
    }
    return local.take(MAX_LOCAL) + queries.map { Suggestion.Search(it) }
}
