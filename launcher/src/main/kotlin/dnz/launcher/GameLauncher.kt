package dnz.launcher

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Who is playing. */
data class Account(val name: String, val uuid: String, val accessToken: String, val offline: Boolean) {
    companion object {
        fun offline(name: String) = Account(
            name,
            UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray()).toString().replace("-", ""),
            "0",
            true,
        )
    }
}

/** How a Minecraft version is started. */
enum class Loader(val label: String) { Fabric("Fabric"), Forge("Forge"), Vanilla("Vanilla") }

/**
 * The Minecraft versions DNZ Launcher offers and how each can start: Fabric (1.14 and newer), Forge (1.8.9
 * and 1.12.2, whose Forge installers are simple enough to read without running them) and always plain
 * Minecraft (Vanilla).
 */
object Versions {
    val all = listOf("26.3", "26.2", "1.21.11", "1.20.1", "1.16.5", "1.12.2", "1.8.9")

    private val fabric = setOf("26.3", "26.2", "1.21.11", "1.20.1", "1.16.5")

    /** Forge build per version (from Forge's own maven; installers in the old, plain format). */
    val forge = mapOf("1.8.9" to "1.8.9-11.15.1.2318-1.8.9", "1.12.2" to "1.12.2-14.23.5.2847")

    /** The ways [mc] can start, the default first. */
    fun loaders(mc: String): List<Loader> = listOfNotNull(
        Loader.Fabric.takeIf { mc in fabric },
        Loader.Forge.takeIf { mc in forge },
        Loader.Vanilla,
    ).let { list -> if (mc == "1.8.9" || mc == "1.12.2") list.sortedBy { if (it == Loader.Forge) 0 else 1 } else list }

    fun loader(mc: String) = loaders(mc).first()

    /** How this profile starts: its own choice if the version supports it, else the version's default. */
    fun loaderOf(profile: Profile): Loader {
        val chosen = profile.loader?.let { name -> Loader.values().firstOrNull { it.name == name } }
        return chosen?.takeIf { it in loaders(profile.version) } ?: loader(profile.version)
    }

    fun hasDnzClient(mc: String, loader: Loader) =
        (mc.startsWith("26.") && loader == Loader.Fabric) || (mc == "1.8.9" && loader == Loader.Forge)
}

/** Installs Minecraft + Fabric + DNZ mods into %APPDATA%\.dnzlauncher and starts the game. */
object GameLauncher {
    // User home instead of %APPDATA%: packaged Windows apps can have AppData redirected, which confuses Fabric's
    // classpath checks. Paths are also made canonical below for the same reason.
    val root: File = File(System.getProperty("user.home"), ".dnzlauncher").apply { mkdirs() }.canonicalFile
    private val libraries = File(root, "libraries")
    private val assets = File(root, "assets")
    private val versions = File(root, "versions")
    private val runtimes = File(root, "runtime")

    val modrinthMods = listOf(
        "fabric-api", "sodium", "sodium-extra", "modmenu",
        "lithium", "entityculling", "immediatelyfast", "ferrite-core", "dynamic-fps", "badoptimizations",
        // 01.10.2026, measured in the FPS benchmark: stutters halved (1% low 49 -> 104 FPS), block-entity scenes +30%.
        // Input on its own thread, HUD spread over frames, faster chests/signs, leaf culling, async log, bug fixes.
        "ixeris", "gnetum", "obe", "cull-fewer-leaves", "asynclogger", "debugify",
        // 03.10.2026, measured against a pack with all extra mods: no FPS difference, so only the stable ones that help
        // where the benchmark cannot look: hidden-block culling, multiplayer networking, the single player server.
        "sciophobia", "vmp-fabric", "servercore",
        // ModernFix (26.x port "ModernFix-mVUS" on Modrinth): faster start, less RAM.
        "modernfix-mvus",
        // 17.10.2026: faster light updates (ScalableLux), faster world generation in single player (Fast Noise + its
        // config library), and a crash screen that returns to the menu instead of
        // closing the game (Not Enough Crashes).
        "scalablelux", "zconfig", "zfastnoise", "notenoughcrashes",
        // Extras: proximity voice chat on servers that have it, items that lie flat and roll on the ground (with its
        // library CreativeCore), the skin's outer layer in 3D.
        "simple-voice-chat", "creativecore", "itemphysic-lite", "3dskinlayers",
    )

    /**
     * Java settings for smooth PvP: the whole RAM is reserved at start (no resizing mid-game) and the garbage
     * collector aims for short pauses, which removes most micro-stutters.
     */
    fun jvmArgs(ramGb: Int): List<String> = listOf(
        "-Xmx${ramGb}G", "-Xms${ramGb}G",
        "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=40", "-XX:+UnlockExperimentalVMOptions",
        "-XX:G1NewSizePercent=20", "-XX:G1ReservePercent=20", "-XX:G1HeapRegionSize=16M",
        "-XX:+ParallelRefProcEnabled", "-XX:+DisableExplicitGC", "-XX:+PerfDisableSharedMem",
    ) + if (Platform.isMac) listOf(
        // Java 25: smaller objects in memory (about 10-20% less RAM), which matters most on 8 GB Macs
        "-XX:+UseCompactObjectHeaders",
        // more room for the game code Java compiles, so it never has to fall back to the slow path after a while
        "-XX:ReservedCodeCacheSize=256M",
    ) else emptyList()

    /** Bundled mods that can't be turned off (the client needs them). */
    private val lockedMods = setOf("fabric-api")

    fun instanceDir(profile: Profile) = File(root, "instances/" + profile.folder)

    /** Files in [mods] that DNZ installs itself: file name (without ".disabled") -> locked. */
    fun bundledFiles(mods: File): Map<String, Boolean> {
        val result = HashMap<String, Boolean>()
        for (slug in modrinthMods) {
            val marker = File(mods, ".dnz-$slug")
            if (marker.exists()) result[marker.readText().trim()] = slug in lockedMods
        }
        mods.listFiles { f -> f.name.startsWith("dnz-client") }?.forEach { result[it.name.removeSuffix(".disabled")] = true }
        mods.listFiles { f -> f.name.startsWith("dnz-schematic") }?.forEach { result[it.name.removeSuffix(".disabled")] = false }
        return result
    }

    /** This profile's game is already open (maybe started before the launcher was reopened). */
    class AlreadyRunning : Exception("already running")

    private fun runningFile(instance: File) = File(instance, "dnz-running.pid")

    /** True while a game started from this profile folder is still open. */
    fun isRunning(profile: Profile): Boolean {
        val pid = runCatching { runningFile(instanceDir(profile)).readText().trim().toLong() }.getOrNull() ?: return false
        return ProcessHandle.of(pid).map { p -> p.isAlive && p.info().command().map { it.contains("java", ignoreCase = true) }.orElse(true) }.orElse(false)
    }

    suspend fun play(profile: Profile, account: Account, ramGb: Int, progress: (String, Float) -> Unit): Process = withContext(Dispatchers.IO) {
        // two games in one folder break each other (Fabric's mod files); the second one is not started
        if (isRunning(profile)) throw AlreadyRunning()
        val mc = profile.version

        progress("Minecraft $mc bilgileri alınıyor...", 0.02f)
        val manifest = Net.json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").asJsonObject
        val entry = manifest.getAsJsonArray("versions").map { it.asJsonObject }.first { it["id"].asString == mc }
        val vanillaFile = File(versions, "$mc/$mc.json")
        Net.download(entry["url"].asString, vanillaFile, entry["sha1"].asString)
        val vanilla = com.google.gson.JsonParser.parseString(vanillaFile.readText()).asJsonObject

        // the mod loader's own version file: Fabric (1.14+), Forge (1.8.9) or none (plain Minecraft)
        val kind = Versions.loaderOf(profile)
        val loaderProfile: JsonObject? = when (kind) {
            Loader.Fabric -> {
                progress("Fabric hazırlanıyor...", 0.05f)
                val loader = Net.json("https://meta.fabricmc.net/v2/versions/loader/$mc").asJsonArray
                    .map { it.asJsonObject["loader"].asJsonObject }.first { it["stable"].asBoolean }["version"].asString
                Net.json("https://meta.fabricmc.net/v2/versions/loader/$mc/$loader/profile/json").asJsonObject
            }
            Loader.Forge -> {
                progress("Forge hazırlanıyor...", 0.05f)
                forgeProfile(mc)
            }
            Loader.Vanilla -> null
        }

        // Client jar
        val clientJar = File(versions, "$mc/$mc.jar")
        val clientDl = vanilla["downloads"].asJsonObject["client"].asJsonObject
        progress("Minecraft indiriliyor...", 0.08f)
        Net.download(clientDl["url"].asString, clientJar, clientDl["sha1"].asString)

        // Libraries (the loader's first so its versions win over vanilla duplicates)
        val jobs = mutableListOf<Triple<String, File, String?>>()
        val nativeJars = mutableListOf<File>()
        val seen = HashSet<String>()
        val classpath = mutableListOf<File>()
        for (lib in loaderProfile?.getAsJsonArray("libraries")?.map { it.asJsonObject }.orEmpty()) {
            if (lib["clientreq"]?.asBoolean == false) continue // Forge: server-only
            val name = lib["name"].asString
            val path = mavenPath(name)
            seen += name.substringBeforeLast(':')
            val file = File(libraries, path)
            classpath += file
            if (file.exists()) continue // Forge's own jar comes out of its installer (forgeProfile)
            val base = lib["url"]?.asString?.replace("http://files.minecraftforge.net/maven", "https://maven.minecraftforge.net")
                ?: if (kind == Loader.Forge) "https://libraries.minecraft.net/" else "https://maven.fabricmc.net/"
            jobs += Triple(base.trimEnd('/') + "/" + path, file, lib["sha1"]?.asString)
        }
        for (lib in vanilla.getAsJsonArray("libraries").map { it.asJsonObject }) {
            if (!rulesAllow(lib["rules"])) continue
            val downloads = lib["downloads"]?.asJsonObject
            // old versions (before 1.19): native libraries (LWJGL...) come as a separate jar that is unpacked
            val classifier = lib["natives"]?.asJsonObject?.get(Platform.mojangOs)?.asString?.replace("\${arch}", "64")
            downloads?.get("classifiers")?.asJsonObject?.get(classifier ?: "")?.asJsonObject?.let { n ->
                val file = File(libraries, n["path"].asString)
                jobs += Triple(n["url"].asString, file, n["sha1"].asString)
                nativeJars += file
            }
            val artifact = downloads?.get("artifact")?.asJsonObject ?: continue
            val name = lib["name"].asString
            val key = name.split(':').let { if (it.size > 3) name else it.take(2).joinToString(":") }
            if (!seen.add(key)) continue
            val file = File(libraries, artifact["path"].asString)
            classpath += file
            jobs += Triple(artifact["url"].asString, file, artifact["sha1"].asString)
        }
        classpath += clientJar
        parallelDownloadWithMirrors(jobs, "Kütüphaneler indiriliyor", 0.10f, 0.30f, progress)

        // Assets
        val assetIndex = vanilla["assetIndex"].asJsonObject
        val indexFile = File(assets, "indexes/${assetIndex["id"].asString}.json")
        Net.download(assetIndex["url"].asString, indexFile, assetIndex["sha1"].asString)
        val objects = com.google.gson.JsonParser.parseString(indexFile.readText()).asJsonObject["objects"].asJsonObject
        val assetJobs = objects.entrySet().map { (_, value) ->
            val hash = value.asJsonObject["hash"].asString
            Triple("https://resources.download.minecraft.net/${hash.take(2)}/$hash", File(assets, "objects/${hash.take(2)}/$hash"), hash)
        }
        parallelDownload(assetJobs, "Oyun dosyaları indiriliyor", 0.30f, 0.70f, progress)

        // Java runtime Mojang recommends for this version
        val java = installJava(vanilla, progress)
        GpuPreference.preferDedicated(java)

        // Mods (Fabric profiles; Forge and plain Minecraft start without DNZ's mods)
        val instance = instanceDir(profile).apply { mkdirs() }
        if (kind == Loader.Fabric) installMods(mc, File(instance, "mods")) { text, value -> progress(text, 0.90f + value * 0.07f) }
        if (kind == Loader.Forge) installDnzClient(mc, File(instance, "mods").apply { mkdirs() }) // DNZ Client 1.8.9

        progress("Oyun başlatılıyor...", 0.98f)
        val natives = File(versions, "$mc/natives").apply { mkdirs() }
        for (jar in nativeJars) unpackNatives(jar, natives)
        val vars = mapOf(
            "auth_player_name" to account.name,
            "auth_session" to "token:${account.accessToken}:${account.uuid}",
            "game_assets" to assets.absolutePath,
            "version_name" to (loaderProfile?.get("id")?.asString ?: mc),
            "game_directory" to instance.absolutePath,
            "assets_root" to assets.absolutePath,
            "assets_index_name" to assetIndex["id"].asString,
            "auth_uuid" to account.uuid,
            "auth_access_token" to account.accessToken,
            "clientid" to "",
            "auth_xuid" to "",
            "user_type" to if (account.offline) "legacy" else "msa",
            "version_type" to "release",
            "user_properties" to "{}",
            "launcher_name" to "DNZLauncher",
            "launcher_version" to "0.2",
            "natives_directory" to natives.absolutePath,
            "library_directory" to libraries.absolutePath,
            "classpath_separator" to File.pathSeparator,
            "classpath" to classpath.joinToString(File.pathSeparator) { it.toPath().toRealPath().toString() },
        )
        val command = mutableListOf(java.absolutePath)
        // Java 25-only switches (Mac) are left out for the older Java that old versions need
        val javaMajor = vanilla["javaVersion"]?.asJsonObject?.get("majorVersion")?.asInt ?: 25
        command += jvmArgs(ramGb).filter { javaMajor >= 25 || it != "-XX:+UseCompactObjectHeaders" }
        val mainClass = loaderProfile?.get("mainClass")?.asString ?: vanilla["mainClass"].asString
        if (vanilla.has("arguments")) {
            // 1.13 and newer: argument lists in the version files
            command += arguments(vanilla["arguments"]?.asJsonObject?.get("jvm"), vars)
            command += arguments(loaderProfile?.get("arguments")?.asJsonObject?.get("jvm"), vars)
            command += mainClass
            command += arguments(vanilla["arguments"]?.asJsonObject?.get("game"), vars)
            command += arguments(loaderProfile?.get("arguments")?.asJsonObject?.get("game"), vars)
        } else {
            // 1.12.2 and older: one argument line (Forge's replaces Minecraft's), the launcher adds the Java part
            command += "-Djava.library.path=${natives.absolutePath}"
            if (kind == Loader.Forge) command += listOf("-Dfml.ignoreInvalidMinecraftCertificates=true", "-Dfml.ignorePatchDiscrepancies=true")
            command += listOf("-cp", vars.getValue("classpath"))
            command += mainClass
            val line = loaderProfile?.get("minecraftArguments")?.asString ?: vanilla["minecraftArguments"].asString
            command += line.split(' ').filter { it.isNotBlank() }.map { substitute(it, vars) }
        }

        val log = File(instance, "dnz-launcher.log")
        ProcessBuilder(command).directory(instance).redirectErrorStream(true).redirectOutput(log).apply {
            // Java settings left on the computer by other programs would sneak into the game (and can slow it down)
            environment().remove("_JAVA_OPTIONS")
            environment().remove("JAVA_TOOL_OPTIONS")
        }.start().also { runCatching { runningFile(instance).writeText(it.pid().toString()) } }
    }

    fun installMods(mc: String, mods: File, progress: (String, Float) -> Unit) {
        mods.mkdirs()
        progress("DNZ Client kuruluyor...", 0f)
        installDnzClient(mc, mods)
        // Modrinth slow or down: mods that are already here are used as they are (the update check waits for the
        // next start), so the game still starts. Only a mod that is missing has to be downloaded.
        var offline = false
        modrinthMods.forEachIndexed { i, slug ->
            progress("Modlar indiriliyor ($slug)...", (i + 1f) / (modrinthMods.size + 1))
            val marker = File(mods, ".dnz-$slug")
            val present = marker.exists() && marker.readText().trim().let { File(mods, it).exists() || File(mods, "$it.disabled").exists() }
            if (offline && present) return@forEachIndexed
            try {
                installModrinthMod(slug, mc, mods)
            } catch (e: Exception) {
                if (!present) throw e
                offline = true
            }
        }
        progress("Modlar hazır", 1f)
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Forge 1.8.9: its version file and Forge's own jar both come from the official installer (Forge's
     * maven); nothing of the installer is run.
     */
    private fun forgeProfile(mc: String): JsonObject {
        val v = Versions.forge.getValue(mc)
        val installer = File(libraries, "net/minecraftforge/forge/$v/forge-$v-installer.jar")
        Net.download("https://maven.minecraftforge.net/net/minecraftforge/forge/$v/forge-$v-installer.jar", installer)
        java.util.zip.ZipFile(installer).use { zip ->
            val profile = zip.getInputStream(zip.getEntry("install_profile.json")).use {
                com.google.gson.JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject
            }
            val install = profile["install"].asJsonObject
            val forgeJar = File(libraries, mavenPath(install["path"].asString))
            if (!forgeJar.exists()) {
                forgeJar.parentFile.mkdirs()
                zip.getInputStream(zip.getEntry(install["filePath"].asString)).use { input -> forgeJar.outputStream().use { input.copyTo(it) } }
            }
            return profile["versionInfo"].asJsonObject
        }
    }

    private suspend fun parallelDownloadWithMirrors(
        jobs: List<Triple<String, File, String?>>, label: String, from: Float, to: Float, progress: (String, Float) -> Unit,
    ) = coroutineScope {
        val done = AtomicInteger()
        val limit = Semaphore(24)
        jobs.map { (url, file, sha1) ->
            async(Dispatchers.IO) {
                limit.withPermit {
                    val path = url.substringAfter("/maven2/", "").ifEmpty { libraries.toPath().relativize(file.toPath()).toString().replace('\\', '/') }
                    val tries = listOf(url, "https://repo1.maven.org/maven2/$path", "https://libraries.minecraft.net/$path", "https://maven.minecraftforge.net/$path").distinct()
                    var error: Exception? = null
                    for (u in tries) {
                        try {
                            Net.download(u, file, sha1)
                            error = null
                            break
                        } catch (e: Exception) {
                            error = e
                        }
                    }
                    error?.let { throw it }
                }
                val n = done.incrementAndGet()
                if (n % 25 == 0 || n == jobs.size) progress("$label ($n/${jobs.size})", from + (to - from) * n / jobs.size)
            }
        }.awaitAll()
    }

    private fun unpackNatives(jar: File, into: File) {
        java.util.zip.ZipFile(jar).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory || entry.name.startsWith("META-INF")) continue
                val out = File(into, entry.name.substringAfterLast('/'))
                if (out.exists() && out.length() == entry.size) continue
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    private suspend fun parallelDownload(
        jobs: List<Triple<String, File, String?>>, label: String, from: Float, to: Float, progress: (String, Float) -> Unit,
    ) = coroutineScope {
        val done = AtomicInteger()
        val limit = Semaphore(24)
        jobs.map { (url, file, sha1) ->
            async(Dispatchers.IO) {
                limit.withPermit { Net.download(url, file, sha1) }
                val n = done.incrementAndGet()
                if (n % 25 == 0 || n == jobs.size) progress("$label ($n/${jobs.size})", from + (to - from) * n / jobs.size)
            }
        }.awaitAll()
    }

    private fun mavenPath(name: String): String {
        val parts = name.split(':')
        val (group, artifact, version) = Triple(parts[0], parts[1], parts[2])
        val classifier = if (parts.size > 3) "-" + parts[3] else ""
        return "${group.replace('.', '/')}/$artifact/$version/$artifact-$version$classifier.jar"
    }

    /** Mojang rule lists: allow/disallow by OS; feature-gated entries (demo, custom resolution...) are skipped. */
    private fun rulesAllow(rules: JsonElement?): Boolean {
        if (rules == null || !rules.isJsonArray) return true
        var allowed = false
        for (rule in rules.asJsonArray.map { it.asJsonObject }) {
            if (rule.has("features")) return false
            val os = rule["os"]?.asJsonObject
            val name = os?.get("name")?.asString
            // "x86" = 32-bit only; old files write Apple Silicon as "osx-arm64".
            val nameMatches = name == null || name == Platform.mojangOs || (name == "osx-arm64" && Platform.isMac && Platform.arm64)
            val matches = os == null || (nameMatches && os["arch"]?.asString != "x86")
            if (matches) allowed = rule["action"].asString == "allow"
        }
        return allowed
    }

    private fun arguments(list: JsonElement?, vars: Map<String, String>): List<String> {
        if (list == null || !list.isJsonArray) return emptyList()
        val out = mutableListOf<String>()
        for (arg in list.asJsonArray) {
            if (arg.isJsonPrimitive) {
                out += substitute(arg.asString, vars)
            } else {
                val o = arg.asJsonObject
                if (!rulesAllow(o["rules"])) continue
                val value = o["value"]
                if (value.isJsonArray) value.asJsonArray.forEach { out += substitute(it.asString, vars) } else out += substitute(value.asString, vars)
            }
        }
        return out
    }

    private fun substitute(s: String, vars: Map<String, String>): String =
        Regex("\\$\\{([a-z_]+)}").replace(s) { vars[it.groupValues[1]] ?: "" }

    /** The Java runtime for Minecraft [mc] (downloaded when missing), e.g. for a local server. */
    internal suspend fun javaFor(mc: String, progress: (String, Float) -> Unit): File = withContext(Dispatchers.IO) {
        val manifest = Net.json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").asJsonObject
        val entry = manifest.getAsJsonArray("versions").map { it.asJsonObject }.first { it["id"].asString == mc }
        val vanillaFile = File(versions, "$mc/$mc.json")
        Net.download(entry["url"].asString, vanillaFile, entry["sha1"].asString)
        installJava(com.google.gson.JsonParser.parseString(vanillaFile.readText()).asJsonObject, progress)
    }

    internal fun vanillaJson(mc: String): JsonObject = com.google.gson.JsonParser.parseString(File(versions, "$mc/$mc.json").readText()).asJsonObject

    /** Downloads the Java runtime Mojang lists for this version (e.g. java-runtime-epsilon = Java 25). */
    private suspend fun installJava(vanilla: JsonObject, progress: (String, Float) -> Unit): File {
        val component = vanilla["javaVersion"]?.asJsonObject?.get("component")?.asString ?: "java-runtime-epsilon"
        val dir = File(runtimes, component)
        val javaExe = Platform.javaIn(dir)
        val marker = File(dir, ".dnz-complete")
        if (marker.exists() && javaExe.exists()) return javaExe

        progress("Java indiriliyor...", 0.70f)
        val all = Net.json("https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json").asJsonObject
        // Apple Silicon always gets the Apple Silicon Java: Intel Java through Rosetta would be much slower.
        // Old Java (Java 8 for 1.8.9...) has no Apple Silicon build: the Intel one runs through Rosetta.
        val builds = all[Platform.javaRuntimeKey].asJsonObject[component]?.asJsonArray?.takeIf { it.size() > 0 }
            ?: all["mac-os"]?.asJsonObject?.get(component)?.asJsonArray?.takeIf { Platform.isMac }
            ?: error("Java $component bu bilgisayar için yok")
        val manifestUrl = builds[0].asJsonObject["manifest"].asJsonObject["url"].asString
        val files = Net.json(manifestUrl).asJsonObject["files"].asJsonObject
        val executables = mutableListOf<File>()
        val links = mutableListOf<Pair<File, String>>()
        val jobs = files.entrySet().mapNotNull { (path, info) ->
            val o = info.asJsonObject
            when (o["type"].asString) {
                "link" -> links += File(dir, path) to o["target"].asString
                "file" -> {
                    val raw = o["downloads"].asJsonObject["raw"].asJsonObject
                    if (o["executable"]?.asBoolean == true) executables += File(dir, path)
                    return@mapNotNull Triple(raw["url"].asString, File(dir, path), raw["sha1"].asString)
                }
            }
            null
        }
        parallelDownload(jobs, "Java indiriliyor", 0.70f, 0.88f, progress)
        if (!Platform.isWindows) {
            executables.forEach { it.setExecutable(true) }
            for ((link, target) in links) {
                runCatching {
                    link.parentFile.mkdirs()
                    java.nio.file.Files.deleteIfExists(link.toPath())
                    java.nio.file.Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get(target))
                }
            }
        }
        marker.writeText(component)
        return javaExe
    }

    private fun installDnzClient(mc: String, mods: File) {
        if (!Versions.hasDnzClient(mc, if (mc == "1.8.9") Loader.Forge else Loader.Fabric)) return // older versions: the FPS mods only
        mods.listFiles { f -> f.name.startsWith("dnz-client") }?.forEach { it.delete() }
        val resource = GameLauncher::class.java.getResourceAsStream("/mods/dnz-client-mc$mc.jar")
            ?: error("DNZ Client $mc bulunamadı")
        resource.use { input -> File(mods, "dnz-client-mc$mc.jar").outputStream().use { input.copyTo(it) } }

        // the schematic mod can be turned off on the Mods page; then it stays off after updates too
        val old = mods.listFiles { f -> f.name.startsWith("dnz-schematic") } ?: emptyArray()
        val off = old.isNotEmpty() && old.all { it.name.endsWith(".disabled") }
        old.forEach { it.delete() }
        GameLauncher::class.java.getResourceAsStream("/mods/dnz-schematic-mc$mc.jar")?.use { input ->
            File(mods, "dnz-schematic-mc$mc.jar" + if (off) ".disabled" else "").outputStream().use { input.copyTo(it) }
        }
    }

    internal fun installModrinthMod(slug: String, mc: String, mods: File) {
        val url = "https://api.modrinth.com/v2/project/$slug/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22$mc%22%5D"
        val versionsJson: JsonArray = Net.json(url).asJsonArray
        val version = versionsJson.map { it.asJsonObject }.firstOrNull { it["version_type"].asString == "release" }
            ?: versionsJson.firstOrNull()?.asJsonObject ?: return
        val files = version.getAsJsonArray("files").map { it.asJsonObject }
        val file = files.firstOrNull { it["primary"].asBoolean } ?: files.first()
        val name = file["filename"].asString
        // Remember which file belongs to which mod, so an update replaces exactly the old file
        val marker = File(mods, ".dnz-$slug")
        val previous = if (marker.exists()) marker.readText().trim() else null
        // A mod the player turned off (file renamed to .disabled) stays off, also after an update
        val disabled = slug !in lockedMods && previous != null && File(mods, "$previous.disabled").exists()
        if (previous != null && previous != name) {
            File(mods, previous).delete()
            File(mods, "$previous.disabled").delete()
        }
        val sha1 = file["hashes"].asJsonObject["sha1"].asString
        if (disabled) {
            val target = File(mods, "$name.disabled")
            if (!target.exists()) {
                Net.download(file["url"].asString, File(mods, name), sha1)
                File(mods, name).renameTo(target)
            }
            File(mods, name).delete()
        } else {
            Net.download(file["url"].asString, File(mods, name), sha1)
        }
        marker.writeText(name)
    }
}
