package dnz.launcher

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.Base64

/**
 * Hand-off play: DNZ prepares the mods, adds a profile to the official Minecraft Launcher and opens it.
 * The official launcher handles the Microsoft account, so DNZ never touches it and needs no Mojang approval.
 */
object OfficialLauncher {
    private val minecraftDir = Platform.minecraftDir
    private const val STORE_APP_ID = "Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft"
    private val classicExes = listOf(
        File("C:\\Program Files (x86)\\Minecraft Launcher\\MinecraftLauncher.exe"),
        File("C:\\XboxGames\\Minecraft Launcher\\Content\\Minecraft.exe"),
    )

    suspend fun play(profile: Profile, ramGb: Int, progress: (String, Float) -> Unit) = withContext(Dispatchers.IO) {
        val mc = profile.version

        progress("Fabric hazırlanıyor...", 0.10f)
        val loader = Net.json("https://meta.fabricmc.net/v2/versions/loader/$mc").asJsonArray
            .map { it.asJsonObject["loader"].asJsonObject }.first { it["stable"].asBoolean }["version"].asString
        val fabricJson = Net.json("https://meta.fabricmc.net/v2/versions/loader/$mc/$loader/profile/json").asJsonObject
        val versionId = fabricJson["id"].asString
        val versionDir = File(minecraftDir, "versions/$versionId").apply { mkdirs() }
        File(versionDir, "$versionId.json").writeText(fabricJson.toString())
        // Same as the official Fabric installer: an empty jar next to the json.
        File(versionDir, "$versionId.jar").takeIf { !it.exists() }?.writeBytes(EMPTY_ZIP)

        progress("Modlar kuruluyor...", 0.30f)
        val instance = GameLauncher.instanceDir(profile)
        GameLauncher.installMods(mc, File(instance, "mods")) { text, value -> progress(text, 0.30f + value * 0.55f) }

        progress("Minecraft Launcher profili ekleniyor...", 0.90f)
        writeProfile(profile, versionId, instance, ramGb)

        // Laptops: the official launcher's Java starts on the strong graphics card too.
        GpuPreference.officialRuntimes().forEach { GpuPreference.preferDedicated(it) }

        progress("Minecraft Launcher açılıyor...", 0.97f)
        open()
    }

    private fun writeProfile(profile: Profile, versionId: String, instance: File, ramGb: Int) {
        val file = File(minecraftDir, "launcher_profiles.json")
        val gson = GsonBuilder().setPrettyPrinting().create()
        val root = if (file.exists()) {
            // Keep one backup of the user's original file, just in case.
            val backup = File(minecraftDir, "launcher_profiles.json.dnz-backup")
            if (!backup.exists()) file.copyTo(backup)
            JsonParser.parseString(file.readText()).asJsonObject
        } else {
            JsonObject().apply { addProperty("version", 3) }
        }
        val profiles = root.getAsJsonObject("profiles") ?: JsonObject().also { root.add("profiles", it) }
        val now = Instant.now().toString()
        val key = "dnz-" + instance.name.lowercase()
        val entry = profiles.getAsJsonObject(key) ?: JsonObject().apply { addProperty("created", now) }
        entry.addProperty("name", profile.name)
        entry.addProperty("type", "custom")
        entry.addProperty("lastVersionId", versionId)
        entry.addProperty("gameDir", instance.absolutePath)
        entry.addProperty("javaArgs", GameLauncher.jvmArgs(ramGb).joinToString(" "))
        entry.addProperty("lastUsed", now) // newest profile is pre-selected in the official launcher
        entry.addProperty("icon", "data:image/png;base64," + Base64.getEncoder().encodeToString(logoPng()))
        profiles.add(key, entry)
        file.writeText(gson.toJson(root))
    }

    /** Opens the Minecraft Launcher (classic or Microsoft Store version). */
    fun open() {
        if (Platform.isMac) {
            // /Applications/Minecraft.app
            ProcessBuilder("open", "-a", "Minecraft").start()
            return
        }
        val exe = classicExes.firstOrNull { it.exists() }
        if (exe != null) {
            ProcessBuilder(exe.absolutePath).start()
        } else {
            // Microsoft Store / Xbox app version.
            ProcessBuilder("explorer.exe", "shell:AppsFolder\\$STORE_APP_ID").start()
        }
    }

    private fun logoPng(): ByteArray = OfficialLauncher::class.java.getResourceAsStream("/logo.png")!!.readBytes()

    /** Smallest valid zip file (just an end-of-central-directory record). */
    private val EMPTY_ZIP = byteArrayOf(0x50, 0x4B, 0x05, 0x06) + ByteArray(18)
}
