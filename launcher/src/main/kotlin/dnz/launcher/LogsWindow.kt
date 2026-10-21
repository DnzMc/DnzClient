package dnz.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

private enum class Level { Error, Warn, Info }

private fun levelOf(line: String) = when {
    line.contains("/ERROR]") || line.contains("/FATAL]") || line.contains("Exception") || line.startsWith("\tat ") -> Level.Error
    line.contains("/WARN]") -> Level.Warn
    else -> Level.Info
}

/**
 * The game's log (what Minecraft prints while it runs) in its own window, live: new lines appear as the game
 * writes them.
 */
@Composable
fun LogsWindow(state: LauncherState, profile: Profile, onClose: () -> Unit) {
    val file = remember(profile) { File(GameLauncher.instanceDir(profile), "dnz-launcher.log") }
    val lines = remember(profile) { mutableStateListOf<String>() }
    var query by remember { mutableStateOf("") }
    var errorsOnly by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    fun t(tr: String, en: String, de: String) = when (state.language) {
        Lang.TR -> tr
        Lang.DE -> de
        Lang.EN -> en
    }

    // reads the file as it grows (and from the start again when a new game run started a new file)
    LaunchedEffect(file) {
        var offset = 0L
        var partial = ""
        while (true) {
            val chunk = withContext(Dispatchers.IO) {
                runCatching {
                    if (!file.exists()) return@runCatching null
                    if (file.length() < offset) {
                        offset = 0
                        partial = ""
                        lines.clear()
                    }
                    RandomAccessFile(file, "r").use { raf ->
                        val size = (raf.length() - offset).coerceAtMost(4L shl 20).toInt()
                        if (size <= 0) return@use null
                        raf.seek(offset)
                        val bytes = ByteArray(size)
                        raf.readFully(bytes)
                        offset += size
                        String(bytes, Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (chunk != null) {
                val text = partial + chunk
                val parts = text.split('\n')
                partial = parts.last()
                lines.addAll(parts.dropLast(1).map { it.trimEnd('\r') })
                if (lines.size > 20000) lines.removeRange(0, lines.size - 20000)
            }
            delay(400)
        }
    }

    val shown = remember(lines.size, query, errorsOnly) {
        lines.filter { (!errorsOnly || levelOf(it) != Level.Info) && (query.isBlank() || it.contains(query, ignoreCase = true)) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(shown.size, follow) { if (follow && shown.isNotEmpty()) listState.scrollToItem(shown.size - 1) }

    val windowState = rememberWindowState(width = 980.dp, height = 640.dp, position = WindowPosition(Alignment.Center))
    Window(onCloseRequest = onClose, state = windowState, title = "DNZ Logs · ${profile.name}", undecorated = true, icon = BitmapPainter(DnzLogo)) {
        Column(Modifier.fillMaxSize().background(Dnz.Background).border(1.dp, Dnz.Border)) {
            // Title bar
            WindowDraggableArea {
                Row(Modifier.fillMaxWidth().height(52.dp).background(Dnz.Sidebar).padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(if (state.gameRunning) Color(0xFF3DDC84) else Dnz.Muted))
                    Spacer(Modifier.width(10.dp))
                    Text(t("Oyun Logları", "Game Logs", "Spiel-Logs"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(profile.name, color = Dnz.Muted, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${lines.size} " + t("satır", "lines", "Zeilen"), color = Dnz.Muted, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).dnzClickable { onClose() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, null, tint = Dnz.Muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
            // Toolbar
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(Dnz.Surface).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = Dnz.Muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(t("Loglarda ara", "Search the logs", "Logs durchsuchen"), color = Dnz.Muted, fontSize = 13.sp)
                        BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = Dnz.Text, fontSize = 13.sp), cursorBrush = SolidColor(Dnz.Accent), modifier = Modifier.fillMaxWidth())
                    }
                }
                Chip(t("Sadece hatalar", "Errors only", "Nur Fehler"), errorsOnly) { errorsOnly = !errorsOnly }
                Chip(t("Otomatik kaydır", "Auto scroll", "Auto-Scroll"), follow) { follow = !follow }
                Chip(t("Kopyala", "Copy", "Kopieren"), false) {
                    val text = shown.joinToString("\n")
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
                }
                Chip(t("Klasörü aç", "Open folder", "Ordner öffnen"), false) {
                    runCatching { java.awt.Desktop.getDesktop().open(file.parentFile) }
                }
            }
            // Lines
            Box(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, bottom = 14.dp).clip(RoundedCornerShape(12.dp)).background(Dnz.Surface)) {
                if (shown.isEmpty()) {
                    Text(
                        if (lines.isEmpty()) t("Henüz log yok. Oyunu başlatınca burada görünecek.", "No logs yet. They show here once the game starts.", "Noch keine Logs. Sie erscheinen, sobald das Spiel startet.")
                        else t("Eşleşen satır yok", "No matching lines", "Keine passenden Zeilen"),
                        color = Dnz.Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    SelectionContainer {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                            itemsIndexed(shown) { _, line ->
                                val color = when (levelOf(line)) {
                                    Level.Error -> Color(0xFFFF6B6B)
                                    Level.Warn -> Color(0xFFFFC83D)
                                    Level.Info -> Dnz.Text.copy(alpha = 0.85f)
                                }
                                Text(line, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Dnz.Accent.copy(alpha = 0.18f) else Dnz.Surface)
            .border(1.dp, if (on) Dnz.Accent else Color.Transparent, RoundedCornerShape(10.dp)).dnzClickable { onClick() }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) Dnz.Accent else Dnz.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}
