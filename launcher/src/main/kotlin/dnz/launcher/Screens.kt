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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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

private fun tr(state: LauncherState, turkish: String, english: String, german: String) = when (state.language) {
    Lang.TR -> turkish
    Lang.DE -> german
    Lang.EN -> english
}

@Composable
fun HomeScreen(state: LauncherState) {
    val profile = state.profiles[state.selectedProfile]
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 40.dp, top = 24.dp, bottom = 28.dp)) {
        // top right: search and the account
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Dnz.Surface).dnzClickable { state.screen = Screen.Mods },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Search, null, tint = Dnz.Muted, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Dnz.Surface).dnzClickable { state.screen = Screen.Account }
                    .padding(start = 6.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkinHead(state.currentAccount, 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(state.currentAccount?.name ?: state.t("account"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        MojangNotice(state)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth()) {
        // the player's skin as a 3D character at the right (or the pixel scene when that is off / there is no skin)
        val skinOwner = state.currentAccount?.uuid
        var skin by remember { mutableStateOf<LoadedSkin?>(null) }
        var skinLoaded by remember { mutableStateOf(false) }
        var skinVersion by remember { mutableStateOf(0) }
        var found by remember { mutableStateOf<FoundSkin?>(null) }
        LaunchedEffect(skinOwner, state.skinAnimation, skinVersion) { if (state.skinAnimation) { skin = loadSkin(skinOwner); skinLoaded = true } }
        val shownSkin = found?.skin ?: skin
        if (state.skinAnimation && !skinLoaded) {
            // still loading the skin: nothing yet (no flash of the pixel scene)
        } else if (state.skinAnimation && shownSkin != null) {
            // same 420 dp as before the skin search, so the profile cards below keep their size
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 60.dp).width(340.dp).height(420.dp)) {
                Box(Modifier.fillMaxWidth().height(320.dp).background(Brush.radialGradient(listOf(Dnz.Accent.copy(alpha = 0.22f), Color.Transparent))))
                // A new key per skin, so the character redraws with the searched skin
                key(shownSkin) { PlayerModel(shownSkin, animate = !state.gameRunning, modifier = Modifier.fillMaxWidth().height(if (found != null) 230.dp else 320.dp)) }
                SkinFinder(state, found, onFound = { found = it }, onApplied = { skinVersion++ }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
        } else
        // Pixel scene at the right, level with the profile name; its edges fade into the page
        Box(Modifier.align(Alignment.CenterEnd).width(460.dp).height(354.dp)) {
            Image(
                HomeScene, null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
            )
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.75f to Color.Transparent, 1f to Dnz.Background)))
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to Dnz.Background, 0.18f to Color.Transparent, 0.85f to Color.Transparent, 1f to Dnz.Background)))
        }
        Column {
        // Selected profile
        Text(
            tr(state, "SEÇİLİ PROFİL", "SELECTED PROFILE", "AUSGEWÄHLTES PROFIL"), color = Dnz.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Dnz.Accent.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(profile.name, color = Dnz.Text, fontSize = 72.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val mods = remember(profile) { GameLauncher.instanceDir(profile).resolve("mods").listFiles { f -> f.name.endsWith(".jar") }?.size ?: 0 }
            listOf(profile.version, Versions.loaderOf(profile).label, state.t("profiles.mods").replace("%s", mods.toString())).forEach {
                Text(
                    it, color = Dnz.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Dnz.Surface).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                state.gameRunning -> state.t("running")
                state.busy -> "${(state.progress * 100).toInt()}%"
                else -> tr(state, "OYNA", "PLAY", "SPIELEN")
            }
            Row(
                Modifier.width(196.dp).height(68.dp).clip(RoundedCornerShape(14.dp)).background(Dnz.Accent)
                    .dnzClickable { if (!state.busy && !state.gameRunning) requireSignIn(state) { state.scope.launch { play(state) } } },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!state.busy && !state.gameRunning) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Dnz.OnAccent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, color = Dnz.OnAccent, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
            Spacer(Modifier.width(12.dp))
            // the game's log in its own window; works while the game is open too
            Row(
                Modifier.height(68.dp).clip(RoundedCornerShape(14.dp)).background(Dnz.Surface)
                    .dnzClickable { state.logsProfile = profile }.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, null, tint = Dnz.Text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr(state, "Logları Aç", "Open Logs", "Logs öffnen"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        state.status?.let { status ->
            Spacer(Modifier.height(12.dp))
            Column(Modifier.width(420.dp)) {
                Text(status, color = Dnz.Muted, fontSize = 12.sp, maxLines = 1)
                if (state.busy) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Dnz.Accent, trackColor = Dnz.Surface,
                    )
                }
            }
        }
        }
        }
        Spacer(Modifier.weight(1f))
        // My profiles
        Text(tr(state, "Profillerim", "My profiles", "Meine Profile"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            state.profiles.take(4).forEachIndexed { i, p ->
                val selected = i == state.selectedProfile
                Column(
                    Modifier.weight(1f).height(130.dp).clip(RoundedCornerShape(14.dp)).background(Dnz.Surface)
                        .border(1.5.dp, if (selected) Dnz.Accent else Dnz.Border, RoundedCornerShape(14.dp))
                        .dnzClickable { state.selectedProfile = i; state.version = p.version }.padding(16.dp),
                ) {
                    Icon(Icons.Outlined.List, null, tint = if (selected) Dnz.Accent else Dnz.Muted, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.weight(1f))
                    Text(p.name, color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text("${Versions.loaderOf(p).label} · ${p.version}", color = Dnz.Muted, fontSize = 12.sp)
                }
            }
            val dash = Dnz.Border
            Box(
                Modifier.weight(1f).height(130.dp).clip(RoundedCornerShape(14.dp))
                    .drawBehind {
                        drawRoundRect(
                            dash, cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                            ),
                        )
                    }
                    .dnzClickable { requireSignIn(state) { state.editingProfile = -1 } },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Add, null, tint = Dnz.Muted, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(tr(state, "Yeni profil", "New profile", "Neues Profil"), color = Dnz.Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            // Always five slots wide, so a single profile card keeps its size
            repeat(4 - state.profiles.size.coerceAtMost(4)) { Spacer(Modifier.weight(1f)) }
        }
    }
    // "New profile" here opens the same window as on the Profiles page
    state.editingProfile?.let { ProfileEditor(state, it) }
}

/** Red notice until Mojang approves DNZ for Microsoft sign-in. ✕ hides it until the next start; "Don't show again" for good. */
@Composable
private fun MojangNotice(state: LauncherState) {
    if (MicrosoftAuth.approved || state.mojangNoticeHidden || state.mojangNoticeClosed) return
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
                    if (!state.busy && !state.gameRunning) requireSignIn(state) { state.scope.launch { play(state) } }
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
    // the Minecraft sign-in only lasts about a day: renew it quietly before starting
    if (account != null && !account.offline) {
        account = withContext(Dispatchers.IO) { runCatching { MicrosoftAuth.restore() }.getOrNull() } ?: account
        state.account = account
        // Shown from the last start but never renewed (no internet): the game can't start without a sign-in
        if (account.accessToken.isEmpty()) {
            if (MicrosoftAuth.hasSaved) {
                state.dialog = state.t("launch_error") to state.t("signin.no_internet")
                return
            }
            account = null // Microsoft no longer accepts the saved sign-in
            state.account = null
        }
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
    } catch (e: GameLauncher.AlreadyRunning) {
        state.dialog = state.t("launch_error") to state.t("already_running")
        state.status = null
    } catch (e: Exception) {
        state.dialog = state.t("launch_error") to (e.message ?: e.toString())
        state.status = null
    } finally {
        state.busy = false
        state.gameRunning = false
    }
}

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
private fun CloudCard(state: LauncherState) {
    var on by remember { mutableStateOf(Cloud.enabled) }
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.t("cloud.title"), color = Dnz.Text, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(state.t("cloud.caption"), color = Dnz.Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.width(20.dp))
            Switch(
                checked = on,
                onCheckedChange = { Cloud.enabled = it; on = Cloud.enabled },
                modifier = Modifier.handCursor(), colors = SwitchDefaults.colors(checkedThumbColor = Dnz.OnAccent, checkedTrackColor = Dnz.Accent),
            )
        }
    }
}

@Composable
fun SettingsScreen(state: LauncherState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(state.t("settings"))
        UpdateCard(state)
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
        CloudCard(state)
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
                LangChip(state, Lang.DE, "Deutsch")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Text(state.t("settings.theme"), color = Dnz.Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LauncherTheme.entries.forEach { theme -> ThemeChip(state, theme) }
            }
            Spacer(Modifier.height(12.dp))
            // A DNZ theme file (.dnztheme): launcher colors and picture, and the in-game DNZ menu (docs/dnz-theme.md)
            Row(verticalAlignment = Alignment.CenterVertically) {
                DnzButton(state.t("settings.theme_file")) {
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, state.t("settings.theme_file"), java.awt.FileDialog.LOAD)
                    dialog.setFilenameFilter { _, n -> n.lowercase().endsWith(".dnztheme") || n.lowercase().endsWith(".json") }
                    dialog.isVisible = true
                    val file = dialog.files.firstOrNull() ?: return@DnzButton
                    val colors = runCatching { ThemePack.install(file.readBytes()) }
                    colors.onSuccess { c ->
                        if (c != null) Dnz.useWorkshopTheme("file:" + file.name, c) else Dnz.pickAccent(null)
                        state.toast = state.t("settings.theme_applied")
                    }.onFailure { state.dialog = state.t("settings.theme_file") to (it.message ?: it.toString()) }
                }
                if (java.io.File(ThemePack.dir, "theme.json").exists()) {
                    Spacer(Modifier.width(10.dp))
                    SmallButton(state.t("settings.theme_remove")) {
                        ThemePack.clear()
                        Dnz.pickAccent(null)
                        Dnz.theme = LauncherTheme.Colorful
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Text(state.t("settings.accent"), color = Dnz.Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(state.t("settings.accent.caption"), color = Dnz.Muted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Dnz.ACCENTS.forEachIndexed { i, color ->
                    val selected = (Dnz.accent ?: Dnz.ACCENTS[0]) == color && Dnz.theme == LauncherTheme.Colorful
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(50))
                            .border(if (selected) 3.dp else 0.dp, if (selected) Dnz.Text else Color.Transparent, RoundedCornerShape(50))
                            .padding(if (selected) 5.dp else 0.dp).clip(RoundedCornerShape(50)).background(Color(color))
                            .dnzClickable { ThemePack.clear(); Dnz.pickAccent(if (i == 0) null else color); Dnz.theme = LauncherTheme.Colorful },
                    )
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.t("settings.character"), color = Dnz.Text, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(state.t("settings.character.caption"), color = Dnz.Muted, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Spacer(Modifier.width(20.dp))
                Switch(
                    checked = state.skinAnimation, onCheckedChange = { state.skinAnimation = it },
                    modifier = Modifier.handCursor(), colors = SwitchDefaults.colors(checkedThumbColor = Dnz.OnAccent, checkedTrackColor = Dnz.Accent),
                )
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
private fun UpdateCard(state: LauncherState) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val status = Updater.status
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.t("update.title"), color = Dnz.Text, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                val line = when {
                    Updater.ready != null -> state.t("update.ready").format(Updater.ready)
                    status == Updater.Status.Checking -> state.t("update.checking")
                    status == Updater.Status.UpToDate -> state.t("update.latest")
                    status == Updater.Status.Failed -> state.t("update.failed")
                    status == Updater.Status.NotInstalled -> state.t("update.dev")
                    else -> state.t("update.caption")
                }
                Text(state.t("update.version").format(Updater.current ?: "dev") + "  •  " + line, color = Dnz.Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.width(20.dp))
            when {
                Updater.ready != null -> DnzButton(state.t("update.now"), onClick = { if (Updater.restartNow()) kotlin.system.exitProcess(0) })
                status == Updater.Status.Checking -> CircularProgressIndicator(Modifier.size(22.dp), color = Dnz.Accent, strokeWidth = 2.5.dp)
                else -> DnzButton(state.t("update.check"), onClick = { scope.launch { withContext(Dispatchers.IO) { Updater.check() } } })
            }
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

@Composable
private fun ThemeChip(state: LauncherState, theme: LauncherTheme) {
    val selected = Dnz.theme == theme && (Dnz.custom == null || Dnz.accent != null)
    val preview = if (theme == LauncherTheme.Mono) listOf(Color(0xFF050505), Color(0xFFF2F2F2)) else listOf(Color(0xFFFF8A3D), Color(0xFFFF6A1A))
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Dnz.Accent else Dnz.SurfaceHigh)
            .dnzClickable { ThemePack.clear(); Dnz.pickAccent(null); Dnz.theme = theme }.padding(horizontal = 14.dp, vertical = 10.dp),
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
                    // until Mojang approves DNZ Launcher, sign-in can't work: show the same notice as on Home
                    if (MicrosoftAuth.approved && MicrosoftAuth.ready) signInWithMicrosoft(state)
                    else state.dialog = state.t("sign_in") to state.t("mojang_notice")
                }
                // Developer test account: only when the launcher runs from the source code (gradlew run),
                // never in a released build (installer, portable, Mac app).
                if (DevBuild.fromSource) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Dnz.SurfaceHigh)
                            .dnzClickable { state.account = Account.offline("DNZ_Test") }.padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(state.t("test_account"), color = Dnz.Text, fontWeight = FontWeight.SemiBold) }
                    Text(state.t("test_account.info"), color = Dnz.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
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
                if (state.account?.offline == false && MicrosoftAuth.approved && MicrosoftAuth.ready) {
                    Spacer(Modifier.height(10.dp))
                    DnzButton("+ " + state.t("acc.add_another"), Modifier.fillMaxWidth()) { signInWithMicrosoft(state) }
                }
            }
            val others = state.dnzOthers
            LaunchedEffect(state.account) { state.refreshAccounts() }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(state.t("acc.others"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                for (other in others) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Dnz.SurfaceHigh).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkinHead(other, 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(other.name, color = Dnz.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        SmallButton(state.t("acc.use"), accent = true) { state.scope.launch { state.switchAccount(other) } }
                        Spacer(Modifier.width(6.dp))
                        SmallButton(state.t("acc.remove")) {
                            MicrosoftAuth.removeOther(other.localId!!.removePrefix("dnz:"))
                            state.scope.launch { state.refreshAccounts() }
                        }
                    }
                }
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
