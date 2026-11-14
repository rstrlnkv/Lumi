package ru.digitalpunk.lumi.about

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Inputs nobody fed `gradle/build-number.sh`: detached HEAD, grafts, replace refs, stale or shadowed refs, odd tags. */
class BuildNumberEdgeTest {
    private val script = File("../gradle/build-number.sh").canonicalPath
    private val temp = Files.createTempDirectory("lumi-build-number-edge").toFile()
    private val origin = File(temp, "origin.git")
    private val work = File(temp, "work")

    @AfterTest fun cleanUp() { temp.deleteRecursively() }

    private fun run(dir: File, vararg cmd: String): Triple<Int, String, String> {
        val pb = ProcessBuilder(*cmd).directory(dir)
        pb.environment().keys.removeAll { it.startsWith("GIT_") }
        pb.environment().putAll(mapOf(
            "GIT_AUTHOR_NAME" to "t", "GIT_AUTHOR_EMAIL" to "t@t", "GIT_COMMITTER_NAME" to "t", "GIT_COMMITTER_EMAIL" to "t@t",
            "GIT_CONFIG_GLOBAL" to "/dev/null", "GIT_CONFIG_NOSYSTEM" to "1",
        ))
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

    private fun commit(name: String) {
        File(work, name).writeText(name)
        git(work, "add", name); git(work, "commit", "-q", "-m", name)
    }

    /** main = a, beta = a, dev = a+b; work is on branch feature = a+b+c. Expected count: 3. */
    private fun fixture() {
        origin.mkdirs(); git(origin, "init", "-q", "--bare", "-b", "main")
        work.mkdirs(); git(work, "init", "-q", "-b", "main")
        git(work, "remote", "add", "origin", origin.path)
        commit("a"); git(work, "push", "-q", "origin", "main"); git(work, "push", "-q", "origin", "main:beta")
        git(work, "checkout", "-q", "-b", "dev"); commit("b"); git(work, "push", "-q", "origin", "dev")
        git(work, "checkout", "-q", "-b", "feature"); commit("c")
    }

    private fun number() = run(work, "bash", script)

    @Test fun `a detached HEAD is counted like a branch tip`() {
        fixture()
        git(work, "checkout", "-q", "--detach")
        assertEquals(Triple(0, "3", ""), number())
    }

    @Test fun `refuses when a graft file hides history`() {
        fixture()
        File(work, ".git/info").mkdirs()
        File(work, ".git/info/grafts").writeText(git(work, "rev-parse", "HEAD") + "\n")
        val r = number()
        assertEquals(1, r.first, r.toString()); assertTrue("graft" in r.third, r.third)
    }

    @Test fun `refuses when a replace ref rewrites history`() {
        fixture()
        git(work, "replace", "HEAD", git(work, "rev-parse", "HEAD~2"))
        val r = number()
        assertEquals(1, r.first, r.toString()); assertTrue("replace" in r.third, r.third)
    }

    @Test fun `refuses when a branch was deleted on origin and only a stale tracking ref is left`() {
        fixture()
        git(work, "fetch", "-q", "origin")
        git(origin, "branch", "-q", "-D", "beta")
        val r = number()
        assertEquals(1, r.first, "origin has no beta, but the stale origin/beta was counted: $r")
    }

    @Test fun `a local branch named like a remote branch does not change the count`() {
        fixture()
        git(work, "branch", "origin/dev", "feature") // refs/heads/origin/dev shadows refs/remotes/origin/dev in rev-list
        git(work, "checkout", "-q", "main")
        // origin/dev really holds a, b: the count of {a (main/beta), b (dev), HEAD = a} is 2
        assertEquals("2", number().second)
    }

    @Test fun `a tag named like a remote branch does not change the count`() {
        fixture()
        git(work, "tag", "origin/main", "feature")
        git(work, "checkout", "-q", "dev")
        assertEquals("2", number().second)
    }

    @Test fun `two tags on HEAD give the larger number and a tag on another commit is ignored`() {
        fixture()
        git(work, "tag", "v0.1.0.DEV.9", "HEAD~1")
        git(work, "tag", "v0.1.0.DEV.5"); git(work, "tag", "v0.1.0.BETA.12")
        assertEquals("12", number().second)
    }

    @Test fun `a tag number that is zero or too large for an Int is refused`() {
        fixture()
        git(work, "tag", "v0.1.0.DEV.0")
        assertEquals(1, number().first)
        git(work, "tag", "-d", "v0.1.0.DEV.0"); git(work, "tag", "v0.1.0.DEV.99999999999999999999")
        assertEquals(1, number().first)
        git(work, "tag", "-d", "v0.1.0.DEV.99999999999999999999"); git(work, "tag", "v0.1.0.DEV.2100000001")
        assertEquals(1, number().first)
    }

    @Test fun `a tag that only looks like a build tag does not set the number`() {
        fixture()
        git(work, "tag", "v0.1.0.DEV.7x"); git(work, "tag", "v0.1.0-dev.7"); git(work, "tag", "xv0.1.0.DEV.7")
        assertEquals("3", number().second)
    }

    @Test fun `refuses when the repository has no origin remote`() {
        fixture()
        git(work, "remote", "remove", "origin")
        assertEquals(1, number().first)
    }
}
