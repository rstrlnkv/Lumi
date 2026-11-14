package ru.digitalpunk.lumi.about

import java.time.LocalDate

/** One `## [version] - date` section of CHANGELOG.md, `[YANKED]` ones included; [date] is null when missing or invalid. */
data class Release(val version: String, val date: LocalDate?, val items: List<String>)

private val RELEASE = Regex("""^## \[([^\]]+)\](?: - (\d{4}-\d{2}-\d{2}))?(?: \[YANKED\])?\s*$""")

/**
 * The released sections of a Keep a Changelog file, in file order (newest first). `[Unreleased]` and the
 * `###` group headings are dropped; an item starts with `- ` and goes on over lines indented by two spaces.
 */
fun parseChangelog(text: String): List<Release> {
    val releases = mutableListOf<Release>()
    var items: MutableList<String>? = null
    for (line in text.lines().map { it.trimEnd('\r') }) {
        val heading = RELEASE.matchEntire(line)
        when {
            heading != null -> {
                val (version, date) = heading.destructured
                items = if (version.equals("Unreleased", ignoreCase = true)) null else mutableListOf<String>().also {
                    releases += Release(version, runCatching { LocalDate.parse(date) }.getOrNull(), it)
                }
            }
            line.startsWith("## ") -> items = null
            items == null -> {}
            line.startsWith("- ") -> items += line.removePrefix("- ").trim()
            line.startsWith("  ") && line.isNotBlank() && items.isNotEmpty() -> items[items.lastIndex] += " " + line.trim()
        }
    }
    return releases
}
