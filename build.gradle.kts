plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.aboutlibraries) apply false
}

// The build number counts the distinct commits of origin/dev and HEAD (Core standard "versioning", rule 2, project exception: only dev for now);
// gradle/build-number.sh makes it. Refuse at configuration time, before anything compiles, when it cannot be exact; there is no fallback number.
fun refuse(why: String): Nothing = throw GradleException("Lumi build refused: $why")

fun git(vararg args: String): Pair<Int, String> {
    val out = providers.exec {
        commandLine(listOf("git") + args); workingDir = rootDir; isIgnoreExitValue = true
        // No system or user config: no include, core.* or replace setting outside the repository can change the build number or the channel.
        environment("GIT_CONFIG_NOSYSTEM", "1")
        environment("GIT_CONFIG_GLOBAL", "/dev/null")
    }
    val code = try { out.result.get().exitValue } catch (e: Exception) { refuse("git cannot run (${e.javaClass.simpleName})") }
    return code to out.standardOutput.asText.get().trim()
}

val lumiVersionCode: Int = run {
    val script = providers.exec {
        commandLine("bash", "gradle/build-number.sh"); workingDir = rootDir; isIgnoreExitValue = true
    }
    val code = try { script.result.get().exitValue } catch (e: Exception) { refuse("gradle/build-number.sh cannot run (${e.javaClass.simpleName})") }
    if (code != 0) refuse(script.standardError.asText.get().trim())
    script.standardOutput.asText.get().trim().toIntOrNull() ?: refuse("gradle/build-number.sh printed no build number")
}

// The channel comes from the branch, not the tree: a fast-forward puts the same commit on dev, beta and main once those branches exist.
val lumiChannel: String = when (git("rev-parse", "--abbrev-ref", "HEAD").second) {
    "main" -> "STABLE"
    "beta" -> "BETA"
    else -> "DEV"
}

// Read from the project's own file, so a lumi.version in ~/.gradle/gradle.properties cannot override it.
val lumiVersionName: String = run {
    val text = providers.fileContents(layout.projectDirectory.file("gradle.properties")).asText.get()
    val v = java.util.Properties().apply { load(text.reader()) }.getProperty("lumi.version")?.trim()
        ?: refuse("lumi.version is missing in gradle.properties")
    if (!Regex("""\d+\.\d+\.\d+""").matches(v)) refuse("lumi.version '$v' is not X.Y.Z")
    v
}
extra["lumiVersionCode"] = lumiVersionCode
extra["lumiVersionName"] = lumiVersionName
extra["lumiChannel"] = lumiChannel
