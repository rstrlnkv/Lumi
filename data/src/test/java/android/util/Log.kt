package android.util

/** Unit-test stand-in for the platform logger, whose stub throws "not mocked"; lets a test reach a repository's failure path. */
object Log {
    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int = 0
    @JvmStatic fun e(tag: String?, msg: String?): Int = 0
    @JvmStatic fun w(tag: String?, msg: String?, tr: Throwable?): Int = 0
    @JvmStatic fun w(tag: String?, msg: String?): Int = 0
}
