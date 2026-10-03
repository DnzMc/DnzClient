package dnz.launcher

import java.io.File
import java.util.concurrent.TimeUnit

/** Which computer the launcher runs on (Windows, macOS on Apple Silicon or Intel, Linux) and its folders. */
object Platform {
    private val osName = System.getProperty("os.name").lowercase()
    val isWindows = osName.startsWith("windows")
    val isMac = osName.startsWith("mac") || osName.startsWith("darwin")
    val isLinux = !isWindows && !isMac
    /** Apple Silicon (M1...) or Windows/Linux on ARM. */
    val arm64 = System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")

    private val home = File(System.getProperty("user.home"))

    /** Where apps keep their data: %APPDATA% on Windows, ~/Library/Application Support on a Mac. */
    val appData: File = when {
        isWindows -> File(System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path)
        isMac -> File(home, "Library/Application Support")
        else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path)
    }

    /** The official Minecraft Launcher's folder. */
    val minecraftDir: File = when {
        isMac -> File(appData, "minecraft")
        isWindows -> File(appData, ".minecraft")
        else -> File(home, ".minecraft")
    }

    /** OS name used in Mojang's version files ("rules"). */
    val mojangOs: String = when {
        isWindows -> "windows"
        isMac -> "osx"
        else -> "linux"
    }

    /** Key of Mojang's Java runtime list for this computer. */
    val javaRuntimeKey: String = when {
        isWindows -> if (arm64) "windows-arm64" else "windows-x64"
        isMac -> if (arm64) "mac-os-arm64" else "mac-os"
        else -> "linux"
    }

    /** The Java program inside one of Mojang's runtimes (javaw on Windows: no console window). */
    fun javaIn(runtime: File): File = when {
        isWindows -> File(runtime, "bin/javaw.exe")
        isMac -> File(runtime, "jre.bundle/Contents/Home/bin/java")
        else -> File(runtime, "bin/java")
    }

    /** Runs a small command and returns what it printed, or null. */
    fun run(vararg command: String, timeoutSec: Long = 15): String? = runCatching {
        val process = ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val text = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        text.trim()
    }.getOrNull()

    /** Shows [file] selected in Finder / Explorer. */
    fun reveal(file: File) {
        runCatching {
            when {
                isWindows -> ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                isMac -> ProcessBuilder("open", "-R", file.absolutePath).start()
                else -> java.awt.Desktop.getDesktop().open(file.parentFile)
            }
        }
    }
}
