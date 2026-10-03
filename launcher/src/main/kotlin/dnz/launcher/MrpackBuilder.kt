package dnz.launcher

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a Modrinth modpack (.mrpack) of DNZ Client for Modrinth App / Prism (gradlew mrpack):
 * DNZ Client itself goes into overrides/mods, every other mod (Sodium, the FPS mods...) is listed with its
 * official Modrinth download and hashes, so the app downloads it from Modrinth.
 *
 * Arguments: <DNZ version> <client jars folder> <output folder>
 */
fun main(args: Array<String>) {
    val version = args[0]
    val jars = File(args[1])
    val out = File(args[2]).apply { mkdirs() }
    for (mc in listOf("26.2", "26.3")) {
        val jar = File(jars, "dnz-client-mc$mc-$version.jar")
        check(jar.exists()) { "missing ${jar.absolutePath}" }
        val file = File(out, "DNZ-Client-$version-mc$mc.mrpack")
        // DNZ Schematic comes along when it has been built (../schematic/build/libs).
        val schematic = File(jars, "../../../schematic/build/libs").canonicalFile
            .listFiles { f -> f.name.startsWith("dnz-schematic-mc$mc-") && f.name.endsWith(".jar") && !f.name.contains("sources") }
            ?.maxByOrNull { it.lastModified() }
        MrpackBuilder.build(version, mc, jar, file, listOfNotNull(schematic))
        println("Wrote ${file.absolutePath} (${file.length() / 1024} KB)")
    }
}

object MrpackBuilder {
    /** Same mods the launcher installs (GameLauncher), from Modrinth. */
    val mods = listOf("fabric-api", "sodium", "sodium-extra", "modmenu", "lithium", "entityculling", "immediatelyfast", "ferrite-core", "dynamic-fps", "badoptimizations",
        "ixeris", "gnetum", "obe", "cull-fewer-leaves", "asynclogger", "debugify")

    fun build(version: String, mc: String, dnzJar: File, target: File, extraJars: List<File> = emptyList()) {
        val files = JsonArray()
        for (slug in mods) {
            val versions = Modrinth.versions(slug, mc)
            val chosen = versions.firstOrNull { it.type == "release" } ?: versions.firstOrNull() ?: error("$slug has no version for $mc")
            val raw = Net.json("https://api.modrinth.com/v2/version/${chosen.id}").asJsonObject
            val f = raw.getAsJsonArray("files").map { it.asJsonObject }.let { list -> list.firstOrNull { it["primary"].asBoolean } ?: list.first() }
            files.add(JsonObject().apply {
                addProperty("path", "mods/" + f["filename"].asString)
                add("hashes", f.getAsJsonObject("hashes"))
                add("env", JsonObject().apply { addProperty("client", "required"); addProperty("server", "unsupported") })
                add("downloads", JsonArray().apply { add(f["url"].asString) })
                addProperty("fileSize", f["size"].asLong)
            })
            println("  $mc: $slug ${chosen.number}")
        }
        val loader = Net.json("https://meta.fabricmc.net/v2/versions/loader/$mc").asJsonArray
            .map { it.asJsonObject["loader"].asJsonObject }.first { it["stable"].asBoolean }["version"].asString
        val index = JsonObject().apply {
            addProperty("formatVersion", 1)
            addProperty("game", "minecraft")
            addProperty("versionId", "$version+mc$mc")
            addProperty("name", "DNZ Client $version")
            addProperty("summary", "DNZ Client: modern menus, HUD, script mods and the best FPS mods (Sodium, Lithium, EntityCulling, ImmediatelyFast, FerriteCore, Dynamic FPS).")
            add("files", files)
            add("dependencies", JsonObject().apply { addProperty("minecraft", mc); addProperty("fabric-loader", loader) })
        }
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("modrinth.index.json"))
            zip.write(GsonBuilder().setPrettyPrinting().create().toJson(index).toByteArray())
            zip.closeEntry()
            for (jar in listOf(dnzJar) + extraJars) {
                zip.putNextEntry(ZipEntry("overrides/mods/${jar.name}"))
                jar.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                println("  $mc: ${jar.name} (inside the pack)")
            }
        }
    }
}
