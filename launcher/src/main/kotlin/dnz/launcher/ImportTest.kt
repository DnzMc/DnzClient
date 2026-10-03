package dnz.launcher

import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Import check without the window: lists the installs found on this computer, then imports a throwaway
 * install (settings, servers, packs, mod settings, one real mod + one unknown mod) into a throwaway profile.
 * gradlew importTest
 */
fun main() = runBlocking {
    val start = System.currentTimeMillis()
    val found = Importer.find()
    println("FOUND ${found.size} in ${System.currentTimeMillis() - start} ms")
    found.forEach { println("  ${it.dir}  game=${it.gameDir != null} mods=${it.modCount} servers=${it.serverCount} packs=${it.packCount} mc=${it.mcVersion}") }

    // Real installs of this computer into a throwaway profile (the installs themselves are only read).
    for (source in found.filter { it.gameDir != null }) {
        val tmp = Profile("DNZ_ImportReal", "26.3", "test")
        val dir = GameLauncher.instanceDir(tmp)
        try {
            fun snapshot() = source.dir.walkTopDown().filter { it.isFile }.associate { it.path to "${it.lastModified()}/${it.length()}" }
            val before = snapshot()
            println("REAL ${source.dir.name}")
            val r = Importer.import(source, tmp, Importer.Parts()) {}
            println("  installed=${r.modsInstalled} already=${r.modsAlready} noVersion=${r.modsNoVersion} unknown=${r.modsUnknown}")
            println("  servers=${r.serversAdded} packs=${r.packsCopied} configs=${r.configsCopied} errors=${r.errors}")
            val sameOptions = File(source.gameDir, "options.txt").readText() == File(dir, "options.txt").readText()
            val serverCount = runCatching { (Nbt.read(File(dir, "servers.dat"))["servers"] as Nbt.TagList).items.size }.getOrDefault(0)
            val mods = File(dir, "mods").list()?.sorted() ?: emptyList()
            val after = snapshot()
            val changed = (before.keys + after.keys).filter { before[it] != after[it] }
            if (changed.isNotEmpty()) println("  changed in source: ${changed.take(10)}")
            val untouched = changed.isEmpty()
            println("  options same=$sameOptions  servers in file=$serverCount/${source.serverCount}  source untouched=$untouched")
            println("  mod files: $mods")
            // What PLAY does next (downloads only, the game is not started): no mod may end up twice.
            GameLauncher.installMods(tmp.version, File(dir, "mods")) { _, _ -> }
            val all = Library.scan(tmp, checkUpdates = false)
            val twice = all.filter { it.projectId != null }.groupBy { it.projectId }.filter { it.value.size > 1 }
                .map { (_, list) -> list.map { it.file.name } }
            println("  after PLAY: ${all.size} mods, duplicates=$twice")
            val ok = sameOptions && serverCount == source.serverCount && untouched && r.errors.isEmpty() && twice.isEmpty()
            println(if (ok) "  REAL OK" else "  REAL FAILED")
        } finally {
            dir.deleteRecursively()
        }
    }

    val src = Profile("DNZ_ImportSrc", "26.3", "test")
    val dst = Profile("DNZ_ImportDst", "26.2", "test")
    val srcDir = GameLauncher.instanceDir(src)
    val dstDir = GameLauncher.instanceDir(dst)
    try {
        // Source: a real mod from Modrinth + a Fabric mod that is on no site.
        Library.install(src, "iris")
        ZipOutputStream(File(srcDir, "mods/unknown-test-mod.jar").outputStream()).use {
            it.putNextEntry(ZipEntry("fabric.mod.json"))
            it.write("""{"schemaVersion":1,"id":"unknowntest","version":"1"}""".toByteArray())
        }
        File(srcDir, "options.txt").writeText("fov:0.75\nmouseSensitivity:0.3\n")
        File(srcDir, "resourcepacks").mkdirs()
        File(srcDir, "resourcepacks/pack.zip").writeText("zip")
        File(srcDir, "config").mkdirs()
        File(srcDir, "config/some-mod.json").writeText("{}")
        Nbt.write(File(srcDir, "servers.dat"), servers("play.example.net" to "Example", "shared.example.net" to "Shared"))

        // Target already has one of the servers and its own options.
        dstDir.mkdirs()
        File(dstDir, "options.txt").writeText("fov:0.5\n")
        Nbt.write(File(dstDir, "servers.dat"), servers("shared.example.net" to "Shared"))

        val source = Importer.find().firstOrNull { it.dir.canonicalPath == srcDir.canonicalPath }
        // The launcher's own folder is skipped on purpose, so build the source the same way find() would.
            ?: Importer.Source(srcDir, srcDir, File(srcDir, "mods"), 2, true, 2, 1, null, 0)
        val r = Importer.import(source, dst, Importer.Parts()) { println("  .. $it") }
        println("RESULT $r")

        val options = File(dstDir, "options.txt").readText()
        val list = Nbt.read(File(dstDir, "servers.dat"))["servers"] as Nbt.TagList
        val ips = list.items.map { (it as Map<*, *>)["ip"] }
        val mods = File(dstDir, "mods").list()?.toList() ?: emptyList()
        val checks = listOf(
            "options copied" to (r.options && "mouseSensitivity:0.3" in options),
            "options backup" to File(dstDir, "options.txt.dnz-before-import").exists(),
            "servers merged" to (r.serversAdded == 1 && ips == listOf("shared.example.net", "play.example.net")),
            "pack copied" to File(dstDir, "resourcepacks/pack.zip").exists(),
            "config copied" to File(dstDir, "config/some-mod.json").exists(),
            "iris installed for 26.2" to (r.modsInstalled.any { it.contains("Iris", true) } && mods.any { it.startsWith("iris") }),
            "unknown mod reported" to ("unknown-test-mod.jar" in r.modsUnknown),
            "no errors" to r.errors.isEmpty(),
        )
        checks.forEach { (name, ok) -> println((if (ok) "OK   " else "FAIL ") + name) }
        println(if (checks.all { it.second }) "IMPORT OK" else "IMPORT FAILED")
    } finally {
        srcDir.deleteRecursively()
        dstDir.deleteRecursively()
    }
}

private fun servers(vararg entries: Pair<String, String>): MutableMap<String, Any> = linkedMapOf(
    "servers" to Nbt.TagList(Nbt.COMPOUND, entries.map { (ip, name) ->
        linkedMapOf<String, Any>("ip" to ip, "name" to name, "hidden" to 0.toByte())
    }.toMutableList()),
)
