package dnz.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.URLEncoder

/**
 * CurseForge API (https://docs.curseforge.com) as a second source for the mod library.
 *
 * Results use the same shapes as [Modrinth] so the library pages work for both. CurseForge ids are written
 * "cf:<number>" (projects and files) so they never mix with Modrinth ids.
 * The API needs DNZ's key; it stays on DNZ Cloud (cloud/worker.js, "/cf/"), which asks CurseForge for the launcher,
 * so the key is never inside the launcher.
 */
object CurseForge {
    private const val API = "https://cloud.dnzclient.com/cf/v1"
    private const val MINECRAFT = 432
    private const val MODS = 6
    private const val FABRIC = 4
    private const val PACKS = 12 // texture packs
    const val PREFIX = "cf:"

    /** True once DNZ Cloud answered for CurseForge ([check]); until then the library shows Modrinth only. */
    var ready by mutableStateOf(false)
        private set

    /** Asks DNZ Cloud once (in the background at start) whether CurseForge can be reached through it. */
    fun check() {
        ready = runCatching { get("/mods/238222").has("data") }.getOrDefault(false)
    }

    fun isCf(id: String?) = id?.startsWith(PREFIX) == true
    private fun num(id: String) = id.removePrefix(PREFIX)

    private val headers = mapOf("Accept" to "application/json")
    private fun get(path: String) = Net.json(API + path, headers).asJsonObject
    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

    /** CurseForge's own sort fields for each library sort. */
    private fun sortField(sort: Modrinth.Sort) = when (sort) {
        Modrinth.Sort.Relevance -> 2 // popularity
        Modrinth.Sort.Downloads -> 6
        Modrinth.Sort.Follows -> 12 // rating
        Modrinth.Sort.Newest -> 11
        Modrinth.Sort.Updated -> 3
    }

    /** False once CurseForge refused the search for this key; the library then shows its popular lists instead. */
    @Volatile
    var searchAllowed = true
        private set

    /** [packs]: texture packs instead of mods, not filtered by Minecraft version (see Modrinth.search). */
    fun search(query: String, mc: String, sort: Modrinth.Sort, offset: Int, limit: Int = 20, packs: Boolean = false): Modrinth.SearchPage {
        if (searchAllowed) {
            val filter = if (packs) "classId=$PACKS" else "classId=$MODS&modLoaderType=$FABRIC&gameVersion=${enc(mc)}"
            val url = "/mods/search?gameId=$MINECRAFT&$filter" +
                "&searchFilter=${enc(query)}&sortField=${sortField(sort)}&sortOrder=desc&index=$offset&pageSize=$limit"
            val root = runCatching { get(url) }.getOrElse { e ->
                if (e.message?.startsWith("HTTP 403") != true) throw e
                searchAllowed = false
                null
            }
            if (root != null) {
                val hits = root.getAsJsonArray("data").map { hit(it.asJsonObject) }
                // The API never pages past 10 000 results.
                val total = root.getAsJsonObject("pagination")?.get("totalCount")?.asInt ?: hits.size
                return Modrinth.SearchPage(hits, total.coerceAtMost(10_000 - limit))
            }
        }
        return lists(query, mc, sort, packs)
    }

    /**
     * Without search: CurseForge's popular and recently updated mods that have a Fabric file for [mc], filtered by
     * the typed text. A typed project id (the number on a CurseForge mod page) opens that mod.
     */
    private fun lists(query: String, mc: String, sort: Modrinth.Sort, packs: Boolean): Modrinth.SearchPage {
        val q = query.trim()
        q.toLongOrNull()?.let { id ->
            val o = runCatching { get("/mods/$id").getAsJsonObject("data") }.getOrNull()
            return Modrinth.SearchPage(listOfNotNull(o?.let { hit(it) }), if (o == null) 0 else 1)
        }
        val body = """{"gameId":$MINECRAFT,"excludedModIds":[]}"""
        val data = Net.postJson("$API/mods/featured", body, headers).asJsonObject.getAsJsonObject("data")
        val mods = listOf("popular", "recentlyUpdated", "featured").flatMap { key -> (data[key] as? JsonArray)?.map { it.asJsonObject } ?: emptyList() }
            .filter { if (packs) it.long("classId") == PACKS.toLong() else it.long("classId") == MODS.toLong() && works(it, mc) }
            .distinctBy { it.long("id") }
        val hits = mods.map { hit(it) }.filter { q.isEmpty() || it.title.contains(q, true) || it.description.contains(q, true) }
        val sorted = when (sort) {
            Modrinth.Sort.Downloads -> hits.sortedByDescending { it.downloads }
            Modrinth.Sort.Follows -> hits.sortedByDescending { it.follows }
            else -> hits
        }
        return Modrinth.SearchPage(sorted, sorted.size)
    }

    private fun works(o: JsonObject, mc: String) = (o["latestFilesIndexes"] as? JsonArray)?.any {
        val f = it.asJsonObject
        f.str("gameVersion") == mc && f.long("modLoader") == FABRIC.toLong()
    } == true

    private fun hit(o: JsonObject) = Modrinth.Hit(
        PREFIX + o.long("id"), o.str("slug") ?: "", o.str("name") ?: "", authors(o) ?: "", o.str("summary") ?: "",
        o.long("downloadCount"), o.long("thumbsUpCount"), icon(o), emptyList(),
        pack = o.long("classId") == PACKS.toLong(),
    )

    fun project(id: String): Modrinth.Project {
        val o = get("/mods/${num(id)}").getAsJsonObject("data")
        val description = runCatching { get("/mods/${num(id)}/description")["data"].asString }.getOrDefault("")
        val links = o.getAsJsonObject("links")
        return Modrinth.Project(
            PREFIX + o.long("id"), o.str("slug") ?: "", o.str("name") ?: "", o.str("summary") ?: "", htmlToMarkdown(description),
            icon(o), o.long("downloadCount"), o.long("thumbsUpCount"), emptyList(), null,
            links?.str("sourceUrl"), links?.str("issuesUrl"), links?.str("wikiUrl"), null,
            (o["screenshots"] as? JsonArray)?.map { it.asJsonObject }?.mapNotNull { s ->
                s.str("url")?.let { Modrinth.GalleryImage(it, s.str("title"), false) }
            } ?: emptyList(),
            pageUrl = links?.str("websiteUrl") ?: "https://www.curseforge.com/minecraft/mc-mods/${o.str("slug")}",
            author = authors(o),
            pack = o.long("classId") == PACKS.toLong(),
        )
    }

    fun brief(id: String): Modrinth.ProjectBrief {
        val o = get("/mods/${num(id)}").getAsJsonObject("data")
        return Modrinth.ProjectBrief(PREFIX + o.long("id"), o.str("slug") ?: "", o.str("name") ?: "", icon(o))
    }

    /** Fabric files for [mc], newest first. */
    fun versions(id: String, mc: String, packs: Boolean = false): List<Modrinth.Version> {
        val filter = if (packs) "" else "gameVersion=${enc(mc)}&modLoaderType=$FABRIC&"
        val data = get("/mods/${num(id)}/files?${filter}pageSize=50").getAsJsonArray("data")
        return data.map { version(it.asJsonObject) }.sortedByDescending { it.date }
    }

    private fun version(o: JsonObject): Modrinth.Version {
        val sha1 = (o["hashes"] as? JsonArray)?.map { it.asJsonObject }?.firstOrNull { it.long("algo") == 1L }?.str("value")
        val file = Modrinth.VersionFile(o.str("downloadUrl") ?: "", o.str("fileName") ?: "", sha1, o.long("fileLength"), true)
        return Modrinth.Version(
            PREFIX + o.long("id"), PREFIX + o.long("modId"), o.str("displayName") ?: "", o.str("fileName")?.removeSuffix(".jar") ?: "",
            when (o.long("releaseType")) { 2L -> "beta"; 3L -> "alpha"; else -> "release" },
            o.str("fileDate") ?: "", o.long("downloadCount"), listOf(file),
            (o["dependencies"] as? JsonArray)?.map { it.asJsonObject }?.map { d ->
                Modrinth.Dependency(PREFIX + d.long("modId"), null, if (d.long("relationType") == 3L) "required" else "optional")
            } ?: emptyList(),
        )
    }

    /** Download servers CurseForge uses; anything else is refused. */
    fun trusted(url: String) = url.startsWith("https://edge.forgecdn.net/") || url.startsWith("https://mediafilez.forgecdn.net/")

    private fun authors(o: JsonObject) = (o["authors"] as? JsonArray)?.firstOrNull()?.asJsonObject?.str("name")
    private fun icon(o: JsonObject) = (o["logo"] as? JsonObject)?.let { it.str("thumbnailUrl") ?: it.str("url") }

    /** CurseForge descriptions are HTML; turn headings and list items into the Markdown the page already draws. */
    private fun htmlToMarkdown(html: String) = html
        .replace(Regex("(?i)<h1[^>]*>"), "\n# ").replace(Regex("(?i)<h2[^>]*>"), "\n## ").replace(Regex("(?i)<h[3-6][^>]*>"), "\n### ")
        .replace(Regex("(?i)<li[^>]*>"), "\n- ")
        .replace(Regex("(?i)<p[^>]*>"), "\n")
        .replace("&#39;", "'").replace("&rsquo;", "'")

    private fun JsonObject.str(k: String): String? = get(k)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
    private fun JsonObject.long(k: String): Long = get(k)?.takeUnless { it.isJsonNull }?.asLong ?: 0
}
