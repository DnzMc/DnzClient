package dnz.launcher

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.URLEncoder

/** Modrinth API (https://docs.modrinth.com) for the mod library. Only Fabric mods that work on the client are shown. */
object Modrinth {
    private const val API = "https://api.modrinth.com/v2"

    data class Hit(
        val id: String, val slug: String, val title: String, val author: String, val description: String,
        val downloads: Long, val follows: Long, val iconUrl: String?, val categories: List<String>,
    )

    data class SearchPage(val hits: List<Hit>, val total: Int)

    enum class Sort(val index: String, val key: String) {
        Relevance("relevance", "sort.relevance"),
        Downloads("downloads", "sort.downloads"),
        Follows("follows", "sort.follows"),
        Newest("newest", "sort.newest"),
        Updated("updated", "sort.updated"),
    }

    /** Mod categories on Modrinth (ids are the API names, labels come from Strings as "cat.<id>"). */
    val categories = listOf(
        "optimization", "utility", "decoration", "adventure", "equipment", "game-mechanics", "library", "magic",
        "management", "mobs", "social", "storage", "technology", "transportation", "worldgen", "food", "economy", "minigame",
    )

    data class GalleryImage(val url: String, val title: String?, val featured: Boolean)

    data class Project(
        val id: String, val slug: String, val title: String, val description: String, val body: String,
        val iconUrl: String?, val downloads: Long, val followers: Long, val categories: List<String>, val license: String?,
        val sourceUrl: String?, val issuesUrl: String?, val wikiUrl: String?, val discordUrl: String?,
        val gallery: List<GalleryImage>,
        val pageUrl: String = "https://modrinth.com/mod/$slug",
        /** Author, when the project itself says it (Modrinth needs a separate request). */
        val author: String? = null,
    )

    data class ProjectBrief(val id: String, val slug: String, val title: String, val iconUrl: String?)

    data class VersionFile(val url: String, val filename: String, val sha1: String?, val size: Long, val primary: Boolean)

    data class Dependency(val projectId: String?, val versionId: String?, val type: String)

    data class Version(
        val id: String, val projectId: String, val name: String, val number: String, val type: String,
        val date: String, val downloads: Long, val files: List<VersionFile>, val dependencies: List<Dependency>,
    ) {
        val primaryFile: VersionFile? get() = files.firstOrNull { it.primary } ?: files.firstOrNull()
    }

    // ------------------------------------------------------------------ requests

    fun search(query: String, mc: String, sort: Sort, category: String?, offset: Int, limit: Int = 20): SearchPage {
        val facets = buildList {
            add("[\"categories:fabric\"]")
            add("[\"versions:$mc\"]")
            add("[\"project_type:mod\"]")
            add("[\"client_side:required\",\"client_side:optional\"]")
            if (category != null) add("[\"categories:$category\"]")
        }.joinToString(",", "[", "]")
        val url = "$API/search?limit=$limit&offset=$offset&index=${sort.index}&query=${enc(query)}&facets=${enc(facets)}"
        val root = Net.json(url).asJsonObject
        val hits = root.getAsJsonArray("hits").map {
            val o = it.asJsonObject
            Hit(
                o.str("project_id")!!, o.str("slug") ?: "", o.str("title") ?: "", o.str("author") ?: "",
                o.str("description") ?: "", o.long("downloads"), o.long("follows"), o.str("icon_url").nonBlank(),
                o.strings("display_categories").ifEmpty { o.strings("categories") },
            )
        }
        return SearchPage(hits, root["total_hits"]?.asInt ?: hits.size)
    }

    fun project(idOrSlug: String): Project {
        val o = Net.json("$API/project/${enc(idOrSlug)}").asJsonObject
        val gallery = o.getAsJsonArray("gallery")?.map { it.asJsonObject }
            ?.sortedWith(compareByDescending<JsonObject> { it.bool("featured") }.thenBy { it["ordering"]?.asInt ?: 0 })
            ?.mapNotNull { g -> g.str("url")?.let { GalleryImage(it, g.str("title"), g.bool("featured")) } }
            ?: emptyList()
        return Project(
            o.str("id")!!, o.str("slug") ?: "", o.str("title") ?: "", o.str("description") ?: "", o.str("body") ?: "",
            o.str("icon_url").nonBlank(), o.long("downloads"), o.long("followers"),
            o.strings("categories") + o.strings("additional_categories"),
            o.getAsJsonObject("license")?.let { license ->
                // Custom licenses come as "LicenseRef-Polyform-Shield-1.0.0"; show "Polyform Shield 1.0.0".
                license.str("name").nonBlank() ?: license.str("id")?.removePrefix("LicenseRef-")?.replace('-', ' ')
            },
            o.str("source_url").nonBlank(), o.str("issues_url").nonBlank(), o.str("wiki_url").nonBlank(), o.str("discord_url").nonBlank(),
            gallery,
        )
    }

    /** Owner's user name, shown as the author on the mod page. */
    fun author(projectId: String): String? = runCatching {
        val members = Net.json("$API/project/${enc(projectId)}/members").asJsonArray.map { it.asJsonObject }
        val owner = members.firstOrNull { it.str("role") == "Owner" } ?: members.firstOrNull()
        owner?.getAsJsonObject("user")?.str("username")
    }.getOrNull()

    fun projects(ids: Collection<String>): List<ProjectBrief> {
        if (ids.isEmpty()) return emptyList()
        val list = ids.joinToString(",", "[", "]") { "\"$it\"" }
        return Net.json("$API/projects?ids=${enc(list)}").asJsonArray.map {
            val o = it.asJsonObject
            ProjectBrief(o.str("id")!!, o.str("slug") ?: "", o.str("title") ?: "", o.str("icon_url").nonBlank())
        }
    }

    /** Versions of a project that work with Fabric on [mc], newest first. */
    fun versions(projectId: String, mc: String): List<Version> {
        val url = "$API/project/${enc(projectId)}/version?loaders=${enc("[\"fabric\"]")}&game_versions=${enc("[\"$mc\"]")}"
        return Net.json(url).asJsonArray.map { version(it.asJsonObject) }
    }

    fun version(versionId: String): Version = version(Net.json("$API/version/${enc(versionId)}").asJsonObject)

    /** Which Modrinth version each file is (by SHA-1). Files that are not on Modrinth are missing from the map. */
    fun versionsByHash(sha1s: Collection<String>): Map<String, Version> {
        if (sha1s.isEmpty()) return emptyMap()
        val body = """{"hashes":${sha1s.toJsonArray()},"algorithm":"sha1"}"""
        return Net.postJson("$API/version_files", body).asJsonObject.entrySet().associate { (hash, v) -> hash to version(v.asJsonObject) }
    }

    /** Newest Fabric version for [mc] of each file's project (by SHA-1), used to find updates. */
    fun latestByHash(sha1s: Collection<String>, mc: String): Map<String, Version> {
        if (sha1s.isEmpty()) return emptyMap()
        val body = """{"hashes":${sha1s.toJsonArray()},"algorithm":"sha1","loaders":["fabric"],"game_versions":["$mc"]}"""
        return Net.postJson("$API/version_files/update", body).asJsonObject.entrySet().associate { (hash, v) -> hash to version(v.asJsonObject) }
    }

    fun format(n: Long): String = when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }

    // ------------------------------------------------------------------ parsing helpers

    private fun version(o: JsonObject) = Version(
        o.str("id")!!, o.str("project_id") ?: "", o.str("name") ?: "", o.str("version_number") ?: "",
        o.str("version_type") ?: "release", o.str("date_published") ?: "", o.long("downloads"),
        o.getAsJsonArray("files")?.map {
            val f = it.asJsonObject
            VersionFile(f.str("url") ?: "", f.str("filename") ?: "", f.getAsJsonObject("hashes")?.str("sha1"), f.long("size"), f.bool("primary"))
        } ?: emptyList(),
        o.getAsJsonArray("dependencies")?.map {
            val d = it.asJsonObject
            Dependency(d.str("project_id"), d.str("version_id"), d.str("dependency_type") ?: "optional")
        } ?: emptyList(),
    )

    private fun JsonObject.str(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
    private fun JsonObject.long(key: String): Long = get(key)?.takeUnless { it.isJsonNull }?.asLong ?: 0
    private fun JsonObject.bool(key: String): Boolean = get(key)?.takeUnless { it.isJsonNull }?.asBoolean ?: false
    private fun JsonObject.strings(key: String): List<String> =
        (get(key) as? JsonArray)?.mapNotNull { e: JsonElement -> e.takeUnless { it.isJsonNull }?.asString } ?: emptyList()

    private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }
    private fun Collection<String>.toJsonArray() = joinToString(",", "[", "]") { "\"$it\"" }
    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
}
