package dnz.launcher

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * Only one DNZ Launcher runs at a time. A second start (desktop shortcut) asks the running one to show its
 * window and then quits, so opening is instant when the launcher already runs in the background.
 */
object SingleInstance {
    private const val PORT = 47813
    private const val HELLO = "dnz-launcher"

    /** Called (on the UI thread) when another start asks this launcher to show itself. */
    @Volatile
    var onShow: () -> Unit = {}

    /** True if this is the only launcher; false if another one was found and told to show its window. */
    fun claim(): Boolean {
        val server = try {
            ServerSocket(PORT, 5, InetAddress.getLoopbackAddress())
        } catch (e: IOException) {
            // Port taken: if it is a DNZ Launcher, it answers and shows itself.
            return !askRunningToShow()
        }
        thread(isDaemon = true, name = "DNZ single instance") {
            while (true) {
                runCatching {
                    server.accept().use { socket ->
                        socket.soTimeout = 2000
                        if (socket.getInputStream().bufferedReader().readLine() == "show") {
                            socket.getOutputStream().write("$HELLO\n".toByteArray())
                            SwingUtilities.invokeLater { onShow() }
                        }
                    }
                }
            }
        }
        return true
    }

    private fun askRunningToShow(): Boolean = runCatching {
        Socket(InetAddress.getLoopbackAddress(), PORT).use { socket ->
            socket.soTimeout = 2000
            socket.getOutputStream().write("show\n".toByteArray())
            socket.getInputStream().bufferedReader().readLine() == HELLO
        }
    }.getOrDefault(false)
}

/** "Start with Windows": a shortcut in the Startup folder that opens the launcher hidden (--background). */
object Autostart {
    private val link: File?
        get() = System.getenv("APPDATA")?.let { File(it, "Microsoft/Windows/Start Menu/Programs/Startup/DNZ Launcher.lnk") }

    /** macOS: a login item (LaunchAgent) that starts the app hidden when the user logs in. */
    private val agent = File(System.getProperty("user.home"), "Library/LaunchAgents/com.dnz.launcher.plist")

    fun isEnabled(): Boolean = if (Platform.isMac) agent.exists() else link?.exists() == true

    private fun setMac(enabled: Boolean): Boolean {
        if (!enabled) return !agent.exists() || agent.delete()
        val exe = System.getProperty("jpackage.app-path") ?: return false
        fun x(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return runCatching {
            agent.parentFile.mkdirs()
            agent.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>Label</key><string>com.dnz.launcher</string>
                    <key>ProgramArguments</key>
                    <array><string>${x(exe)}</string><string>--background</string></array>
                    <key>RunAtLoad</key><true/>
                </dict>
                </plist>
                """.trimIndent(),
            )
            true
        }.getOrDefault(false)
    }

    /** Turns it on or off; false if it could not be changed. */
    fun set(enabled: Boolean): Boolean {
        if (Platform.isMac) return setMac(enabled)
        val file = link ?: return false
        if (!enabled) return !file.exists() || file.delete()
        val (target, args, dir) = selfCommand() ?: return false
        val icon = File(dir, "../../dnz.ico").canonicalFile.takeIf { it.exists() }?.path ?: target
        fun q(s: String) = "'" + s.replace("'", "''") + "'"
        val script = """
            ${'$'}s = (New-Object -ComObject WScript.Shell).CreateShortcut(${q(file.path)})
            ${'$'}s.TargetPath = ${q(target)}
            ${'$'}s.Arguments = ${q(args)}
            ${'$'}s.WorkingDirectory = ${q(dir)}
            ${'$'}s.IconLocation = ${q(icon)}
            ${'$'}s.Description = 'DNZ Launcher (background)'
            ${'$'}s.Save()
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return runCatching {
            file.parentFile.mkdirs()
            val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
                .redirectErrorStream(true).start()
            process.waitFor(20, TimeUnit.SECONDS) && file.exists()
        }.getOrDefault(false)
    }

    /** How this launcher was started: the packaged .exe, or javaw with the lib folder (desktop shortcut). */
    private fun selfCommand(): Triple<String, String, String>? {
        System.getProperty("jpackage.app-path")?.let { exe -> return Triple(exe, "--background", File(exe).parent) }
        val java = ProcessHandle.current().info().command().orElse(null) ?: return null
        val dir = File(System.getProperty("user.dir"))
        if (!File(dir, "lib").isDirectory) return null
        val javaw = File(File(java).parentFile, "javaw.exe").takeIf { it.exists() }?.path ?: java
        return Triple(javaw, "-cp \"lib\\*\" dnz.launcher.MainKt --background", dir.absolutePath)
    }
}
