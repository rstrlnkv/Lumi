package ru.digitalpunk.lumi.about

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Project exception to rule 2 of the versioning standard (only dev is long-lived for now): the build number counts the
 * distinct commits of origin/dev and HEAD after a fetch; origin/beta and origin/main are ignored. `gradle/build-number.sh` makes it; these tests run it on temporary repositories whose "origin"
 * is a local folder, so no network is used.
 */
class BuildNumberScriptTest {
    private val script = File("../gradle/build-number.sh").canonicalPath
    private val temp = Files.createTempDirectory("lumi-build-number").toFile()
    private val origin = File(temp, "origin.git")
    private val work = File(temp, "work")

    @AfterTest fun cleanUp() { temp.deleteRecursively() }

    private fun run(dir: File, vararg cmd: String, env: Map<String, String> = emptyMap()): Triple<Int, String, String> {
        val pb = ProcessBuilder(*cmd).directory(dir)
        pb.environment().keys.removeAll { it.startsWith("GIT_") }
        pb.environment().putAll(mapOf(
            "GIT_AUTHOR_NAME" to "t", "GIT_AUTHOR_EMAIL" to "t@t", "GIT_COMMITTER_NAME" to "t", "GIT_COMMITTER_EMAIL" to "t@t",
            "GIT_CONFIG_GLOBAL" to "/dev/null", "GIT_CONFIG_NOSYSTEM" to "1",
        ))
        pb.environment().putAll(env)
        val out = File(temp, "out.txt"); val err = File(temp, "err.txt")
        pb.redirectOutput(out).redirectError(err)
        val code = pb.start().waitFor()
        return Triple(code, out.readText().trim(), err.readText().trim())
    }

    private fun git(dir: File, vararg args: String): String {
        val (code, out, err) = run(dir, "git", *args)
        assertEquals(0, code, "git ${args.toList()}: $err")
        return out
    }

    private fun commit(dir: File, name: String) {
        File(dir, name).writeText(name)
        git(dir, "add", name); git(dir, "commit", "-q", "-m", name)
    }

    /** origin has dev (2 commits); `work` is a clone on a branch with its own commit. `strayBranches` adds beta and main with a commit dev lacks. */
    private fun fixture(withDev: Boolean = true, strayBranches: Boolean = false) {
        origin.mkdirs()
        git(origin, "init", "-q", "--bare", "-b", "dev")
        work.mkdirs()
        git(work, "init", "-q", "-b", "dev")
        git(work, "remote", "add", "origin", origin.path)
        commit(work, "a")
        commit(work, "b")
        if (withDev) git(work, "push", "-q", "origin", "dev") else git(work, "push", "-q", "origin", "dev:other")
        git(work, "checkout", "-q", "-b", "feature")
        commit(work, "c")
        if (strayBranches) {
            git(work, "checkout", "-q", "-b", "stray", "dev"); commit(work, "x")
            git(work, "push", "-q", "origin", "stray:beta", "stray:main")
            git(work, "checkout", "-q", "feature")
        }
    }

    private fun number(dir: File = work, env: Map<String, String> = emptyMap()) = run(dir, "bash", script, env = env)

    @Test fun `counts the distinct commits of origin dev and HEAD`() {
        fixture()
        assertEquals(Triple(0, "3", ""), number()) // a, b (dev), c (HEAD)
    }

    @Test fun `ignores origin beta and origin main`() {
        fixture(strayBranches = true)
        assertEquals(Triple(0, "3", ""), number()) // x on beta and main is not counted
    }

    @Test fun `does not refuse when origin has no beta and no main`() {
        fixture()
        assertEquals(0, number().first)
    }

    @Test fun `a commit on origin dev and HEAD counts once`() {
        fixture()
        git(work, "checkout", "-q", "dev")
        assertEquals("2", number().second) // a, b; HEAD is dev
    }

    @Test fun `a commit pushed to origin after the last fetch is counted`() {
        fixture()
        val other = File(temp, "other")
        git(temp, "clone", "-q", "-b", "dev", origin.path, other.path)
        commit(other, "d")
        git(other, "push", "-q", "origin", "dev")
        assertEquals("4", number().second)
    }

    @Test fun `a rebuild of a tagged commit takes the number from the tag`() {
        fixture()
        git(work, "tag", "v0.1.0.DEV.7")
        assertEquals("7", number().second)
    }

    @Test fun `a STABLE tag does not set the number`() {
        fixture()
        git(work, "tag", "v0.1.0")
        assertEquals("3", number().second)
    }

    private fun assertRefused(result: Triple<Int, String, String>, why: String) {
        assertEquals(1, result.first, result.toString())
        assertEquals("", result.second)
        assertTrue(why in result.third, "expected '$why' in: ${result.third}")
    }

    @Test fun `refuses when origin dev is missing`() {
        fixture(withDev = false)
        assertRefused(number(), "origin/dev")
    }

    @Test fun `refuses when the fetch fails`() {
        fixture()
        git(work, "remote", "set-url", "origin", File(temp, "gone.git").path)
        assertRefused(number(), "fetch")
    }

    @Test fun `refuses a shallow history`() {
        fixture()
        val shallow = File(temp, "shallow")
        git(temp, "clone", "-q", "--depth", "1", "file://${origin.path}", shallow.path)
        assertRefused(number(shallow), "shallow")
    }

    @Test fun `refuses when GIT_DIR or GIT_WORK_TREE is set`() {
        fixture()
        assertRefused(number(env = mapOf("GIT_DIR" to File(work, ".git").path)), "GIT_DIR")
        assertRefused(number(env = mapOf("GIT_WORK_TREE" to work.path)), "GIT_WORK_TREE")
    }

    @Test fun `refuses when git's top level is not the project root`() {
        fixture()
        val sub = File(work, "sub").apply { mkdirs() }
        assertRefused(number(sub), "top level")
    }

    @Test fun `refuses when there is no repository or no commit`() {
        val empty = File(temp, "empty").apply { mkdirs() }
        assertRefused(number(empty), "repository")
        git(empty, "init", "-q")
        assertRefused(number(empty), "commit")
    }
}
