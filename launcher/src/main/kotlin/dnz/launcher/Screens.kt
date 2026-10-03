package dnz.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ------------------------------------------------------------------ Home

@Composable
fun HomeScreen(state: LauncherState) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Hero(state)
        Spacer(Modifier.height(24.dp))
        Text(state.t("news"), color = Dnz.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        MojangNotice(state)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NewsCard(state.t("news1.title"), state.t("news1.body"), Dnz.Accent, Modifier.weight(1f))
            NewsCard(state.t("news2.title"), state.t("news2.body"), Dnz.Accent2, Modifier.weight(1f))
            NewsCard(state.t("news3.title"), state.t("news3.body"), Dnz.Accent3, Modifier.weight(1f))
        }
    }
}

/** Red notice until Mojang approves DNZ for Microsoft sign-in. ✕ hides it until the next start; "Don't show again" for good. */
@Composable
private fun MojangNotice(state: LauncherState) {
    if (state.mojangNoticeHidden || state.mojangNoticeClosed) return
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(Dnz.Danger.copy(alpha = 0.14f))
            .border(1.dp, Dnz.Danger.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, tint = Dnz.Danger, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(state.t("mojang_notice"), color = Dnz.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(
            state.t("dont_show_again"), color = Dnz.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).dnzClickable { state.mojangNoticeHidden = true }.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).dnzClickable { state.mojangNoticeClosed = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, state.t("cancel"), tint = Dnz.Muted, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun Hero(state: LauncherState) {
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)).background(Dnz.HeroGradient)) {
        PixelMosaic(Modifier.matchParentSize())
        Column(Modifier.align(Alignment.CenterStart).padding(start = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(DnzLogo, null, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)), colorFilter = Dnz.LogoFilter)
                Spacer(Modifier.width(16.dp))
                Text(state.t("hero.title"), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            Text(state.t("hero.subtitle"), color = Color(0xCCFFFFFF), fontSize = 15.sp)
        }
        // Play area bottom-right
        val scope = rememberCoroutineScope()
        Column(Modifier.align(Alignment.BottomEnd).padding(24.dp), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VersionPicker(state)
                Spacer(Modifier.width(12.dp))
                val label = when {
                    state.gameRunning -> state.t("running")
                    state.busy -> "${(state.progress * 100).toInt()}%"
                    else -> state.t("play")
                }
                DnzButton(label, big = true) {
                    if (!state.busy && !state.gameRunning) scope.launch { play(state) }
                }
            }
            state.status?.let { status ->
                Spacer(Modifier.height(10.dp))
                Column(Modifier.width(360.dp)) {
                    Text(status, color = Color.White, fontSize = 12.sp, maxLines = 1)
                    if (state.busy) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Dnz.Accent, trackColor = Color(0x33FFFFFF),
                        )
                    }
                }
            }
        }
    }
}

/** Installs everything that is missing, then starts the game. */
internal suspend fun play(state: LauncherState) {
    val profile = state.profiles[state.selectedProfile]
    if (state.launchMode == LaunchMode.Official) {
        state.busy = true
        try {
            OfficialLauncher.play(profile, state.ramFor(profile)) { text, value ->
                state.status = text
                state.progress = value
            }
            state.status = state.t("official.done").replace("%s", profile.name)
        } catch (e: Exception) {
            state.dialog = state.t("launch_error") to (e.message ?: e.toString())
            state.status = null
        } finally {
            state.busy = false
        }
        return
    }

    var account = state.account
    // The Minecraft sign-in only lasts about a day: renew it quietly before starting.
    if (account != null && !account.offline) {
        account = withContext(Dispatchers.IO) { runCatching { MicrosoftAuth.restore() }.getOrNull() } ?: account
        state.account = account
    }
    if (account == null) {
        state.dialog = state.t("need_account.title") to state.t("need_account.body")
        state.screen = Screen.Account
        return
    }
    state.busy = true
    try {
        val process = GameLauncher.play(state.profiles[state.selectedProfile], account, state.ramFor(state.profiles[state.selectedProfile])) { text, value ->
            state.status = text
            state.progress = value
        }
        state.busy = false
        state.gameRunning = true
        state.status = state.t("running")
        withContext(Dispatchers.IO) { process.waitFor() }
        state.status = null
    } catch (e: Exception) {
        state.dialog = state.t("launch_error") to (e.message ?: e.toString())
        state.status = null
    } finally {
        state.busy = false
        state.gameRunning = false
    }
}

/** Soft pixel squares like a Minecraft-style banner. */
@Composable
private fun PixelMosaic(modifier: Modifier) {
    val squares = remember {
        val r = Random(7)
        List(46) { Triple(r.nextFloat(), r.nextFloat(), 0.03f + r.nextFloat() * 0.10f) }
    }
    Canvas(modifier) {
        val cell = size.height / 6
        squares.forEach { (x, y, a) ->
            val px = (x * size.width / cell).toInt() * cell
            val py = (y * size.height / cell).toInt() * cell
            drawRect(Color.White.copy(alpha = a), Offset(px, py), Size(cell, cell))
        }
        drawRect(Brush.horizontalGradient(listOf(Color(0x66000000), Color.Transparent)), size = size)
    }
}

@Composable
private fun VersionPicker(state: LauncherState) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x66000000)).dnzClickable { open = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(state.profiles[state.selectedProfile].name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${state.t("version")} ${state.version}", color = Color(0xB3FFFFFF), fontSize = 11.sp)
            }
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Filled.ArrowDropDown, null, tint = Color.White)
        }
        DnzDropdown(open, onDismiss = { open = false }) {
            state.profiles.forEachIndexed { i, p ->
                DropdownMenuItem(
                    modifier = Modifier.menuItem(),
                    text = { Text("${p.name}  •  ${p.version}", color = Dnz.Text) },
                    onClick = { state.selectedProfile = i; state.version = p.version; open = false },
                )
            }
        }
    }
}

@Composable
private fun NewsCard(title: String, body: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(16.dp))) {
        Box(Modifier.fillMaxWidth().height(90.dp).background(Brush.linearGradient(listOf(color.copy(alpha = 0.85f), Dnz.Surface))))
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(body, color = Dnz.Muted, fontSize = 12.sp)
        }
    }
}

// ------------------------------------------------------------------ Profiles

// ------------------------------------------------------------------ Settings

@Composable
fun SettingsScreen(state: LauncherState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(state.t("settings"))
        AutoCard(state)
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.t("startup.title"), color = Dnz.Text, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(state.t("startup.caption"), color = Dnz.Muted, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Spacer(Modifier.width(20.dp))
                Switch(
                    checked = state.startWithWindows,
                    onCheckedChange = { on ->
                        if (Autostart.set(on)) state.startWithWindows = Autostart.isEnabled()
                        else state.dialog = state.t("startup.title") to state.t("startup.failed")
                    },
                    modifier = Modifier.handCursor(), colors = SwitchDefaults.colors(checkedThumbColor = Dnz.OnAccent, checkedTrackColor = Dnz.Accent),
                )
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.t("settings.ram"), color = Dnz.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${state.ramGb.toInt()} GB", color = Dnz.Accent, fontWeight = FontWeight.Black)
            }
            Slider(
                state.ramGb, { state.ramGb = it }, valueRange = 2f..16f, steps = 13,
                modifier = Modifier.handCursor(), colors = SliderDefaults.colors(thumbColor = Dnz.Accent, activeTrackColor = Dnz.Accent, inactiveTrackColor = Dnz.SurfaceHigh),
            )
            Text(state.t("settings.ram.hint"), color = Dnz.Muted, fontSize = 12.sp)
        }
        Card(Modifier.fillMaxWidth()) {
            Text(state.t("settings.mode"), color = Dnz.Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            ModeOption(state, LaunchMode.Official, "mode.official", "mode.official.info")
            Spacer(Modifier.height(8.dp))
            ModeOption(state, LaunchMode.Direct, "mode.direct", "mode.direct.info")
        }
        Card(Modifier.fillMaxWidth()) {
            Text(state.t("settings.language"), color = Dnz.Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LangChip(state, Lang.TR, "Türkçe")
                LangChip(state, Lang.EN, "English")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Text(state.t("settings.theme"), color = Dnz.Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LauncherTheme.entries.forEach { theme -> ThemeChip(state, theme) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Text(state.t("settings.folder"), color = Dnz.Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(GameLauncher.root.absolutePath, color = Dnz.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ModeOption(state: LauncherState, mode: LaunchMode, titleKey: String, infoKey: String) {
    val selected = state.launchMode == mode
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Dnz.SurfaceHigh)
            .border(1.dp, if (selected) Dnz.Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .dnzClickable { state.launchMode = mode }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = { state.launchMode = mode }, modifier = Modifier.handCursor(), colors = RadioButtonDefaults.colors(selectedColor = Dnz.Accent))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(state.t(titleKey), color = Dnz.Text, fontWeight = FontWeight.SemiBold)
            Text(state.t(infoKey), color = Dnz.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LangChip(state: LauncherState, lang: Lang, label: String) {
    val selected = state.language == lang
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Dnz.Accent else Dnz.SurfaceHigh)
            .dnzClickable { state.language = lang }.padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(label, color = if (selected) Dnz.OnAccent else Dnz.Text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

/** A theme button with a small preview of its colors. */
@Composable
private fun ThemeChip(state: LauncherState, theme: LauncherTheme) {
    val selected = Dnz.theme == theme
    val preview = if (theme == LauncherTheme.Mono) listOf(Color(0xFF050505), Color(0xFFF2F2F2)) else listOf(Color(0xFF4FA3FF), Color(0xFF7B5CFF))
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Dnz.Accent else Dnz.SurfaceHigh)
            .dnzClickable { Dnz.theme = theme }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(Brush.linearGradient(preview))
                .border(1.dp, Color(0x55808080), RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(8.dp))
        Text(state.t(theme.key), color = if (selected) Dnz.OnAccent else Dnz.Text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ------------------------------------------------------------------ Account

@Composable
fun AccountScreen(state: LauncherState) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        ScreenTitle(state.t("account"))
        Card(Modifier.widthIn(max = 620.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicrosoftLogo()
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(state.t("account.title"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(state.accountName ?: state.t("not_signed"), color = Dnz.Muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            if (state.launchMode == LaunchMode.Official) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Dnz.Success.copy(alpha = 0.10f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Dnz.Success, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.t("account.official"), color = Dnz.Text, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (state.account == null) {
                DnzButton(state.t("sign_in"), Modifier.fillMaxWidth()) {
                    // Until Mojang approves DNZ Launcher, sign-in can't work: show the same notice as on Home.
                    if (MicrosoftAuth.approved && MicrosoftAuth.ready) signInWithMicrosoft(state)
                    else state.dialog = state.t("sign_in") to state.t("mojang_notice")
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Dnz.SurfaceHigh)
                        .dnzClickable {
                            if (state.account?.offline == false) MicrosoftAuth.signOut()
                            state.account = null
                        }.padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(state.t("sign_out"), color = Dnz.Danger, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(18.dp))
            listOf("account.safe1", "account.safe2", "account.safe3").forEach {
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = Dnz.Success, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.t(it), color = Dnz.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MicrosoftLogo() {
    Canvas(Modifier.size(36.dp)) {
        val s = size.width / 2 - 1
        drawRect(Color(0xFFF25022), Offset(0f, 0f), Size(s, s))
        drawRect(Color(0xFF7FBA00), Offset(s + 2, 0f), Size(s, s))
        drawRect(Color(0xFF00A4EF), Offset(0f, s + 2), Size(s, s))
        drawRect(Color(0xFFFFB900), Offset(s + 2, s + 2), Size(s, s))
    }
}
