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
    val localId: String?,
    val file: File?,
)

/** Accounts signed in to the official Minecraft Launcher. */
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

    fun openOfficialLauncher(): Boolean = runCatching { OfficialLauncher.open() }.isSuccess

    // ------------------------------------------------------------------ skin heads

    private val heads = HashMap<String, ImageBitmap?>()
    private val headDir = File(GameLauncher.root, "skins").apply { mkdirs() }

    fun cachedHead(uuid: String): ImageBitmap? = synchronized(heads) { heads[uuid] }

    /** The face of the account's skin (face + hat layer), 8x8 pixels. */
    fun head(uuid: String): ImageBitmap? {
        synchronized(heads) { if (heads.containsKey(uuid)) return heads[uuid] }
        val image = runCatching { face(ImageIO.read(skinFile(uuid))) }.getOrNull()
        synchronized(heads) { heads[uuid] = image }
        return image
    }

    /** The whole skin (64x64 or old 64x32) and whether it has slim arms; null if it can't be had. */
    fun skin(uuid: String): Pair<BufferedImage, Boolean>? = runCatching {
        val file = skinFile(uuid)
        ImageIO.read(file) to File(headDir, "$uuid.slim").exists()
    }.getOrNull()

    /** The skin was changed: the next [skin] downloads it again. */
    fun forgetSkin(uuid: String) {
        File(headDir, "$uuid.png").delete()
    }

    /** ~/.dnzlauncher/skins/<uuid>.png, downloaded from Mojang once a day (and "<uuid>.slim" for slim arms). */
    private fun skinFile(uuid: String): File {
        val file = File(headDir, "$uuid.png")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 24 * 3600 * 1000L
        if (!fresh) {
            runCatching {
                val profile = Net.json("https://sessionserver.mojang.com/session/minecraft/profile/$uuid").asJsonObject
                val textures = profile.getAsJsonArray("properties").map { it.asJsonObject }.first { it["name"].asString == "textures" }
                val decoded = JsonParser.parseString(String(Base64.getDecoder().decode(textures["value"].asString))).asJsonObject
                val skin = decoded.getAsJsonObject("textures").getAsJsonObject("SKIN")
                val url = skin["url"].asString
                check(url.startsWith("https://textures.minecraft.net/") || url.startsWith("http://textures.minecraft.net/"))
                file.writeBytes(Net.bytes(url.replace("http://", "https://")))
                val slim = skin.getAsJsonObject("metadata")?.get("model")?.asString == "slim"
                File(headDir, "$uuid.slim").let { if (slim) it.writeText("") else it.delete() }
            }
        }
        return file
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
