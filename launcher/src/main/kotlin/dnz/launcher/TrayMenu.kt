package dnz.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import java.awt.Image as AwtImage
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

private val trayImage: BufferedImage by lazy { ImageIO.read(TrayLogo::class.java.getResourceAsStream("/tray.png")) }
val TrayLogoBitmap: ImageBitmap by lazy { trayImage.toComposeImageBitmap() }

private object TrayLogo

private fun trayIconImage(size: Int): AwtImage {
    val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    // Step down by halves for a sharp small icon
    var src: BufferedImage = trayImage
    var w = src.width
    while (w / 2 >= size) {
        w /= 2
        val half = BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB)
        half.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(src, 0, 0, w, w, null)
            dispose()
        }
        src = half
    }
    g.drawImage(src, 0, 0, size, size, null)
    g.dispose()
    return out
}

/**
 * DNZ in the system tray: left click opens the launcher, right click opens a modern DNZ menu (play, logs,
 * folders, settings, quit) instead of Windows' plain one.
 */
@Composable
fun DnzTray(state: LauncherState, onOpen: () -> Unit, onQuit: () -> Unit) {
    var menuAt by remember { mutableStateOf<java.awt.Point?>(null) }
    DisposableEffect(Unit) {
        if (!SystemTray.isSupported()) return@DisposableEffect onDispose { }
        val tray = SystemTray.getSystemTray()
        val px = (tray.trayIconSize.width * java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
            .defaultConfiguration.defaultTransform.scaleX).toInt().coerceAtLeast(16)
        val icon = TrayIcon(trayIconImage(px), "DNZ Launcher").apply { isImageAutoSize = true }
        icon.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3 || e.isPopupTrigger) {
                    val p = e.locationOnScreen
                    SwingUtilities.invokeLater { menuAt = p }
                } else if (e.button == MouseEvent.BUTTON1) {
                    SwingUtilities.invokeLater { menuAt = null; onOpen() }
                }
            }
        })
        tray.add(icon)
        onDispose { tray.remove(icon) }
    }
    menuAt?.let { at -> TrayMenuWindow(state, at, onOpen, onQuit) { menuAt = null } }
}

private val MENU_W = 280.dp
private val MENU_H = 390.dp

@Composable
private fun TrayMenuWindow(state: LauncherState, at: java.awt.Point, onOpen: () -> Unit, onQuit: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val profile = state.profiles.getOrNull(state.selectedProfile)
    fun t(tr: String, en: String, de: String) = when (state.language) {
        Lang.TR -> tr
        Lang.DE -> de
        Lang.EN -> en
    }
    // above and left of the click (the taskbar is usually at the bottom), kept on the screen
    val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val x = (at.x - MENU_W.value.toInt()).coerceIn(screen.x, screen.x + screen.width - MENU_W.value.toInt())
    val y = (at.y - MENU_H.value.toInt()).coerceIn(screen.y, screen.y + screen.height - MENU_H.value.toInt())
    val windowState = rememberWindowState(size = DpSize(MENU_W, MENU_H), position = WindowPosition(x.dp, y.dp))
    Window(
        onCloseRequest = onDismiss, state = windowState, title = "DNZ", undecorated = true, transparent = true,
        resizable = false, alwaysOnTop = true, icon = androidx.compose.ui.graphics.painter.BitmapPainter(TrayLogoBitmap),
    ) {
        // closes when the player clicks anywhere else
        DisposableEffect(window) {
            val listener = object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent) = onDismiss()
            }
            window.addWindowFocusListener(listener)
            window.toFront()
            window.requestFocus()
            onDispose { window.removeWindowFocusListener(listener) }
        }
        Column(
            Modifier.fillMaxSize().padding(6.dp).shadow(16.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                .background(Dnz.SurfaceHigh).border(1.dp, Dnz.Border, RoundedCornerShape(16.dp)).padding(8.dp),
        ) {
            // Header: logo, name, version and what is happening
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(TrayLogoBitmap, null, Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("DNZ Launcher", color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val version = TrayLogo::class.java.`package`?.implementationVersion
                    Text(listOfNotNull(version?.let { "v$it" }, state.currentAccount?.name).joinToString(" · "), color = Dnz.Muted, fontSize = 12.sp)
                }
                val running = state.gameRunning
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background((if (running) Color(0xFF3DDC84) else Dnz.Muted).copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(if (running) t("Oyunda", "Playing", "Im Spiel") else t("Hazır", "Ready", "Bereit"), color = if (running) Color(0xFF3DDC84) else Dnz.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Divider()
            // Play the selected profile straight from the tray
            if (profile != null) {
                val canPlay = !state.busy && !state.gameRunning
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).height(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (canPlay) Dnz.Accent else Dnz.Surface)
                        .dnzClickable(enabled = canPlay) { onDismiss(); onOpen(); requireSignIn(state) { state.scope.launch { play(state) } } }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = if (canPlay) Dnz.OnAccent else Dnz.Muted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (state.gameRunning) t("Oyun açık", "Game is running", "Spiel läuft") else t("Oyna", "Play", "Spielen"), color = if (canPlay) Dnz.OnAccent else Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${profile.name} · ${profile.version}", color = (if (canPlay) Dnz.OnAccent else Dnz.Muted).copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
            }
            Item(Icons.Outlined.Home, t("Launcher'ı aç", "Open launcher", "Launcher öffnen")) { onDismiss(); onOpen() }
            Item(Icons.Outlined.Info, t("Logları aç", "Open logs", "Logs öffnen")) { onDismiss(); profile?.let { state.logsProfile = it } }
            Item(Icons.Outlined.List, t("Oyun klasörü", "Game folder", "Spielordner")) {
                onDismiss(); profile?.let { p -> runCatching { java.awt.Desktop.getDesktop().open(GameLauncher.instanceDir(p).apply { mkdirs() }) } }
            }
            Item(Icons.Outlined.Settings, t("Ayarlar", "Settings", "Einstellungen")) { onDismiss(); state.screen = Screen.Settings; onOpen() }
            Spacer(Modifier.weight(1f))
            Divider()
            Item(Icons.Outlined.ExitToApp, t("Çıkış", "Quit", "Beenden"), danger = true) { onDismiss(); onQuit() }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Dnz.Border))
}

@Composable
private fun Item(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).dnzClickable { onClick() }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (danger) Color(0xFFFF6B6B) else Dnz.Text
        Icon(icon, null, tint = if (danger) color else Dnz.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
