plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.aboutlibraries) apply false
}

// The build number is the commit count of this project's own history (Core standard "versioning").
// Refuse at configuration time, before anything compiles, when the count cannot be exact; there is no fallback number.
fun refuse(why: String): Nothing = throw GradleException("Lumi build refused: $why")

fun git(vararg args: String): Pair<Int, String> {
    val out = providers.exec {
        commandLine(listOf("git") + args); workingDir = rootDir; isIgnoreExitValue = true
        // No system or user config: no include, core.* or replace setting outside the repository can change the count.
        environment("GIT_CONFIG_NOSYSTEM", "1")
        environment("GIT_CONFIG_GLOBAL", "/dev/null")
    }
    val code = try { out.result.get().exitValue } catch (e: Exception) { refuse("git cannot run (${e.javaClass.simpleName})") }
    return code to out.standardOutput.asText.get().trim()
}

val lumiVersionCode: Int = run {
    // The -c style overrides from the environment are not covered by the clean config above, so they are refused.
    for (name in listOf("GIT_DIR", "GIT_WORK_TREE", "GIT_GRAFT_FILE", "GIT_REPLACE_REF_BASE", "GIT_CONFIG_COUNT", "GIT_CONFIG_PARAMETERS", "GIT_NO_REPLACE_OBJECTS")) {
        if (providers.environmentVariable(name).isPresent) refuse("$name is set; git must read this project's own history with no injected config")
    }
    val (topCode, top) = git("rev-parse", "--show-toplevel")
    if (topCode != 0) refuse("no git repository at the project root")
    if (File(top).canonicalFile != rootDir.canonicalFile) refuse("git's top level ($top) is not the project root")
    val (shallowCode, shallow) = git("rev-parse", "--is-shallow-repository")
    if (shallowCode != 0 || shallow != "false") refuse("shallow clone (--is-shallow-repository printed '$shallow'); fetch the full history")
    if (git("for-each-ref", "refs/replace").second.isNotEmpty()) refuse("replace refs exist")
    val (graftsCode, grafts) = git("rev-parse", "--path-format=absolute", "--git-path", "info/grafts")
    if (graftsCode != 0) refuse("cannot locate the git directory")
    if (File(grafts).exists()) refuse("grafts exist")
    val (countCode, count) = git("rev-list", "--count", "HEAD")
    if (countCode != 0) refuse("no commits, cannot count the history")
    count.toIntOrNull()?.takeIf { it > 0 } ?: refuse("commit count '$count' is not a positive integer")
}

// Read from the project's own file, so a lumi.version in ~/.gradle/gradle.properties cannot override it.
val lumiVersionName: String = run {
    val text = providers.fileContents(layout.projectDirectory.file("gradle.properties")).asText.get()
    val v = java.util.Properties().apply { load(text.reader()) }.getProperty("lumi.version")?.trim()
        ?: refuse("lumi.version is missing in gradle.properties")
    if (!Regex("""\d+\.\d+\.\d+(-dev\.\d+)?""").matches(v)) refuse("lumi.version '$v' is not X.Y.Z or X.Y.Z-dev.N")
    v
}
extra["lumiVersionCode"] = lumiVersionCode
extra["lumiVersionName"] = lumiVersionName
