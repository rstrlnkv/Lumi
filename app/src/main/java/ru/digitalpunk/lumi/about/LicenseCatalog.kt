package ru.digitalpunk.lumi.about

import org.json.JSONException
import org.json.JSONObject

data class LicenseText(val name: String, val text: String)

data class Library(val id: String, val name: String, val version: String?, val licenses: List<LicenseText>)

/**
 * The bundled libraries from the AboutLibraries plugin's JSON (`res/raw/aboutlibraries.json`), sorted by name.
 * A license the plugin has no text for gets an empty [LicenseText.text]; unreadable input gives an empty list.
 */
fun parseLicenses(json: String): List<Library> = try {
    val root = JSONObject(json)
    val licenses = root.optJSONObject("licenses") ?: JSONObject()
    val libraries = root.getJSONArray("libraries")
    (0 until libraries.length()).map { i ->
        val lib = libraries.getJSONObject(i)
        val ids = lib.optJSONArray("licenses")
        Library(
            id = lib.getString("uniqueId"),
            name = lib.optString("name").ifBlank { lib.getString("uniqueId") },
            version = lib.optString("artifactVersion").ifBlank { null },
            licenses = (0 until (ids?.length() ?: 0)).map { j ->
                val key = ids!!.getString(j)
                val license = licenses.optJSONObject(key)
                LicenseText(license?.optString("name")?.ifBlank { null } ?: key, license?.optString("content").orEmpty())
            },
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
} catch (_: JSONException) {
    emptyList()
}
