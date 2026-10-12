package dnz.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonParser
import java.io.File
import java.lang.management.ManagementFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.util.Base64

/**
 * Self-update of the installed (setup or portable) launcher.
 *
 * download.dnzclient.com/launcher-latest.json lists every jar of the lib folder with its SHA-256; it is signed with
 * DNZ's private update key (launcher-latest.json.sig, Ed25519) and only accepted with that signature. Changed jars
 * are downloaded into the "update" folder next to lib. The swap happens after the launcher has closed
 * (UpdateApply, a small program started from a temporary copy), then the launcher starts again.
 */
object Updater {
    private const val BASE = "https://download.dnzclient.com/"
    private const val MANIFEST = "launcher-latest.json"
    /** DNZ's public update key; the private key never leaves the release computer. */
    private const val PUBLIC_KEY = "MCowBQYDK2VwAyEAhOU6kb5Gnk9J64ua74/K23VXG9MgXJ+HL7uV3ZFg3k4="

    /** Version of a downloaded update waiting for a restart, or null. */
    var ready by mutableStateOf<String?>(null)
        private set

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build()

    /** The lib folder of an installed launcher, or null (development, single jar, Mac app). */
    private val libDir: File? by lazy {
        runCatching {
            val jar = File(Updater::class.java.protectionDomain.codeSource.location.toURI())
            if (jar.isFile && jar.name.startsWith("libs-dnz-launcher") && jar.parentFile.name == "lib") jar.parentFile else null
        }.getOrNull()
    }

    private val stageDir: File? get() = libDir?.let { File(it.parentFile, "update") }

    /** What the "Check for updates" button in Settings shows. */
    enum class Status { Idle, Checking, UpToDate, Ready, Failed, NotInstalled }

    var status by mutableStateOf(Status.Idle)
        private set

    /** This launcher's version ("1.0.12"), or null in development. */
    val current: String? get() = Updater::class.java.`package`.implementationVersion

    private val lock = Any()

    /** Looks for a new version and downloads it. Runs in the background; problems only mean "no update now". */
    fun check() {
        if (libDir == null) {
            status = Status.NotInstalled
            return
        }
        status = Status.Checking
        synchronized(lock) { runCatching { newVersion(Duration.ofSeconds(30))?.let { download(it) } }.onFailure { lastCheckFailed = true } }
        status = when {
            ready != null -> Status.Ready
            lastCheckFailed -> Status.Failed
            else -> Status.UpToDate
        }
    }

    /**
     * At start, before any window: a waiting update is swapped in, and a newer version on the server (a fresh install
     * from an older setup file, or an update that came out while the launcher was closed) is downloaded first, with
     * the "Updating your launcher" window. Returns true when the launcher must quit (UpdateApply starts it again).
     */
    fun updateAtStart(args: Array<String>): Boolean {
        if (applyPendingAtStart(args)) return true
        libDir ?: return false
        // A quick look only: without internet the launcher simply starts as it is.
        val update = runCatching { newVersion(Duration.ofSeconds(4)) }.getOrNull() ?: return false
        val splash = if ("--background" in args) null else UpdateSplash.show("Updating your launcher...")
        val ok = synchronized(lock) { runCatching { download(update) }.getOrDefault(false) }
        if (ok && startApply(args)) return true // the splash stays until this process ends
        splash?.close()
        return false
    }

    private var lastCheckFailed = false

    /** A newer, correctly signed version on the server whose files differ from ours, or null. */
    private class Update(val version: String, val json: ByteArray, val sig: String, val changed: Map<String, String>, val removed: List<String>)

    private fun newVersion(timeout: Duration): Update? {
        val lib = libDir ?: return null
        lastCheckFailed = true
        val json = fetch(BASE + MANIFEST + "?t=" + System.currentTimeMillis(), timeout)
        val sig = String(fetch(BASE + "$MANIFEST.sig?t=" + System.currentTimeMillis(), timeout)).trim()
        if (!verify(json, sig)) return null
        lastCheckFailed = false
        val manifest = JsonParser.parseString(String(json)).asJsonObject
        val version = manifest["version"].asString
        // Never back to an older (or the same) version, e.g. while a newer build is installed by hand.
        if (!newer(version, current ?: return null)) return null
        val files = manifest["files"].asJsonArray.map { it.asJsonObject }.associate { it["name"].asString to it["sha256"].asString }
        if (files.keys.any { it.contains('/') || it.contains('\\') || !it.endsWith(".jar") }) return null
        val changed = files.filter { (name, sha) -> !File(lib, name).isFile || sha256(File(lib, name)) != sha }
        val removed = lib.listFiles().orEmpty().filter { it.name.endsWith(".jar") && it.name !in files }.map { it.name }
        if (changed.isEmpty() && removed.isEmpty()) return null
        return Update(version, json, sig, changed, removed)
    }

    /** Downloads [update] into the update folder; true when it is complete. */
    private fun download(update: Update): Boolean {
        val stage = stageDir ?: return false
        if (ready == update.version && File(stage, MANIFEST).isFile) return true // already waiting
        val json = update.json
        val sig = update.sig
        val version = update.version
        val changed = update.changed
        val removed = update.removed
        return runCatching {
            stage.deleteRecursively()
            stage.mkdirs()
            for ((name, sha) in changed) {
                val target = File(stage, name)
                val part = File(stage, "$name.part")
                val response = http.send(
                    HttpRequest.newBuilder(URI.create(BASE + "launcher-$sha.jar")).timeout(Duration.ofMinutes(10)).build(),
                    HttpResponse.BodyHandlers.ofFile(part.toPath()),
                )
                check(response.statusCode() == 200 && sha256(part) == sha) { "bad download $name" }
                check(part.renameTo(target))
            }
            File(stage, "remove.txt").writeText(removed.joinToString("\n"))
            // Written last: the update folder counts as complete only with the signed manifest in it.
            File(stage, MANIFEST).writeBytes(json)
            File(stage, "$MANIFEST.sig").writeText(sig)
            ready = version
            true
        }.getOrElse {
            stage.deleteRecursively()
            lastCheckFailed = true
            false
        }
    }

    /**
     * At start: a complete, still correctly signed update waits in the update folder → start UpdateApply and
     * return true (the launcher then quits; UpdateApply starts it again when the files are swapped).
     */
    fun applyPendingAtStart(args: Array<String>): Boolean {
        val stage = stageDir ?: return false
        val json = File(stage, MANIFEST)
        if (!json.isFile) return false
        val ok = runCatching { verify(json.readBytes(), File(stage, "$MANIFEST.sig").readText().trim()) }.getOrDefault(false)
        if (!ok) {
            stage.deleteRecursively()
            return false
        }
        return startApply(args)
    }

    /** "Restart" button: swap the files now and start again. */
    fun restartNow(): Boolean = startApply(emptyArray())

    private fun startApply(args: Array<String>): Boolean {
        val lib = libDir ?: return false
        val stage = stageDir ?: return false
        return runCatching {
            // UpdateApply runs from a copy, because the jars in lib are about to be replaced.
            val copy = File.createTempFile("dnz-update-", ".jar")
            File(Updater::class.java.protectionDomain.codeSource.location.toURI()).copyTo(copy, overwrite = true)
            val home = System.getProperty("java.home")
            val javaw = listOf("bin/javaw.exe", "bin/java").map { File(home, it) }.first { it.isFile }
            val command = mutableListOf(javaw.path, "-cp", copy.path, "dnz.launcher.UpdateApply",
                ProcessHandle.current().pid().toString(), lib.path, stage.path, "--", javaw.path)
            command += ManagementFactory.getRuntimeMXBean().inputArguments
            command += listOf("-cp", File(lib, "*").path, "dnz.launcher.MainKt")
            command += args
            ProcessBuilder(command).directory(lib.parentFile).start()
            true
        }.getOrDefault(false)
    }

    /** "1.0.10" is newer than "1.0.9". */
    private fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun fetch(url: String, timeout: Duration): ByteArray {
        val response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).build(), HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
        return response.body()
    }

    private fun verify(data: ByteArray, signature: String): Boolean {
        val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)))
        return Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(data)
            verify(Base64.getDecoder().decode(signature))
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
