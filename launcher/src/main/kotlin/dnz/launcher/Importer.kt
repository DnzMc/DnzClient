package dnz.launcher

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipFile

/**
 * Brings game settings, mods, servers and packs over from other Minecraft installs on this computer
 * (other launchers, mod platforms, the official launcher) into a DNZ profile.
 *
 * Nothing is hard-coded per launcher: the user's folders are searched for game folders (a folder with an
 * options.txt) and for folders full of Fabric mods. Mods are not copied as files but looked up on Modrinth
 * by their hash and installed again in the version that fits the DNZ profile. Nothing in the source is changed.
 */
object Importer {
    data class Source(
        val dir: File,
        /** Game folder (options.txt, servers.dat, packs, config). Null for a plain folder of mods. */
        val gameDir: File?,
        val modsDir: File?,
        val modCount: Int,
        val hasOptions: Boolean,
        val serverCount: Int,
        val packCount: Int,
        /** Minecraft version, when the folder's own files say it. */
        val mcVersion: String?,
        val lastUsed: Long,
    )

    data class Parts(
        val options: Boolean = true,
        val mods: Boolean = true,
        val servers: Boolean = true,
        val packs: Boolean = true,
        val configs: Boolean = true,
    )

    data class Result(
        val options: Boolean,
        val serversAdded: Int,
        val packsCopied: Int,
        val configsCopied: Int,
        val modsInstalled: List<String>,
        val modsAlready: Int,
        /** On Modrinth, but not for the profile's Minecraft version. */
        val modsNoVersion: List<String>,
        /** Not on Modrinth, so they can't be matched to the profile's version. */
        val modsUnknown: List<String>,
        val errors: List<String>,
    )

    // ------------------------------------------------------------------ finding installs

    /** Folders never searched. AppData is searched on its own (Roaming only); Local is huge and holds no installs. */
    private val skipNames = setOf(
        "appdata", ".dnzlauncher", ".fabric", ".quilt", "node_modules", ".git", ".gradle", ".m2", ".cache", "cache", "caches", "build", "venv", "site-packages",
        "assets", "libraries", "versions", "saves", "logs", "crash-reports", "screenshots", "natives", "runtime",
        "windows", "program files", "program files (x86)", "\$recycle.bin", "temp", "tmp",
        "microsoft", "packages", "google", "mozilla", "discord", "spotify", "steam", "steamapps",
        // macOS: ~/Library is searched on its own (Application Support only).
        "library", "applications", "movies", "music", "pictures", ".trash",
    )

    /** Searches the user's folders. Takes a few seconds at most (it stops after [maxFolders] folders). */
    fun find(maxFolders: Int = 60_000): List<Source> {
        val home = File(System.getProperty("user.home"))
        val appData: File? = Platform.appData.takeIf { it.isDirectory }
        val roots = buildList {
            appData?.let { add(it to 5) }
            add(home to 6)
            // Game folders the official launcher's profiles point to (they can be anywhere, e.g. another drive).
            profileGameDirs(File(Platform.minecraftDir, "launcher_profiles.json")).forEach { add(it to 1) }
        }
        val own = canonical(GameLauncher.root)
        val seen = HashSet<String>()
        val found = LinkedHashMap<String, Source>()
        var budget = maxFolders

        fun walk(dir: File, depth: Int) {
            if (budget-- <= 0) return
            val path = canonical(dir)
            if (!seen.add(path) || path == own || path.startsWith(own + File.separator) || isLink(dir)) return
            val children = dir.listFiles() ?: return
            val source = inspect(dir, children)
            if (source != null) {
                found[path] = source
                // Its "mods" folder belongs to it; worlds, packs etc. hold no other installs.
                if (source.gameDir != null) return
            }
            if (depth <= 0) return
            for (child in children) {
                if (child.isDirectory && child.name.lowercase() !in skipNames) walk(child, depth - 1)
            }
        }
        for ((root, depth) in roots) walk(root, depth)

        // A mods folder that sits inside a found game folder is already covered by it.
        val gameDirs = found.values.mapNotNull { it.gameDir?.let(::canonical) }
        return found.values.filter { s ->
            s.gameDir != null || gameDirs.none { canonical(s.dir).startsWith(it + File.separator) }
        }.sortedByDescending { it.lastUsed }
    }

    private fun inspect(dir: File, children: Array<File>): Source? {
        val options = children.firstOrNull { it.isFile && it.name == "options.txt" }
        if (options != null) {
            // Test runs of mods being developed (a build.gradle next to them) are not real installs.
            val parent = dir.parentFile
            if (parent != null && (File(parent, "build.gradle").exists() || File(parent, "build.gradle.kts").exists())) return null
            val mods = File(dir, "mods").takeIf { it.isDirectory }
            val packs = listOf("resourcepacks", "shaderpacks").sumOf { packFiles(File(dir, it)).size }
            return Source(
                dir, dir, mods, mods?.let { modJars(it).size } ?: 0, true,
                runCatching { File(dir, "servers.dat").takeIf { it.isFile }?.let { servers(Nbt.read(it)).size } ?: 0 }.getOrDefault(0),
                packs, mcVersion(dir, children), options.lastModified(),
            )
        }
        // A folder of Fabric mods without a game folder around it (some launchers keep mods apart).
        val jars = modJars(dir)
        if (jars.isEmpty() || jars.all { it.name.startsWith("dnz-client") || it.name.startsWith("dnz-schematic") }) return null
        if (jars.take(3).none(::isFabricMod)) return null
        return Source(dir, null, dir, jars.size, false, 0, 0, mcVersion(dir, children), jars.maxOf { it.lastModified() })
    }

    private fun profileGameDirs(file: File): List<File> = runCatching {
        val profiles = com.google.gson.JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("profiles")
        profiles.entrySet().mapNotNull { (_, p) -> p.asJsonObject["gameDir"]?.takeIf { it.isJsonPrimitive }?.asString }
            .map(::File).filter { it.isDirectory }
    }.getOrDefault(emptyList())

    private fun modJars(dir: File): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }?.toList() ?: emptyList()

    private fun packFiles(dir: File): List<File> =
        dir.listFiles { f -> (f.isFile && f.name.endsWith(".zip")) || (f.isDirectory && File(f, "pack.mcmeta").exists()) }?.toList() ?: emptyList()

    private fun isFabricMod(jar: File): Boolean = runCatching { ZipFile(jar).use { it.getEntry("fabric.mod.json") != null } }.getOrDefault(false)

    private val versionPattern = Regex("\"(?:gameVersion|game_version|minecraftVersion|mcVersion|inheritsFrom)\"\\s*:\\s*\"([0-9][0-9.]*[0-9])\"")

    /** The Minecraft version, if a small json file in the folder (or its name) tells it. */
    private fun mcVersion(dir: File, children: Array<File>): String? {
        for (file in children) {
            if (!file.isFile || !file.name.endsWith(".json") || file.length() > 256_000) continue
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            versionPattern.find(text)?.let { return it.groupValues[1] }
        }
        // From the folder name: "mc26.2" first, then any Minecraft-looking number ("1.21.4", "26.3").
        val number = "(1\\.[0-9]{1,2}(?:\\.[0-9]{1,2})?|2[0-9]\\.[0-9]{1,2}(?:\\.[0-9]{1,2})?)(?![0-9])"
        return Regex("(?i)mc[-_ ]?$number").find(dir.name)?.groupValues?.get(1)
            ?: Regex("(?<![0-9.])$number").find(dir.name)?.groupValues?.get(1)
    }

    private fun isLink(dir: File): Boolean = runCatching {
        val attrs = Files.readAttributes(dir.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        attrs.isSymbolicLink || attrs.isOther // "isOther" = Windows junctions like "My Documents"
    }.getOrDefault(true)

    private fun canonical(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath).lowercase().trimEnd('\\', '/')

    // ------------------------------------------------------------------ importing

    private const val OPTIONS_BACKUP = "options.txt.dnz-before-import"
    private const val SERVERS_BACKUP = "servers.dat.dnz-before-import"

    /** True when the profile's game is open (it would overwrite options.txt and servers.dat when it closes). */
    fun isRunning(profile: Profile): Boolean {
        val dir = canonical(GameLauncher.instanceDir(profile))
        return PcScan.scan().runningGameDirs.any { canonical(File(it)) == dir }
    }

    /** Copies the chosen [parts] of [source] into [profile]. Nothing that is already in the profile is deleted. */
    suspend fun import(source: Source, profile: Profile, parts: Parts, progress: (String) -> Unit): Result {
        val target = GameLauncher.instanceDir(profile).apply { mkdirs() }
        val errors = mutableListOf<String>()
        fun step(name: String, block: () -> Unit) = runCatching(block).onFailure { errors += "$name: ${it.message ?: it}" }

        var options = false
        var servers = 0
        var packs = 0
        var configs = 0
        val game = source.gameDir
        if (game != null) {
            if (parts.options) step("options.txt") {
                progress(Strings.current("import.step.options"))
                val file = File(target, "options.txt")
                val backup = File(target, OPTIONS_BACKUP)
                if (file.exists() && !backup.exists()) file.copyTo(backup)
                File(game, "options.txt").copyTo(file, overwrite = true)
                options = true
            }
            if (parts.servers) step("servers.dat") {
                progress(Strings.current("import.step.servers"))
                servers = mergeServers(File(game, "servers.dat"), target)
            }
            if (parts.packs) step("packs") {
                progress(Strings.current("import.step.packs"))
                for (folder in listOf("resourcepacks", "shaderpacks")) {
                    for (pack in packFiles(File(game, folder))) {
                        val to = File(target, "$folder/${pack.name}")
                        if (to.exists()) continue
                        pack.copyRecursively(to)
                        packs++
                    }
                }
            }
            if (parts.configs) step("config") {
                progress(Strings.current("import.step.configs"))
                // Mod settings: only files the profile doesn't have yet, so DNZ's own settings stay.
                val from = File(game, "config")
                from.walkTopDown().filter { it.isFile && it.length() < 4_000_000 }.forEach { file ->
                    val to = File(target, "config/" + file.relativeTo(from).path)
                    if (!to.exists()) {
                        file.copyTo(to)
                        configs++
                    }
                }
            }
        }

        val installed = mutableListOf<String>()
        val noVersion = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        var already = 0
        val modsDir = source.modsDir
        if (parts.mods && modsDir != null) try {
            progress(Strings.current("import.step.mods_check"))
            val jars = modJars(modsDir).filter { !it.name.startsWith("dnz-client") && !it.name.startsWith("dnz-schematic") }
            val hashes = jars.associateWith { Net.sha1(it) }
            val versions = Modrinth.versionsByHash(hashes.values)
            val projectIds = hashes.values.mapNotNull { versions[it]?.projectId }.toSet()
            val projects = Modrinth.projects(projectIds).associateBy { it.id }
            val have = Library.scan(profile, checkUpdates = false).mapNotNull { it.projectId }.toMutableSet()
            // Mods DNZ installs itself on PLAY are left to it (two copies of one mod would crash the game).
            val bundled = GameLauncher.modrinthMods.toSet()

            for (jar in jars) {
                val version = versions[hashes.getValue(jar)]
                if (version == null) {
                    unknown += jar.name
                    continue
                }
                val project = projects[version.projectId]
                val title = project?.title ?: jar.name
                if (version.projectId in have || project?.slug in bundled) {
                    already++
                    continue
                }
                progress(Strings.current("lib.downloading").replace("%s", title))
                val noVersionText = Strings.current("lib.no_version").replace("%s", profile.version)
                runCatching { Library.install(profile, version.projectId) }
                    .onSuccess {
                        installed += title
                        // Its required mods came along too; don't install them a second time.
                        have += Library.scan(profile, checkUpdates = false).mapNotNull { it.projectId }
                    }
                    .onFailure { if (it.message == noVersionText) noVersion += title else errors += "$title: ${it.message ?: it}" }
            }
        } catch (e: Exception) {
            errors += "mods: ${e.message ?: e}"
        }
        return Result(options, servers, packs, configs, installed, already, noVersion, unknown, errors)
    }

    /** Adds the servers of [from] to the profile's server list; servers that are already there (same address) are kept once. */
    private fun mergeServers(from: File, target: File): Int {
        if (!from.isFile) return 0
        val incoming = servers(Nbt.read(from))
        val file = File(target, "servers.dat")
        if (!file.exists()) {
            from.copyTo(file)
            return incoming.size
        }
        val root = Nbt.read(file)
        val list = root["servers"] as? Nbt.TagList ?: Nbt.TagList(Nbt.COMPOUND, mutableListOf()).also { root["servers"] = it }
        val known = servers(root).map { (it["ip"] as? String)?.lowercase() }.toSet()
        val added = incoming.filter { (it["ip"] as? String)?.lowercase() !in known }
        if (added.isEmpty()) return 0
        val backup = File(target, SERVERS_BACKUP)
        if (!backup.exists()) file.copyTo(backup)
        list.items.addAll(added)
        Nbt.write(file, root)
        return added.size
    }

    @Suppress("UNCHECKED_CAST")
    private fun servers(root: MutableMap<String, Any>): List<MutableMap<String, Any>> =
        (root["servers"] as? Nbt.TagList)?.items?.filterIsInstance<MutableMap<String, Any>>() ?: emptyList()
}

/** Minimal reader/writer for Minecraft's uncompressed NBT files (servers.dat). */
object Nbt {
    const val COMPOUND: Byte = 10

    class TagList(val type: Byte, val items: MutableList<Any>)

    fun read(file: File): MutableMap<String, Any> = DataInputStream(file.inputStream().buffered()).use { input ->
        check(input.readByte() == COMPOUND) { "not an NBT file" }
        input.readUTF()
        @Suppress("UNCHECKED_CAST")
        readTag(input, COMPOUND) as MutableMap<String, Any>
    }

    fun write(file: File, root: Map<String, Any>) {
        val temp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(temp.outputStream().buffered()).use { out ->
            out.writeByte(COMPOUND.toInt())
            out.writeUTF("")
            writeTag(out, COMPOUND, root)
        }
        file.delete()
        check(temp.renameTo(file)) { "${file.name} could not be written" }
    }

    private fun readTag(input: DataInputStream, type: Byte): Any = when (type.toInt()) {
        1 -> input.readByte()
        2 -> input.readShort()
        3 -> input.readInt()
        4 -> input.readLong()
        5 -> input.readFloat()
        6 -> input.readDouble()
        7 -> ByteArray(input.readInt()).also { input.readFully(it) }
        8 -> input.readUTF()
        9 -> {
            val itemType = input.readByte()
            val size = input.readInt()
            TagList(itemType, MutableList(size) { readTag(input, itemType) })
        }
        10 -> {
            val map = LinkedHashMap<String, Any>()
            while (true) {
                val childType = input.readByte()
                if (childType.toInt() == 0) break
                val name = input.readUTF()
                map[name] = readTag(input, childType)
            }
            map
        }
        11 -> IntArray(input.readInt()) { input.readInt() }
        12 -> LongArray(input.readInt()) { input.readLong() }
        else -> error("unknown NBT tag $type")
    }

    private fun typeOf(value: Any): Byte = when (value) {
        is Byte -> 1
        is Short -> 2
        is Int -> 3
        is Long -> 4
        is Float -> 5
        is Double -> 6
        is ByteArray -> 7
        is String -> 8
        is TagList -> 9
        is Map<*, *> -> 10
        is IntArray -> 11
        is LongArray -> 12
        else -> error("unsupported NBT value ${value::class}")
    }

    private fun writeTag(out: DataOutputStream, type: Byte, value: Any) {
        when (type.toInt()) {
            1 -> out.writeByte((value as Byte).toInt())
            2 -> out.writeShort((value as Short).toInt())
            3 -> out.writeInt(value as Int)
            4 -> out.writeLong(value as Long)
            5 -> out.writeFloat(value as Float)
            6 -> out.writeDouble(value as Double)
            7 -> (value as ByteArray).let { out.writeInt(it.size); out.write(it) }
            8 -> out.writeUTF(value as String)
            9 -> (value as TagList).let { list ->
                out.writeByte(list.type.toInt())
                out.writeInt(list.items.size)
                list.items.forEach { writeTag(out, list.type, it) }
            }
            10 -> {
                for ((name, child) in value as Map<*, *>) {
                    val childType = typeOf(child!!)
                    out.writeByte(childType.toInt())
                    out.writeUTF(name as String)
                    writeTag(out, childType, child)
                }
                out.writeByte(0)
            }
            11 -> (value as IntArray).let { a -> out.writeInt(a.size); a.forEach(out::writeInt) }
            12 -> (value as LongArray).let { a -> out.writeInt(a.size); a.forEach(out::writeLong) }
        }
    }
}
