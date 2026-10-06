package dnz.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext

/** The DNZ logo (loaded once). */

val DnzLogo: ImageBitmap by lazy {
    val bytes = LauncherState::class.java.getResourceAsStream("/logo.png")!!.readBytes()
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

/** Pixel scene on the home page (background removed, mirrored). */
val HomeScene: ImageBitmap by lazy {
    val bytes = LauncherState::class.java.getResourceAsStream("/home-scene.png")!!.readBytes()
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

@Composable
fun LauncherApp(
    state: LauncherState,
    dragArea: @Composable (@Composable () -> Unit) -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    // Save preferences shortly after they change (not on every step of a slider).
    // Signed in with Microsoft before: sign in again quietly (no window).
    LaunchedEffect(Unit) {
        if (state.account == null) withContext(Dispatchers.IO) { runCatching { MicrosoftAuth.restore() }.getOrNull() }?.let { state.account = it }
    }
    // Mac, first start: AUTO once by itself, so the game starts with settings made for this Mac (it can be undone).
    LaunchedEffect(Unit) {
        if (Platform.isMac && !state.macAutoDone) {
            state.macAutoDone = true
            runAuto(state)
        }
    }
    // A few seconds after start: look for a new launcher version (downloaded in the background).
    LaunchedEffect(Unit) {
        delay(5000)
        withContext(Dispatchers.IO) { Updater.check() }
    }
    LaunchedEffect(state) {
        snapshotFlow { Settings.snapshot(state) }.drop(1).collectLatest { saved ->
            delay(400)
            withContext(Dispatchers.IO) { Settings.save(saved) }
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Dnz.Accent, background = Dnz.Background, surface = Dnz.Surface)) {
        Column(Modifier.fillMaxSize().background(Dnz.Background).border(1.dp, Dnz.Border)) {
            dragArea { TitleBar(state, onMinimize, onClose) }
            Row(Modifier.fillMaxSize()) {
                Sidebar(state)
                Box(Modifier.fillMaxSize()) {
                    when (state.screen) {
                        Screen.Home -> HomeScreen(state)
                        Screen.Profiles -> ProfilesScreen(state)
                        Screen.Mods -> ModsScreen(state)
                        Screen.Servers -> ServersScreen(state)
                        Screen.Settings -> SettingsScreen(state)
                        Screen.Account -> AccountScreen(state)
                    }
                    Toast(state, Modifier.align(Alignment.BottomCenter))
                    UpdateBanner(state, Modifier.align(Alignment.TopCenter))
                    CloudSaving(state)
                }
            }
        }
        MicrosoftLoginDialog(state)
        state.dialog?.let { (title, body) ->
            AlertDialog(
                onDismissRequest = { state.dialog = null },
                containerColor = Dnz.SurfaceHigh,
                title = { Text(title, color = Dnz.Text, fontWeight = FontWeight.Bold) },
                text = { Text(body, color = Dnz.Muted) },
                confirmButton = { DnzButton(state.t("ok"), onClick = { state.dialog = null }) },
            )
        }
        state.confirm?.let { confirm ->
            AlertDialog(
                onDismissRequest = { state.confirm = null },
                containerColor = Dnz.SurfaceHigh,
                title = { Text(confirm.title, color = Dnz.Text, fontWeight = FontWeight.Bold) },
                text = { Text(confirm.body, color = Dnz.Muted) },
                confirmButton = {
                    DnzButton(confirm.button, onClick = {
                        state.confirm = null
                        confirm.onConfirm()
                    })
                },
                dismissButton = {
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).dnzClickable { state.confirm = null }.padding(horizontal = 18.dp, vertical = 10.dp),
                    ) { Text(state.t("cancel"), color = Dnz.Muted, fontWeight = FontWeight.SemiBold) }
                },
            )
        }
    }
}

/** Short message at the bottom of the page that hides itself after a few seconds. */
@Composable
private fun Toast(state: LauncherState, modifier: Modifier) {
    val message = state.toast ?: return
    LaunchedEffect(message) {
        delay(4500)
        if (state.toast == message) state.toast = null
    }
    Row(
        modifier.padding(24.dp).widthIn(max = 640.dp).clip(RoundedCornerShape(12.dp)).background(Dnz.SurfaceHigh)
            .border(1.dp, Dnz.Success.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).dnzClickable { state.toast = null }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CheckCircle, null, tint = Dnz.Success, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, color = Dnz.Text, fontSize = 13.sp)
    }
}

/**
 * The game sends settings to the cloud as it closes (DNZ Cloud): a note asks the player not to close anything
 * meanwhile. The game keeps the marker file only while it is saving (a leftover older than a minute is ignored).
 */
@Composable
private fun CloudSaving(state: LauncherState) {
    val marker = remember { java.io.File(System.getProperty("user.home"), ".dnzlauncher/cloud-saving") }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            saving = withContext(Dispatchers.IO) { marker.exists() && System.currentTimeMillis() - marker.lastModified() < 60_000 }
            delay(500)
        }
    }
    if (!saving) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Dnz.SurfaceHigh).border(1.dp, Dnz.Accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = Dnz.Accent, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(16.dp))
            Text(state.t("cloud.saving.title"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(state.t("cloud.saving.body"), color = Dnz.Muted, fontSize = 13.sp)
        }
    }
}

/** A new launcher version is downloaded: one click restarts into it (otherwise it comes with the next start). */
@Composable
private fun UpdateBanner(state: LauncherState, modifier: Modifier) {
    val version = Updater.ready ?: return
    Row(
        modifier.padding(16.dp).clip(RoundedCornerShape(12.dp)).background(Dnz.SurfaceHigh)
            .border(1.dp, Dnz.Accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Refresh, null, tint = Dnz.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(state.t("update.ready").format(version), color = Dnz.Text, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        DnzButton(state.t("update.restart"), onClick = { if (Updater.restartNow()) kotlin.system.exitProcess(0) })
    }
}

@Composable
private fun TitleBar(state: LauncherState, onMinimize: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(32.dp).background(Dnz.Background).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        if (state.screen != Screen.Home) AccountMenu(state) // Home shows the account top right
        Spacer(Modifier.width(8.dp))
        WindowButton("—", Dnz.SurfaceHigh, onMinimize)
        WindowButton("✕", Color(0xFFC42B1C), onClose)
    }
}

@Composable
private fun WindowButton(label: String, hoverColor: Color, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        Modifier.width(46.dp).fillMaxHeight().background(if (hovered) hoverColor else Color.Transparent)
            .hoverable(hover).dnzClickable(highlight = false, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Dnz.Text, fontSize = 12.sp) }
}

@Composable
private fun Sidebar(state: LauncherState) {
    // Narrow icon bar: logo, then one rounded square per page.
    Column(
        Modifier.width(88.dp).fillMaxHeight().background(Dnz.Sidebar).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(DnzLogo, null, Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)), colorFilter = Dnz.LogoFilter)
        Spacer(Modifier.height(28.dp))
        NavItem(state, Screen.Home, Icons.Outlined.Home, "home")
        NavItem(state, Screen.Servers, ServerIcon, "servers")
        NavItem(state, Screen.Profiles, Icons.Outlined.List, "profiles")
        NavItem(state, Screen.Mods, Icons.Outlined.Build, "mods")
        NavItem(state, Screen.Account, Icons.Outlined.Person, "account")
        NavItem(state, Screen.Settings, Icons.Outlined.Settings, "settings")
    }
}

/** Bottom of the sidebar: skin face and name of the active account; opens the Account page. */
@Composable
private fun AccountChip(state: LauncherState) {
    val current = state.currentAccount
    val selected = state.screen == Screen.Account
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) Dnz.SurfaceHigh else Dnz.Surface)
            .dnzClickable { state.screen = Screen.Account }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkinHead(current, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(current?.name ?: state.t("account"), color = Dnz.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(if (current == null) state.t("not_signed") else "Microsoft", color = Dnz.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NavItem(state: LauncherState, screen: Screen, icon: ImageVector, key: String) {
    val selected = state.screen == screen
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        Modifier.padding(vertical = 4.dp).size(48.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) Dnz.SurfaceHigh else if (hovered) Dnz.Surface else Color.Transparent)
            .hoverable(hover).dnzClickable(highlight = false) { state.screen = screen },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, state.t(key), tint = if (selected) Dnz.Accent else Dnz.Muted, modifier = Modifier.size(22.dp))
    }
}

/** Accent-gradient button used everywhere. */
@Composable
fun DnzButton(text: String, modifier: Modifier = Modifier, big: Boolean = false, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        modifier.clip(RoundedCornerShape(if (big) 14.dp else 10.dp))
            .background(Dnz.AccentGradient)
            .background(if (hovered) Color(0x38FFFFFF) else Color.Transparent)
            .hoverable(hover).dnzClickable(highlight = false, onClick = onClick)
            .padding(horizontal = if (big) 48.dp else 18.dp, vertical = if (big) 16.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Dnz.OnAccent, fontWeight = FontWeight.Black, fontSize = if (big) 20.sp else 14.sp, letterSpacing = if (big) 3.sp else 0.sp)
    }
}

/** Dark rounded card. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(16.dp)).padding(20.dp),
        content = content,
    )
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, color = Dnz.Text, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 18.dp))
}
