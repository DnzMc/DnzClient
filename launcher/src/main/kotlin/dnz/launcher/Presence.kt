package dnz.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

/**
 * How many people have DNZ Launcher open, how many of them are playing and how many installed it, shown at
 * the top right.
 */
object Presence {
    private const val SERVER = "https://cloud.dnzclient.com"
    private val idFile = File(GameLauncher.root, "install-id")

    val installId: String by lazy {
        runCatching { idFile.readText().trim() }.getOrNull()?.takeIf { it.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) }
            ?: UUID.randomUUID().toString().also { runCatching { idFile.writeText(it) } }
    }

    var active by mutableStateOf<Int?>(null)
        private set
    var game by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set

    init {
        // Closing the launcher: it leaves the count right away instead of after a few minutes
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { quickPost("off") } })
    }

    private fun body(state: String) = JsonObject().apply {
        addProperty("id", installId)
        addProperty("state", state)
    }.toString()

    fun send(state: String) {
        val answer = Net.postJson("$SERVER/presence", body(state)).asJsonObject
        active = answer["launcher"]?.asInt
        game = answer["game"]?.asInt ?: 0
        total = answer["total"]?.asInt ?: 0
    }

    /** Screenshots only: fixed numbers. */
    internal fun preview(active: Int, game: Int, total: Int) {
        this.active = active
        this.game = game
        this.total = total
    }

    private fun quickPost(state: String) {
        val c = URI("$SERVER/presence").toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 2000
        c.readTimeout = 2000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body(state).toByteArray()) }
        c.responseCode
    }
}

/** Keeps the counts fresh while the launcher runs: every minute, and at once when the game starts or stops. */
@Composable
fun PresenceUpdates(state: LauncherState) {
    val playing = state.gameRunning
    LaunchedEffect(playing) {
        while (true) {
            withContext(Dispatchers.IO) { runCatching { Presence.send(if (playing) "game" else "launcher") } }
            delay(60_000)
        }
    }
}

/** Top right: "● Active players: 12 ⌄"; a click shows the players in game and all players below it. */
@Composable
fun PresenceChip(state: LauncherState) {
    val active = Presence.active ?: return
    var open by remember { mutableStateOf(state.presenceOpen) }
    fun s(tr: String, en: String, de: String) = when (state.language) { Lang.TR -> tr; Lang.EN -> en; Lang.DE -> de }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(if (open) Dnz.SurfaceHigh else Dnz.Surface)
                .dnzClickable(highlight = false) { open = !open }.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Dnz.Success))
            Spacer(Modifier.width(7.dp))
            Text(s("Aktif oyuncu: ", "Active players: ", "Aktive Spieler: "), color = Dnz.Muted, fontSize = 11.sp)
            Text("$active", color = Dnz.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("⌄", color = Dnz.Muted, fontSize = 11.sp, modifier = Modifier.rotate(if (open) 180f else 0f))
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(0, 44),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier.width(220.dp).clip(RoundedCornerShape(12.dp)).background(Dnz.SurfaceHigh)
                        .border(1.dp, Dnz.Border, RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatRow(s("Aktif oyuncu", "Active players", "Aktive Spieler"), active, Dnz.Success)
                    StatRow(s("Oyunda", "In game", "Im Spiel"), Presence.game, Dnz.Accent)
                    StatRow(s("Toplam oyuncu", "Overall players", "Spieler insgesamt"), Presence.total, Dnz.Muted)
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Dnz.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("$value", color = Dnz.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
