package dnz.launcher

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.awt.image.BufferedImage
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

/** A Minecraft account the player can pick in the top-right account menu. */
data class McAccount(
    val name: String,
    /** Minecraft profile id without dashes (used for the skin). */
    val uuid: String,
    /** Id inside the official launcher's account file; null for DNZ's own test account. */
    val localId: String?,
    val file: File?,
)

/**
 * Accounts signed in to the official Minecraft Launcher. DNZ only reads names and profile ids, and when the player
 * switches, it only changes which account is active (sign-in keys stay untouched, the file is backed up first).
 */
object Accounts {
    private val dir = Platform.minecraftDir
    private val files = listOf("launcher_accounts_microsoft_store.json", "launcher_accounts.json").map { File(dir, it) }

    data class Official(val accounts: List<McAccount>, val activeLocalId: String?)

    fun readOfficial(): Official {
        val list = mutableListOf<McAccount>()
        var active: String? = null
        for (file in files.filter { it.isFile }) {
            val root = runCatching { JsonParser.parseString(file.readText()).asJsonObject }.getOrNull() ?: continue
            if (active == null) active = root["activeAccountLocalId"]?.takeIf { it.isJsonPrimitive }?.asString
            val accounts = root.getAsJsonObject("accounts") ?: continue
            for ((localId, value) in accounts.entrySet()) {
                val profile = value.asJsonObject.getAsJsonObject("minecraftProfile") ?: continue
                val name = profile["name"]?.asString ?: continue
                val uuid = profile["id"]?.asString ?: continue
                if (list.none { it.uuid == uuid }) list += McAccount(name, uuid, localId, file)
            }
        }
        return Official(list, active)
    }

    /** True while the Minecraft Launcher is open (it would overwrite the account file when it closes). */
    fun officialLauncherRunning(): Boolean = ProcessHandle.allProcesses().anyMatch { p ->
        val path = p.info().command().orElse("")
        val cmd = path.substringAfterLast('\\').substringAfterLast('/').lowercase()
        cmd == "minecraftlauncher.exe" || cmd == "minecraft.exe" ||
            (Platform.isMac && path.contains("/Minecraft.app/", ignoreCase = true) && !path.contains("/runtime/"))
    }

    /** Makes [account] the active one in the official launcher. Returns false if it could not be changed. */
    fun switchOfficial(account: McAccount): Boolean {
        val file = account.file ?: return false
        val localId = account.localId ?: return false
        if (officialLauncherRunning()) return false
        return runCatching {
            val root = JsonParser.parseString(file.readText()).asJsonObject
            if (root["activeAccountLocalId"]?.asString == localId) return true
            val backup = File(file.path + ".dnz-backup")
            if (!backup.exists()) file.copyTo(backup)
            root.addProperty("activeAccountLocalId", localId)
            val temp = File(file.path + ".tmp")
            temp.writeText(GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root))
            file.delete()
            check(temp.renameTo(file))
            true
        }.getOrDefault(false)
    }

    /** Opens the Minecraft Launcher, where new accounts are added. */
    fun openOfficialLauncher(): Boolean = runCatching { OfficialLauncher.open() }.isSuccess

    // ------------------------------------------------------------------ skin heads

    private val heads = HashMap<String, ImageBitmap?>()
    private val headDir = File(GameLauncher.root, "skins").apply { mkdirs() }

    fun cachedHead(uuid: String): ImageBitmap? = synchronized(heads) { heads[uuid] }

    /**
     * The face of the account's skin (face + hat layer), 8x8 pixels. Downloaded from Mojang once a day,
     * then read from ~/.dnzlauncher/skins so it shows instantly and offline.
     */
    fun head(uuid: String): ImageBitmap? {
        synchronized(heads) { if (heads.containsKey(uuid)) return heads[uuid] }
        val file = File(headDir, "$uuid.png")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 24 * 3600 * 1000L
        if (!fresh) {
            runCatching {
                val profile = Net.json("https://sessionserver.mojang.com/session/minecraft/profile/$uuid").asJsonObject
                val textures = profile.getAsJsonArray("properties").map { it.asJsonObject }.first { it["name"].asString == "textures" }
                val decoded = JsonParser.parseString(String(Base64.getDecoder().decode(textures["value"].asString))).asJsonObject
                val url = decoded.getAsJsonObject("textures").getAsJsonObject("SKIN")["url"].asString
                check(url.startsWith("https://textures.minecraft.net/") || url.startsWith("http://textures.minecraft.net/"))
                file.writeBytes(Net.bytes(url.replace("http://", "https://")))
            }
        }
        val image = runCatching { face(ImageIO.read(file)) }.getOrNull()
        synchronized(heads) { heads[uuid] = image }
        return image
    }

    private fun face(skin: BufferedImage): ImageBitmap {
        val out = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                out.setRGB(x, y, skin.getRGB(8 + x, 8 + y))
                val hat = skin.getRGB(40 + x, 8 + y)
                if ((hat ushr 24) > 0x40) out.setRGB(x, y, hat)
            }
        }
        return out.toComposeImageBitmap()
    }
}
