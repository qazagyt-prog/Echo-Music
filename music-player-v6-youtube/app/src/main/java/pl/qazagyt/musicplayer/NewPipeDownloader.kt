package pl.qazagyt.musicplayer

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class NewPipeDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 20000
            useCaches = false
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36")
            setRequestProperty("Accept", "*/*")
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
        val stream = if (code >= 400) connection.errorStream else connection.inputStream
        val body = stream?.use { input ->
            val out = ByteArrayOutputStream()
            input.copyTo(out)
            out.toString(Charsets.UTF_8.name())
        } ?: ""
        val headers = connection.headerFields.filterKeys { it != null }.mapKeys { it.key!! }.mapValues { it.value ?: emptyList() }
        return Response(code, connection.responseMessage ?: "", headers, body, connection.url.toString())
    }
}
