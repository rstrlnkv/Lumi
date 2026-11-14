package ru.digitalpunk.lumi.updates

import ru.digitalpunk.lumi.about.Channel
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateCheckerTest {
    private val release = Release(ReleaseTag(0, 1, 0, Channel.DEV, 3613700), "v0.1.0.DEV.3613700", "https://github.com/rstrlnkv/lumi/releases/tag/v0.1.0.DEV.3613700")

    @Test fun `a body of exactly the cap is read whole`() =
        assertEquals(1_048_576, readCapped(ByteArrayInputStream(ByteArray(1_048_576)), 1_048_576)?.size)

    @Test fun `a body one byte over the cap is refused`() =
        assertNull(readCapped(ByteArrayInputStream(ByteArray(1_048_577)), 1_048_576))

    @Test fun `a new release is announced`() = assertEquals(release, shouldNotify(CheckResult.Available(release), null))

    @Test fun `a release already announced is not announced again`() =
        assertNull(shouldNotify(CheckResult.Available(release), "v0.1.0.DEV.3613700"))

    @Test fun `no update or a failed check announces nothing`() {
        assertNull(shouldNotify(CheckResult.UpToDate, null))
        assertNull(shouldNotify(CheckResult.Failed, null))
    }

    @Test fun `any failure inside a check is a failed check`() = kotlinx.coroutines.runBlocking {
        assertEquals(CheckResult.Failed, guarded { throw IllegalStateException("disk full") })
        assertEquals(CheckResult.Failed, guarded { throw ClassCastException() })
        assertEquals(CheckResult.UpToDate, guarded { CheckResult.UpToDate })
    }

    @Test fun `a cancelled check stays cancelled`() {
        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
            kotlinx.coroutines.runBlocking { guarded { throw kotlinx.coroutines.CancellationException() } }
        }
    }
}
