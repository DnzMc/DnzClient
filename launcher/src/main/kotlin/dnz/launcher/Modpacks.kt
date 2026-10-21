package dnz.launcher

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.zip.ZipFile

/** Modpacks from Modrinth (.mrpack): installing one makes a new profile with the pack's mods, settings and files. */
object Modpacks {
    private const val API = "https://api.modrinth.com/v2"

    private val HOSTS = setOf("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com")

    fun search(query: String, versions: List<String>, sort: Modrinth.Sort, offset: Int, limit: Int = 20): Modrinth.SearchPage {
        val facets = "[[\"project_type:modpack\"],[\"categories:fabric\"],[" + versions.joinToString(",") { "\"versions:$it\"" } + "]]"
        val url = "$API/search?limit=$limit&offset=$offset&index=${sort.index}&query=${enc(query)}&facets=${enc(facets)}"
        val root = Net.json(url).asJsonObject
        val hits = root.getAsJsonArray("hits").map {
            val o = it.asJsonObject
            Modrinth.Hit(
                o["project_id"].asString, o.str("slug") ?: "", o.str("title") ?: "", o.str("author") ?: "",
                o.str("description") ?: "", o["downloads"]?.asLong ?: 0, o["follows"]?.asLong ?: 0, o.str("icon_url")?.takeIf { it.isNotBlank() },
                emptyList(), modpack = true,
            )
        }
        return Modrinth.SearchPage(hits, root["total_hits"]?.asInt ?: hits.size)
    }

    /** What an install made: the new profile and how many files it got. */
    class Result(val profile: Profile, val files: Int, val skipped: Int)

    /**
     * Downloads the newest version of the pack that fits a supported Minecraft version (the first in
     * [versions] wins) and fills a new profile with it. [taken]: folders of the existing profiles.
     */
    fun install(projectId: String, title: String, versions: List<String>, taken: Collection<String>, progress: (String) -> Unit): Result {
        progress("…")
        val list = Net.json("$API/project/${enc(projectId)}/version?loaders=${enc("[\"fabric\"]")}&game_versions=${enc(versions.joinToString(",", "[", "]") { "\"$it\"" })}")
            .asJsonArray.map { it.asJsonObject }
        val (version, mc) = versions.firstNotNullOfOrNull { mc ->
            list.firstOrNull { v -> v.getAsJsonArray("game_versions").any { it.asString == mc } }?.let { it to mc }
        } ?: error("No version of this modpack for Minecraft ${versions.joinToString(" / ")}")
        val file = version.getAsJsonArray("files").map { it.asJsonObject }.let { files -> files.firstOrNull { it["primary"]?.asBoolean == true } ?: files.first() }
        val pack = File.createTempFile("dnz-modpack-", ".mrpack")
        try {
            Net.download(file["url"].asString, pack, file.getAsJsonObject("hashes")?.str("sha1"))
            ZipFile(pack).use { zip ->
                val index = JsonParser.parseString(zip.getInputStream(zip.getEntry("modrinth.index.json") ?: error("Not a Modrinth modpack")).reader().readText()).asJsonObject
                val deps = index.getAsJsonObject("dependencies")
                check(deps?.has("fabric-loader") == true) { "This modpack is not made for Fabric" }

                val name = title.take(32)
                val profile = Profile(name, mc, "Modpack · $title", Profiles.freeFolder(name, taken))
                val dir = GameLauncher.instanceDir(profile).canonicalFile
                dir.mkdirs()
                var done = 0
                var skipped = 0
                val files = index.getAsJsonArray("files").map { it.asJsonObject }
                for (f in files) {
                    if (f.getAsJsonObject("env")?.str("client") == "unsupported") {
                        skipped++
                        continue
                    }
                    val target = inside(dir, f["path"].asString)
                    val url = f.getAsJsonArray("downloads").map { it.asString }.firstOrNull { runCatching { URI(it).host in HOSTS && it.startsWith("https://") }.getOrDefault(false) }
                        ?: error("A file of this modpack comes from a site that is not allowed: ${f["path"].asString}")
                    progress("${++done} / ${files.size}")
                    Net.download(url, target, f.getAsJsonObject("hashes")?.str("sha1") ?: error("A file has no checksum: ${f["path"].asString}"))
                }
                // Settings and other files that come inside the pack (client ones win over the shared ones)
                for (prefix in listOf("overrides/", "client-overrides/")) {
                    for (entry in zip.entries()) {
                        if (entry.isDirectory || !entry.name.startsWith(prefix)) continue
                        val target = inside(dir, entry.name.removePrefix(prefix))
                        target.parentFile.mkdirs()
                        zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                    }
                }
                return Result(profile, done, skipped)
            }
        } finally {
            pack.delete()
        }
    }

    private fun inside(dir: File, path: String): File {
        val clean = path.replace('\\', '/')
        require(clean.isNotBlank() && !clean.startsWith("/") && !clean.contains(':') && clean.split('/').none { it == ".." }) { "Unsafe path in modpack: $path" }
        val target = File(dir, clean).canonicalFile
        require(target.path.startsWith(dir.path + File.separator)) { "Unsafe path in modpack: $path" }
        return target
    }

    private fun JsonObject.str(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
}
