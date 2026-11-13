package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import ru.digitalpunk.lumi.data.search.SuggestGet
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** One open TLS connection. */
internal interface Connection : Closeable {
    val input: InputStream
    val output: OutputStream
}

private const val MAX_ADDRESSES_TRIED = 2

/** Put in the open-connection slot on cancel: a connection that opens later is closed at once. */
private val CANCELLED = Closeable {}

/**
 * A plain GET outside GeckoView (ADR 008). Each hop must pass [isAllowedIconUrl] (https, port 443, no user info, a public
 * name); its host is then resolved, refused unless every record is public ([pickPublicAddresses]), and the socket goes to
 * the checked address, so nothing resolves the name a second time. TLS SNI and the certificate check use the real host.
 * No cookies, no Referer, no compression; 10 s per connect and read. [get] follows at most [MAX_REDIRECTS] redirects and
 * reads at most [MAX_BYTES]; [getCapped] takes both limits from its caller.
 * Null on any error. A cancelled call returns at once, even while the lookup, connect or handshake still blocks a thread.
 * Two users (ADR 008, ADR 009): site icons through [get], search suggestions through [getCapped], each with its own instance.
 */
class PinnedHttpsGet internal constructor(
    private val resolve: (String) -> List<InetAddress>,
    private val connect: (host: String, address: InetAddress) -> Connection,
) : HttpGet, SuggestGet {
    constructor() : this({ InetAddress.getAllByName(it).toList() }, ::tlsConnect)

    /** Blocking exchanges of this instance still running, cancelled ones included: their threads decrement it on return. */
    internal val live = AtomicInteger()

    override suspend fun get(url: String, accept: String): HttpResponse? =
        fetch(url, accept, null, MAX_BYTES, if (accept == IconFetcher.HTML) ReadMode.Html else ReadMode.Image, MAX_REDIRECTS, Int.MAX_VALUE)

    /** A body of at most [maxBytes] (null when longer); null without connecting while [maxLive] exchanges still run. */
    override suspend fun getCapped(url: String, accept: String, acceptLanguage: String, maxBytes: Int, maxRedirects: Int, maxLive: Int): HttpResponse? =
        fetch(url, accept, acceptLanguage, maxBytes, ReadMode.Image, maxRedirects, maxLive)

    private suspend fun fetch(
        url: String, accept: String, language: String?, maxBytes: Int, mode: ReadMode, maxRedirects: Int, maxLive: Int,
    ): HttpResponse? {
        var current = url
        for (hop in 0..maxRedirects) {
            if (!isAllowedIconUrl(current)) return null
            val host = iconTarget(current)?.host ?: return null
            val page = current
            if (live.get() >= maxLive) return null
            val result = closeOnCancel { open ->
                live.incrementAndGet()
                try {
                    exchange(host, page, accept, language, maxBytes, mode, open)
                } finally {
                    live.decrementAndGet()
                }
            }
            when (result) {
                is HttpResponse -> return result
                is Hop -> current = resolveRedirect(current, result.location, hop + 1) ?: return null
                else -> return null
            }
        }
        return null
    }

    private class Hop(val location: String)

    /** Blocking: resolve, check, connect, one request and response. Any exception is an error, so null. */
    private fun exchange(
        host: String, url: String, accept: String, language: String?, maxBytes: Int, mode: ReadMode, open: AtomicReference<Closeable?>,
    ): Any? = try {
        val addresses = pickPublicAddresses(resolve(host))
        if (addresses == null || open.get() != null) return null
        val connection = connectFirst(host, addresses.take(MAX_ADDRESSES_TRIED)) ?: return null
        if (!open.compareAndSet(null, connection)) {
            connection.close()
            return null
        }
        connection.use { c ->
            c.output.write(buildRequest(host, pathAndQuery(url), accept, language))
            c.output.flush()
            val input = BufferedInputStream(c.input)
            val head = readHead(input) ?: return@use null
            when (head.status) {
                301, 302, 303, 307, 308 -> head.headers["location"]?.let(::Hop)
                200 -> bodyStream(head, input)
                    ?.let { readBounded(it, maxBytes, mode) }
                    ?.let { HttpResponse(url, it) }
                else -> null
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun connectFirst(host: String, addresses: List<InetAddress>): Connection? {
        for (address in addresses) {
            try {
                return connect(host, address)
            } catch (_: IOException) {
            }
        }
        return null
    }

    private fun pathAndQuery(url: String): String {
        val uri = URI(url)
        return uri.rawPath.orEmpty().ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
    }

    /**
     * Blocking calls ignore cancellation, so [block] runs outside the caller's scope and a cancel returns at once.
     * Closing the open connection unblocks a read; a connection that opens after the cancel is closed unused.
     */
    private suspend fun <T> closeOnCancel(block: (AtomicReference<Closeable?>) -> T): T {
        val open = AtomicReference<Closeable?>()
        val work = CoroutineScope(Dispatchers.IO).async { block(open) }
        try {
            return work.await()
        } catch (e: CancellationException) {
            work.cancel()
            open.getAndSet(CANCELLED)?.close()
            throw e
        }
    }
}

/** TCP to the checked [address], then TLS with SNI and a hostname check for [host]. */
private fun tlsConnect(host: String, address: InetAddress): Connection {
    val raw = Socket()
    try {
        raw.connect(InetSocketAddress(address, 443), HTTP_TIMEOUT_MS)
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, host, 443, true) as SSLSocket
        ssl.sslParameters = ssl.sslParameters.apply {
            serverNames = listOf(SNIHostName(host))
            endpointIdentificationAlgorithm = "HTTPS"
        }
        ssl.soTimeout = HTTP_TIMEOUT_MS
        ssl.startHandshake()
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ssl.session)) throw SSLPeerUnverifiedException(host)
        return object : Connection {
            override val input: InputStream = ssl.inputStream
            override val output: OutputStream = ssl.outputStream
            override fun close() = ssl.close()
        }
    } catch (e: Exception) {
        raw.close()
        throw e
    }
}
