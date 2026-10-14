package dnz.launcher

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * DNZ Workshop: launcher themes made for DNZ. The list is dnzclient.com/workshop.json (the website shows the same
 * file); the player's library is kept by DNZ Cloud under their Microsoft account, so it is the same list as on
 * dnzclient.com/workshop when they sign in there with the same Microsoft account.
 */
object Workshop {
    private const val CATALOG = "https://dnzclient.com/workshop.json"
    private const val LIBRARY = "https://cloud.dnzclient.com/workshop/library"
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

    data class Item(
        val id: String,
        val type: String,
        val name: String,
        val author: String,
        val created: String,
        val tags: List<String>,
        val description: String,
        val descriptionTr: String?,
        /** The 8 launcher colors of a theme. */
        val colors: ThemeColors?,
    )

    /** Everything in the Workshop (only items with a known type and complete colors). */
    fun catalog(): List<Item> = Net.json(CATALOG).asJsonObject["items"].asJsonArray.mapNotNull { e ->
        runCatching {
            val o = e.asJsonObject
            fun str(k: String) = o[k]?.takeUnless { it.isJsonNull }?.asString
            val colors = o["colors"]?.asJsonObject?.let { c ->
                ThemeColors.fromJson(JsonObject().apply { addProperty("name", str("name")); add("colors", c) }.toString())
            }
            Item(str("id")!!, str("type") ?: "theme", str("name")!!, str("author") ?: "DNZ Official", str("created") ?: "",
                o["tags"]?.asJsonArray?.map { it.asString } ?: emptyList(), str("description") ?: "", str("description_tr"), colors)
        }.getOrNull()?.takeIf { it.type == "theme" && it.colors != null }
    }

    /** Signed in with Microsoft: DNZ Cloud can tell who the player is. */
    val canSync: Boolean get() = MicrosoftAuth.hasSaved

    /**
     * The ids in the player's library. Null when they aren't signed in with Microsoft or Microsoft didn't give an
     * identity for DNZ Cloud (then they need to sign in again); throws when DNZ Cloud can't be reached.
     */
    fun library(): List<String>? {
        val token = MicrosoftAuth.idToken() ?: return null
        val res = http.send(
            HttpRequest.newBuilder(URI.create(LIBRARY)).header("Authorization", "Bearer $token").timeout(Duration.ofSeconds(20)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        if (res.statusCode() == 401) return null
        check(res.statusCode() == 200) { "DNZ Cloud: HTTP ${res.statusCode()}" }
        return JsonParser.parseString(res.body()).asJsonObject["items"]?.asJsonArray?.map { it.asString } ?: emptyList()
    }

    /** Saves the library; false when it could not be saved. */
    fun saveLibrary(ids: List<String>): Boolean = runCatching {
        val token = MicrosoftAuth.idToken() ?: return false
        val body = JsonObject().apply { add("items", com.google.gson.JsonArray().apply { ids.forEach { add(it) } }) }.toString()
        val res = http.send(
            HttpRequest.newBuilder(URI.create(LIBRARY)).header("Authorization", "Bearer $token").header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20)).PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        res.statusCode() == 200
    }.getOrDefault(false)
}
