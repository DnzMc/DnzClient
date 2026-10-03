package dnz.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Server list state; kept in LauncherState so pings survive switching pages. */
class ServersState {
    var servers by mutableStateOf<List<SavedServer>>(emptyList())
    var loadedFor by mutableStateOf<String?>(null)
    /** Address -> status (null while pinging, failure = offline). */
    val status = mutableStateMapOf<String, Result<ServerStatus>?>()
    var adding by mutableStateOf(false)
    var newName by mutableStateOf("")
    var newAddress by mutableStateOf("")
}

/** Server rack icon for the sidebar (two stacked boxes with status lights). */
val ServerIcon: ImageVector by lazy {
    ImageVector.Builder("server", 24.dp, 24.dp, 24f, 24f).apply {
        for (top in listOf(3f, 13f)) {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(5f, top); lineTo(19f, top); quadTo(21f, top, 21f, top + 2f); lineTo(21f, top + 6f)
                quadTo(21f, top + 8f, 19f, top + 8f); lineTo(5f, top + 8f); quadTo(3f, top + 8f, 3f, top + 6f)
                lineTo(3f, top + 2f); quadTo(3f, top, 5f, top); close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(7f, top + 3f); lineTo(9f, top + 3f); lineTo(9f, top + 5f); lineTo(7f, top + 5f); close()
            }
        }
    }.build()
}

@Composable
fun ServersScreen(state: LauncherState) {
    val sv = state.servers
    val profile = state.profiles[state.selectedProfile]
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile.name) {
        if (sv.loadedFor != profile.name) reload(state)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(state.t("servers"))
            Spacer(Modifier.weight(1f))
            SmallButton(state.t("servers.refresh"), Icons.Filled.Refresh) { scope.launch { reload(state) } }
            Spacer(Modifier.width(8.dp))
            DnzButton("+ " + state.t("servers.add")) { sv.adding = !sv.adding }
        }
        // Which profile's list this is (every profile has its own, like in the game).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.t("servers.profile"), color = Dnz.Muted, fontSize = 12.sp)
            state.profiles.forEachIndexed { i, p ->
                val on = i == state.selectedProfile
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) Dnz.Accent else Dnz.SurfaceHigh)
                        .dnzClickable { state.selectedProfile = i; state.version = p.version }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("${p.name}  •  ${p.version}", color = if (on) Dnz.OnAccent else Dnz.Text, fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(14.dp))

        if (sv.adding) {
            AddServerCard(state, profile)
            Spacer(Modifier.height(12.dp))
        }
        if (sv.servers.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(state.t("servers.empty"), color = Dnz.Muted, fontSize = 13.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sv.servers.forEach { ServerCard(state, profile, it) }
        }
    }
}

private suspend fun reload(state: LauncherState) {
    val sv = state.servers
    val profile = state.profiles[state.selectedProfile]
    sv.servers = withContext(Dispatchers.IO) { Servers.list(profile) }
    sv.loadedFor = profile.name
    sv.status.clear()
    pingAll(state)
}

private suspend fun pingAll(state: LauncherState) {
    val sv = state.servers
    for (server in sv.servers) {
        if (sv.status.containsKey(server.address)) continue
        sv.status[server.address] = null
        state.scope.launch {
            sv.status[server.address] = withContext(Dispatchers.IO) { runCatching { Servers.ping(server.address) } }
        }
    }
}

@Composable
private fun AddServerCard(state: LauncherState, profile: Profile) {
    val sv = state.servers
    Card(Modifier.fillMaxWidth()) {
        Text(state.t("servers.add_title"), color = Dnz.Text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(sv.newName, { sv.newName = it }, state.t("servers.name"), Modifier.weight(1f))
            Field(sv.newAddress, { sv.newAddress = it }, state.t("servers.address"), Modifier.weight(1.4f))
            DnzButton(state.t("servers.save")) {
                val address = sv.newAddress.trim()
                if (address.isEmpty()) return@DnzButton
                state.scope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { Servers.add(profile, sv.newName, address) }.isSuccess }
                    if (ok) {
                        state.toast = state.t("servers.added").replace("%s", sv.newName.ifBlank { address })
                        sv.newName = ""
                        sv.newAddress = ""
                        sv.adding = false
                        reload(state)
                    } else {
                        state.dialog = state.t("servers") to state.t("servers.save_failed")
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(Dnz.SurfaceHigh).border(1.dp, Dnz.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) Text(hint, color = Dnz.Muted, fontSize = 13.sp)
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = Dnz.Text, fontSize = 13.sp),
            cursorBrush = SolidColor(Dnz.Accent), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ServerCard(state: LauncherState, profile: Profile, server: SavedServer) {
    val result = state.servers.status[server.address]
    val status = result?.getOrNull()
    val offline = result?.isFailure == true
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = status?.icon ?: remember(server.iconBase64) { server.iconBase64?.let(Servers::decodeIcon) }
        if (icon != null) {
            Image(icon, null, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), filterQuality = FilterQuality.None)
        } else {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Dnz.AccentGradient), contentAlignment = Alignment.Center) {
                Icon(ServerIcon, null, tint = Dnz.OnAccent, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(server.name, color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Text(server.address, color = Dnz.Muted, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            when {
                status != null -> Text(status.motd, color = Dnz.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                offline -> Text(state.t("servers.offline"), color = Dnz.Danger, fontSize = 12.sp)
                else -> Text(state.t("servers.pinging"), color = Dnz.Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(14.dp))
        if (status != null) {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PingBars(status.pingMs)
                    Spacer(Modifier.width(6.dp))
                    Text("${status.pingMs} ms", color = Dnz.Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text("${status.online} / ${status.max}", color = Dnz.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(status.version, color = Dnz.Muted, fontSize = 10.sp, maxLines = 1)
            }
            Spacer(Modifier.width(14.dp))
        }
        DnzButton(state.t("servers.join")) { join(state, profile, server) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).dnzClickable {
                state.confirm = Confirm(
                    state.t("servers.remove.title"), state.t("servers.remove.body").replace("%s", server.name), state.t("lib.remove"),
                ) {
                    state.scope.launch {
                        withContext(Dispatchers.IO) { runCatching { Servers.remove(profile, server.address) } }
                        reload(state)
                    }
                }
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Delete, null, tint = Dnz.Muted, modifier = Modifier.size(18.dp)) }
    }
}

/** Four signal bars: green for a good ping, yellow, red for a bad one. */
@Composable
private fun PingBars(ms: Long) {
    val bars = when {
        ms < 80 -> 4
        ms < 150 -> 3
        ms < 300 -> 2
        else -> 1
    }
    val color = when (bars) {
        4, 3 -> Dnz.Success
        2 -> Color(0xFFFFD24A)
        else -> Dnz.Danger
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..4) {
            Box(Modifier.width(3.dp).height((3 + i * 3).dp).clip(RoundedCornerShape(1.dp)).background(if (i <= bars) color else Dnz.SurfaceHigh))
        }
    }
}

/** Leaves the "join this server" note for DNZ Client, then starts the game like PLAY. */
private fun join(state: LauncherState, profile: Profile, server: SavedServer) {
    if (state.busy || state.gameRunning) {
        state.dialog = state.t("servers") to state.t("servers.game_open")
        return
    }
    state.scope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { Servers.requestJoin(profile, server) }.isSuccess }
        if (!ok) return@launch
        state.toast = state.t("servers.joining").replace("%s", server.name)
        state.screen = Screen.Home
        play(state)
    }
}
