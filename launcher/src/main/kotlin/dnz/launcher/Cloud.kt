package dnz.launcher

import com.google.gson.JsonParser
import java.io.File

/**
 * DNZ Cloud switch (off by default): when on, DNZ Client keeps key bindings and DNZ settings in the player's cloud
 * copy at cloud.dnzclient.com (the game does the syncing, see client CloudSync). Shared with the game through
 * ~/.dnzlauncher/cloud.json.
 */
object Cloud {
    private val file = File(System.getProperty("user.home"), ".dnzlauncher/cloud.json")

    var enabled: Boolean
        get() = runCatching { JsonParser.parseString(file.readText()).asJsonObject["enabled"].asBoolean }.getOrDefault(false)
        set(value) {
            runCatching {
                file.parentFile.mkdirs()
                file.writeText("{\n  \"enabled\": $value\n}")
            }
        }
}
