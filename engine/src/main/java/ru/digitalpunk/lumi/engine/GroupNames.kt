package ru.digitalpunk.lumi.engine

/** The longest group name, counted in code points. */
const val MAX_GROUP_NAME_LENGTH = 20

private val lineBreak = Regex("\r\n|[\n\r  ]")
private val invisible = Regex("\\p{Cf}")

/** For text typed into the name field: line breaks become spaces and the length is capped. Not trimmed, so "My Work" can be typed. */
fun sanitizeGroupInput(raw: String): String {
    val flat = raw.replace(lineBreak, " ")
    if (flat.codePointCount(0, flat.length) <= MAX_GROUP_NAME_LENGTH) return flat
    return flat.substring(0, flat.offsetByCodePoints(0, MAX_GROUP_NAME_LENGTH)).trimEnd('\u200D')
}

/** The name as stored; empty means it is not a usable name. A name of only invisible format characters (zero width space, joiner, byte order mark) is empty. */
fun normalizeGroupName(raw: String): String {
    val name = sanitizeGroupInput(raw).trim()
    return if (name.replace(invisible, "").isBlank()) "" else name
}

/** Whether [name] equals one of [others], ignoring case and surrounding spaces. */
fun groupNameTaken(name: String, others: List<String>): Boolean =
    others.any { it.trim().equals(name.trim(), ignoreCase = true) }
