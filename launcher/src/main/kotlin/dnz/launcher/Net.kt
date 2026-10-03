package dnz.launcher

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

/** Small HTTP helpers: JSON fetch and verified file download. */
object Net {
    private const val USER_AGENT = "DNZLauncher/0.2 (github.com/dnz-client)"
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun json(url: String, headers: Map<String, String> = emptyMap()): JsonElement {
        val builder = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(30))
        headers.forEach { (k, v) -> builder.header(k, v) }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()}: $url" }
        return JsonParser.parseString(response.body())
    }

    /** POSTs a JSON body and parses the JSON answer. */
    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): JsonElement {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", USER_AGENT).header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofString(body))
        headers.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()}: $url" }
        return JsonParser.parseString(response.body())
    }

    /** Small downloads kept in memory (icons, gallery images). */
    fun bytes(url: String): ByteArray {
        val request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(30)).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()}: $url" }
        return response.body()
    }

    /** Downloads [url] to [target] unless it already exists with the right SHA-1. Retries a few times. */
    fun download(url: String, target: File, sha1: String? = null) {
        if (target.exists() && (sha1 == null || sha1(target).equals(sha1, ignoreCase = true))) return
        target.parentFile.mkdirs()
        var lastError: Exception? = null
        repeat(3) {
            try {
                val temp = File(target.path + ".part")
                val request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT).timeout(Duration.ofMinutes(5)).build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofFile(temp.toPath()))
                check(response.statusCode() == 200) { "HTTP ${response.statusCode()}: $url" }
                if (sha1 != null) check(sha1(temp).equals(sha1, ignoreCase = true)) { "Bozuk dosya: ${target.name}" }
                target.delete()
                check(temp.renameTo(target)) { "Kaydedilemedi: ${target.name}" }
                return
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError!!
    }

    fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
