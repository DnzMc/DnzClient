package dnz.launcher

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Laptops with two graphics chips (e.g. Intel + NVIDIA): Windows may start Java on the weak built-in one,
 * which can halve the FPS. Before the game starts, the game's Java is registered as "High performance" in
 * Windows' own graphics settings (Settings > System > Display > Graphics, per user).
 */
object GpuPreference {
    private const val KEY = "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences"

    fun preferDedicated(java: File) {
        if (!Platform.isWindows) return
        runCatching {
            val bin = java.parentFile ?: return
            val targets = listOf("javaw.exe", "java.exe").map { File(bin, it) }.filter { it.isFile }
            if (targets.isEmpty()) return
            val existing = reg("query", KEY) ?: ""
            for (exe in targets) {
                val value = valueOf(existing, exe.absolutePath)
                if (Regex("GpuPreference=([12]);").containsMatchIn(value)) continue
                // Windows keeps other settings in the same text (HDR, ...): they stay, only the choice is added
                reg("add", KEY, "/v", exe.absolutePath, "/t", "REG_SZ", "/d", value.replace(Regex("GpuPreference=\\d+;"), "") + "GpuPreference=2;", "/f")
            }
        }
    }

    /** Java programs the official Minecraft Launcher uses (classic and Microsoft Store), for the hand-off play mode. */
    fun officialRuntimes(): List<File> {
        if (!Platform.isWindows) return emptyList()
        val roots = listOfNotNull(
            File(Platform.minecraftDir, "runtime"),
            System.getenv("LOCALAPPDATA")?.let { File(it, "Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime") },
        ).filter { it.isDirectory }
        return roots.flatMap { root ->
            root.walkTopDown().maxDepth(6).filter { it.isFile && it.name.equals("javaw.exe", ignoreCase = true) }.toList()
        }
    }

    /** The registered settings of [path] ("AppStatus=1;AutoHDREnable=2097;"), or "" when it has none. */
    private fun valueOf(query: String, path: String): String = query.lineSequence().map { it.trim().split(Regex("\\s{2,}|\\t")) }
        .firstOrNull { it.size >= 3 && it[0].equals(path, ignoreCase = true) }?.last() ?: ""

    private fun reg(vararg args: String): String? {
        val exe = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/reg.exe").path
        val process = ProcessBuilder(listOf(exe) + args).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        return out
    }
}
