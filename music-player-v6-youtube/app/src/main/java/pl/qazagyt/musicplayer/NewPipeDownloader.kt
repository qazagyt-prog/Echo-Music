package pl.qazagyt.musicplayer

import org.brotli.dec.BrotliInputStream
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

class NewPipeDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 20000
            useCaches = false
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "gzip, br, deflate")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }

        request.headers().forEach { (name, values) ->
            if (values.isNotEmpty()) connection.setRequestProperty(name, values.joinToString(","))
        }

        request.dataToSend()?.let { data ->
            connection.doOutput = true
            connection.setRequestProperty("Content-Length", data.size.toString())
            connection.outputStream.use { it.write(data) }
        }

        val code = connection.responseCode
        val rawStream = if (code >= 400) connection.errorStream else connection.inputStream
        val encoding = connection.contentEncoding?.lowercase()?.trim()

        val body = rawStream?.use { input ->
            val decoded: InputStream = when {
                encoding?.contains("br") == true -> BrotliInputStream(input)
                encoding?.contains("gzip") == true -> GZIPInputStream(input)
                encoding?.contains("deflate") == true -> InflaterInputStream(input)
                else -> input
            }
            val out = ByteArrayOutputStream()
            decoded.use { it.copyTo(out) }
            out.toString(Charsets.UTF_8.name())
        } ?: ""

        val headers = connection.headerFields
            .filterKeys { it != null }
            .mapKeys { it.key!! }
            .mapValues { it.value ?: emptyList() }

        return Response(
            code,
            connection.responseMessage ?: "",
            headers,
            body,
            connection.url.toString()
        )
    }
}
