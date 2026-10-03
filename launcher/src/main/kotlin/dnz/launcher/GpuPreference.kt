package dnz.launcher

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Laptops with two graphics chips (e.g. Intel + NVIDIA): Windows may start Java on the weak built-in one, which can
 * halve the FPS. Before the game starts, the game's Java is registered as "High performance" in Windows' own graphics
 * settings (Settings > System > Display > Graphics, per user). A choice the player made there themselves is kept.
 */
object GpuPreference {
    private const val KEY = "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences"

    /** Registers javaw.exe and java.exe next to [java] (a Java's bin folder program). Never throws. */
    fun preferDedicated(java: File) {
        if (!Platform.isWindows) return
        runCatching {
            val bin = java.parentFile ?: return
            val targets = listOf("javaw.exe", "java.exe").map { File(bin, it) }.filter { it.isFile }
            if (targets.isEmpty()) return
            val existing = reg("query", KEY) ?: ""
            for (exe in targets) {
                if (hasChoice(existing, exe.absolutePath)) continue
                reg("add", KEY, "/v", exe.absolutePath, "/t", "REG_SZ", "/d", "GpuPreference=2;", "/f")
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

    /** "GpuPreference=1;" or "=2;" means the player chose; "=0;" (let Windows decide) is not a real choice. */
    private fun hasChoice(query: String, path: String): Boolean = query.lineSequence().any { line ->
        val parts = line.trim().split(Regex("\\s{2,}|\\t"))
        parts.size >= 3 && parts[0].equals(path, ignoreCase = true) && Regex("GpuPreference=([12]);").containsMatchIn(parts.last())
    }

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
