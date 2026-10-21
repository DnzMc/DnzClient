package dnz.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO

/** DNZ themes (docs/dnz-theme.md): a `.dnztheme` ZIP (theme.json + images/) or an old launcher-only `.json`. */
object ThemePack {
    val dir = File(GameLauncher.root, "theme")
    private const val MAX_FILE = 4 shl 20
    private const val MAX_PICTURE = 1_000_000
    private const val MAX_SIDE = 2048

    var background by androidx.compose.runtime.mutableStateOf<ImageBitmap?>(null)
        private set

    /** Unpacks [bytes] (ZIP or JSON) as the applied theme; returns its launcher colors (null if it has none / is broken). */
    fun install(bytes: ByteArray): ThemeColors? {
        require(bytes.size <= MAX_FILE) { "theme too big" }
        val files = LinkedHashMap<String, ByteArray>()
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (isZip) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var total = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    val ok = name == "theme.json" ||
                        (Regex("^images/[A-Za-z0-9_.-]{1,64}\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE).matches(name))
                    if (entry.isDirectory || !ok) continue
                    val data = zip.readNBytes(MAX_PICTURE + 1)
                    require(data.size <= MAX_PICTURE) { "picture too big: $name" }
                    total += data.size
                    require(total <= MAX_FILE) { "theme too big" }
                    if (name != "theme.json") {
                        val image = ImageIO.read(ByteArrayInputStream(data)) ?: error("not a picture: $name")
                        require(image.width <= MAX_SIDE && image.height <= MAX_SIDE) { "picture too large: $name" }
                    }
                    files[name] = data
                }
            }
        } else {
            files["theme.json"] = bytes
        }
        val json = files["theme.json"] ?: error("no theme.json")
        val root = JsonParser.parseString(String(json, Charsets.UTF_8)).asJsonObject
        // old launcher-only theme: turned into version 2 (the game keeps its own look)
        val v2 = if (root.has("dnzTheme")) root else JsonObject().apply {
            addProperty("dnzTheme", 2)
            addProperty("name", root["name"]?.asString ?: "Theme")
            root["colors"]?.let { add("launcher", it) }
        }
        files["theme.json"] = GsonBuilder().setPrettyPrinting().create().toJson(v2).toByteArray()

        dir.deleteRecursively()
        File(dir, "images").mkdirs()
        for ((name, data) in files) File(dir, name).writeBytes(data)
        load()
        return launcherColors(v2)
    }

    /** No theme: DNZ's own look in the launcher and the game. */
    fun clear() {
        dir.deleteRecursively()
        background = null
    }

    /** Reads the applied theme's launcher picture (at start and after install). */
    fun load() {
        background = runCatching {
            val root = JsonParser.parseString(File(dir, "theme.json").readText()).asJsonObject
            val path = root.getAsJsonObject("launcher")?.get("backgroundImage")?.asString ?: return@runCatching null
            val file = File(dir, path).canonicalFile
            check(file.path.startsWith(File(dir, "images").canonicalPath + File.separator))
            ImageIO.read(file)?.toComposeImageBitmap()
        }.getOrNull()
    }

    private fun launcherColors(root: JsonObject): ThemeColors? {
        val colors = root.getAsJsonObject("launcher") ?: return null
        val copy = JsonObject().apply {
            addProperty("name", root["name"]?.asString ?: "Theme")
            add("colors", colors)
        }
        return ThemeColors.fromJson(copy.toString())
    }
}
