package dnz.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.GsonBuilder
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/** Settings of a Minecraft server that runs on this computer (Servers → Make → Run the server locally). */
data class LocalServerConfig(
    val name: String,
    val version: String,
    val folder: String,
    val ramGb: Int = 2,
    val gamemode: String = "survival",
    val difficulty: String = "normal",
    val maxPlayers: Int = 10,
    val pvp: Boolean = true,
    val whitelist: Boolean = false,
    val port: Int = 25565,
    /**
     * IP protection: friends join through a relay address (e4mc, e.g. abc.e4mc.link), never through this
     * computer's own internet address, so nobody can find where the player lives from it.
     */
    val ipProtection: Boolean = true,
)

/** The server while it runs: its console lines, the shareable address once e4mc gives one, and whether it is up. */
class RunningServer(val config: LocalServerConfig, internal val process: Process) {
    val lines = mutableStateListOf<String>()
    var domain by mutableStateOf<String?>(null)
    var ready by mutableStateOf(false)
    var stopped by mutableStateOf(false)
}

/** Local servers: one folder each in ~/.dnzlauncher/servers/ with its settings (dnz-server.json). */
object LocalServers {
    val root = File(GameLauncher.root, "servers")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private const val SETTINGS = "dnz-server.json"

    var running by mutableStateOf<RunningServer?>(null)
        private set

    init {
        // the launcher closing stops the server too (it saves the world first)
        Runtime.getRuntime().addShutdownHook(Thread { running?.let { stopNow(it) } })
    }

    fun list(): List<LocalServerConfig> = root.listFiles().orEmpty().mapNotNull { dir ->
        runCatching { gson.fromJson(File(dir, SETTINGS).readText(), LocalServerConfig::class.java) }.getOrNull()
    }.sortedBy { it.name.lowercase() }

    fun save(config: LocalServerConfig) {
        File(root, config.folder).apply { mkdirs() }.resolve(SETTINGS).writeText(gson.toJson(config))
    }

    fun delete(config: LocalServerConfig): Boolean = File(root, config.folder).deleteRecursively()

    fun folder(config: LocalServerConfig) = File(root, config.folder)

    /** A folder name not used yet. */
    fun freeFolder(name: String): String {
        val base = name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "Server" }
        var folder = base
        var n = 2
        while (File(root, folder).exists()) folder = "${base}_${n++}"
        return folder
    }

    /** IP protection works only where e4mc can run: Minecraft versions with Fabric. */
    fun canProtect(mc: String) = Loader.Fabric in Versions.loaders(mc)

    /** This computer's address in the home network (e.g. 192.168.1.20): only reachable from the same network. */
    fun lanAddress(): String? {
        // The address this computer uses to reach the internet (no packet is sent): the real Wi-Fi / cable one,
        // not a virtual network of VirtualBox, VMware or Hyper-V.
        runCatching {
            java.net.DatagramSocket().use { socket ->
                socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 53)
                (socket.localAddress as? Inet4Address)?.takeIf { it.isSiteLocalAddress }?.hostAddress
            }
        }.getOrNull()?.let { return it }
        val virtual = Regex("virtual|vmware|vbox|hyper-v|vethernet|wsl|docker|tap|tun", RegexOption.IGNORE_CASE)
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual && !virtual.containsMatchIn(it.displayName + " " + it.name) }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }

    /** Downloads what the server needs, writes its settings and starts it. [owner]: the player, made operator. */
    suspend fun start(config: LocalServerConfig, owner: Account?, progress: (String) -> Unit): RunningServer {
        check(running?.stopped != false) { "A server is already running" }
        val dir = folder(config).apply { mkdirs() }
        save(config)
        progress("Java")
        val java = GameLauncher.javaFor(config.version) { text, _ -> progress(text) }
        val fabric = canProtect(config.version)
        val jar: File
        if (fabric) {
            progress("Fabric")
            val loader = Net.json("https://meta.fabricmc.net/v2/versions/loader/${config.version}").asJsonArray
                .map { it.asJsonObject["loader"].asJsonObject }.first { it["stable"].asBoolean }["version"].asString
            val installer = Net.json("https://meta.fabricmc.net/v2/versions/installer").asJsonArray
                .map { it.asJsonObject }.first { it["stable"].asBoolean }["version"].asString
            jar = File(dir, "fabric-server-launch.jar")
            val stamp = File(dir, ".dnz-fabric")
            val want = "${config.version}/$loader/$installer"
            if (!jar.exists() || !stamp.exists() || stamp.readText() != want) {
                Net.download("https://meta.fabricmc.net/v2/versions/loader/${config.version}/$loader/$installer/server/jar", jar)
                stamp.writeText(want)
            }
            val mods = File(dir, "mods").apply { mkdirs() }
            progress("Fabric API")
            GameLauncher.installModrinthMod("fabric-api", config.version, mods)
            if (config.ipProtection) {
                progress("e4mc")
                GameLauncher.installModrinthMod("e4mc", config.version, mods)
            } else {
                // Protection turned off: the relay mod goes away, players connect straight to this computer
                val marker = File(mods, ".dnz-e4mc")
                if (marker.exists()) {
                    File(mods, marker.readText().trim()).delete()
                    marker.delete()
                }
            }
        } else {
            progress("Minecraft server")
            val server = GameLauncher.vanillaJson(config.version).getAsJsonObject("downloads").getAsJsonObject("server")
            jar = File(dir, "server.jar")
            Net.download(server["url"].asString, jar, server["sha1"].asString)
        }
        // the player accepted Minecraft's EULA in the form before this runs
        File(dir, "eula.txt").writeText("eula=true\n")
        writeProperties(File(dir, "server.properties"), config)
        if (owner != null && !owner.offline) writeOwner(dir, owner, config.whitelist)

        val command = listOf(java.absolutePath, "-Xms1G", "-Xmx${config.ramGb}G", "-jar", jar.name, "nogui")
        val process = ProcessBuilder(command).directory(dir).redirectErrorStream(true).start()
        val server = RunningServer(config, process)
        running = server
        Thread({
            val domain = Regex("Domain assigned: (\\S+)")
            process.inputStream.bufferedReader().forEachLine { line ->
                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                    server.lines += line
                    if (server.lines.size > 400) server.lines.removeRange(0, server.lines.size - 400)
                    domain.find(line)?.let { server.domain = it.groupValues[1] }
                    if ("Done (" in line) server.ready = true
                }
            }
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { server.stopped = true }
        }, "DNZ local server").apply { isDaemon = true }.start()
        return server
    }

    /** Saves the world and stops (the game's "stop" command), forcing it after 20 seconds. */
    fun stop() {
        running?.let { Thread({ stopNow(it) }, "DNZ local server stop").start() }
    }

    private fun stopNow(server: RunningServer) {
        if (server.stopped || !server.process.isAlive) return
        runCatching {
            server.process.outputStream.bufferedWriter().apply { write("stop\n"); flush() }
        }
        if (!server.process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) server.process.destroyForcibly()
    }

    /** Sends a console command (e.g. "op name", "whitelist add name"). */
    fun command(text: String) {
        val server = running ?: return
        if (server.stopped) return
        runCatching { server.process.outputStream.bufferedWriter().apply { write(text.trim() + "\n"); flush() } }
    }

    private fun writeProperties(file: File, config: LocalServerConfig) {
        val props = java.util.Properties()
        if (file.exists()) file.inputStream().use { props.load(it) }
        props["motd"] = config.name
        props["gamemode"] = config.gamemode
        props["difficulty"] = config.difficulty
        props["max-players"] = config.maxPlayers.toString()
        props["pvp"] = config.pvp.toString()
        props["white-list"] = config.whitelist.toString()
        props["enforce-whitelist"] = config.whitelist.toString()
        props["server-port"] = config.port.toString()
        // Real Minecraft accounts only
        props["online-mode"] = "true"
        file.outputStream().use { props.store(it, "DNZ Launcher local server") }
    }

    private fun writeOwner(dir: File, owner: Account, whitelist: Boolean) {
        val uuid = owner.uuid.let { if (it.contains('-')) it else it.replaceFirst(Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "$1-$2-$3-$4-$5") }
        val ops = File(dir, "ops.json")
        if (!ops.exists()) ops.writeText("""[{"uuid":"$uuid","name":"${owner.name}","level":4,"bypassesPlayerLimit":true}]""")
        val list = File(dir, "whitelist.json")
        if (whitelist && !list.exists()) list.writeText("""[{"uuid":"$uuid","name":"${owner.name}"}]""")
    }
}
