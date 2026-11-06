package example.youtube

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.downloader.StreamingResponse
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import java.io.IOException
import java.util.Locale

/**
 * [Downloader] implementation for PipePipeExtractor backed by OkHttp.
 *
 * The extractor owns YouTube-specific headers (client name/version, Origin,
 * Referer, Cookie, Authorization) and adds them to each request. This class
 * only has to preserve those headers, add a browser-like User-Agent and
 * Accept-Language, and expose streaming responses so SABR batches are not
 * buffered whole in memory.
 */
class PipePipeDownloader(
    private val client: OkHttpClient = OkHttpClient(),
    private val cookieProvider: () -> String? = { null },
) : Downloader() {

    override fun execute(request: Request): Response {
        val call = client.newCall(buildRequest(request, includeCookie = true))
        call.execute().use { response ->
            val raw = response.body.bytes()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                raw.toString(Charsets.UTF_8),
                raw,
                response.request.url.toString(),
            ).also { throwIfRateLimited(response.code, request.url()) }
        }
    }

    override fun executeAsync(request: Request, callback: Downloader.AsyncCallback): CancellableCall {
        val call = client.newCall(buildRequest(request, includeCookie = true))
        val cancellable = CancellableCall(call)
        call.enqueue(
            object : okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cancellable.setFinished()
                    callback.onError(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    try {
                        response.use {
                            val raw = it.body.bytes()
                            throwIfRateLimited(it.code, request.url())
                            callback.onSuccess(
                                Response(
                                    it.code,
                                    it.message,
                                    it.headers.toMultimap(),
                                    raw.toString(Charsets.UTF_8),
                                    raw,
                                    it.request.url.toString(),
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        callback.onError(e)
                    } finally {
                        cancellable.setFinished()
                    }
                }
            },
        )
        return cancellable
    }

    override fun getStreaming(
        url: String,
        headers: Map<String, List<String>>?,
        localization: Localization?,
    ): StreamingResponse = streamingGet(url, headers, localization, timeoutMs = 0)

    override fun getStreaming(
        url: String,
        headers: Map<String, List<String>>?,
        localization: Localization?,
        timeoutMs: Long,
    ): StreamingResponse = streamingGet(url, headers, localization, timeoutMs)

    private fun streamingGet(
        url: String,
        headers: Map<String, List<String>>?,
        localization: Localization?,
        timeoutMs: Long,
    ): StreamingResponse {
        val builder = okhttp3.Request.Builder().url(url).get()
        applyHeaders(builder, headers, localization, includeCookie = true)
        if (timeoutMs > 0) {
            // OkHttp timeouts are client-wide; a per-call deadline is enforced by the
            // extractor's own cancellation budget. Keep the call simple.
        }
        val response = client.newCall(builder.build()).execute()
        throwIfRateLimited(response.code, url)
        val body = response.body.byteStream()
        return StreamingResponse(response.code, response.headers.toMultimap(), body)
    }

    override fun postStreaming(
        url: String,
        headers: Map<String, List<String>>?,
        dataToSend: ByteArray?,
        localization: Localization?,
    ): StreamingResponse {
        val builder = okhttp3.Request.Builder().url(url).post(
            (dataToSend ?: ByteArray(0)).toRequestBody(contentType(headers)),
        )
        applyHeaders(builder, headers, localization, includeCookie = true)
        val response = client.newCall(builder.build()).execute()
        throwIfRateLimited(response.code, url)
        return StreamingResponse(response.code, response.headers.toMultimap(), response.body.byteStream())
    }

    private fun buildRequest(request: Request, includeCookie: Boolean): okhttp3.Request {
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(
                request.httpMethod(),
                if (request.httpMethod() == "GET" || request.httpMethod() == "HEAD") {
                    null
                } else {
                    (request.dataToSend() ?: ByteArray(0))
                        .toRequestBody(contentType(request.headers()))
                },
            )
        applyHeaders(builder, request.headers(), request.localization(), includeCookie)
        return builder.build()
    }

    private fun applyHeaders(
        builder: okhttp3.Request.Builder,
        headers: Map<String, List<String>>?,
        localization: Localization?,
        includeCookie: Boolean,
    ) {
        val supplied = headers.orEmpty()
        var hasUserAgent = false
        var hasAcceptLanguage = false
        var hasCookie = false

        supplied.forEach { (name, values) ->
            if (name.equals("Content-Type", ignoreCase = true)) return@forEach
            if (name.equals("Content-Length", ignoreCase = true)) return@forEach
            if (name.equals("Host", ignoreCase = true)) return@forEach
            if (name.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
            if (name.equals("Accept-Language", ignoreCase = true)) hasAcceptLanguage = true
            if (name.equals("Cookie", ignoreCase = true)) hasCookie = true
            values.forEach { builder.addHeader(name, it) }
        }

        if (!hasUserAgent) builder.header("User-Agent", DEFAULT_USER_AGENT)
        if (!hasAcceptLanguage) {
            val value = localization?.let {
                "${it.languageCode}-${it.countryCode}, ${it.languageCode};q=0.9"
            } ?: "en-US,en;q=0.9"
            builder.header("Accept-Language", value)
        }
        if (includeCookie && !hasCookie) {
            val host = runCatching { java.net.URI(builder.build().url.toString()).host }.getOrNull()
            if (host != null && (host.endsWith("youtube.com") || host.endsWith("google.com"))) {
                cookieProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
            }
        }
    }

    private fun contentType(headers: Map<String, List<String>>?): okhttp3.MediaType? =
        headers.orEmpty().entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.toMediaTypeOrNull()

    private fun throwIfRateLimited(code: Int, url: String) {
        if (code == 429) throw ReCaptchaException("ReCaptcha Challenge requested", url)
        if (code in 500..599) throw IOException("HTTP $code for $url")
    }

    private companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        @Suppress("unused")
        val defaultLocale: Locale = Locale.US
    }
}
