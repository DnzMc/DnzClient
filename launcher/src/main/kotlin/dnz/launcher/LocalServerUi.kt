@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dnz.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/** Servers → "+ Make": play with friends. */
internal enum class HostStep { Choose, World, Server }

/** Make window state (kept in LauncherState through ServersState). */
class HostState {
    internal var step by mutableStateOf<HostStep?>(null)

    var ipProtection by mutableStateOf(true)
    var starting by mutableStateOf<String?>(null)
    var consoleOpen by mutableStateOf(false)
    var saved by mutableStateOf<List<LocalServerConfig>>(emptyList())

    fun open() {
        step = HostStep.Choose
    }
}

private fun LauncherState.s(tr: String, en: String, de: String) = when (language) {
    Lang.TR -> tr
    Lang.EN -> en
    Lang.DE -> de
}

@Composable
fun MakeServerButton(state: LauncherState) {
    DnzButton("+ " + state.s("Oluştur", "Make", "Erstellen")) { state.host.open() }
}

/** Make windows: the choice, then opening a world or the server form. */
@Composable
fun HostDialogs(state: LauncherState) {
    val host = state.host
    when (host.step) {
        HostStep.Choose -> ChooseDialog(state)
        HostStep.World -> OpenWorldDialog(state)
        HostStep.Server -> ServerFormDialog(state)
        null -> {}
    }
}

@Composable
private fun ChooseDialog(state: LauncherState) {
    val host = state.host
    AlertDialog(
        onDismissRequest = { host.step = null },
        containerColor = Dnz.SurfaceHigh,
        title = { Text(state.s("Arkadaşlarınla oyna", "Play with friends", "Mit Freunden spielen"), color = Dnz.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceRow(
                    Icons.Filled.PlayArrow,
                    state.s("Tek oyunculu dünyanı aç", "Open your singleplayer world", "Deine Einzelspielerwelt öffnen"),
                    state.s("Oyunda bir dünyaya gir, arkadaşların oraya katılsın. En kolay yol; sen oyundayken açık kalır.",
                        "Join a world in the game and let your friends join it. The easiest way; open while you play.",
                        "Betritt eine Welt und lass deine Freunde beitreten. Am einfachsten; offen, solange du spielst."),
                ) { host.step = HostStep.World }
                ChoiceRow(
                    Icons.Filled.Home,
                    state.s("Sunucuyu bu bilgisayarda çalıştır", "Run the server locally", "Server lokal starten"),
                    state.s("Bu bilgisayarda gerçek bir Minecraft sunucusu açılır. Ayarlarını sen seçersin; sen oynamasan da açık kalır.",
                        "A real Minecraft server runs on this computer. You choose its settings; it stays up even when you don't play.",
                        "Ein echter Minecraft-Server läuft auf diesem PC. Du wählst die Einstellungen; er läuft auch ohne dich."),
                ) { host.step = HostStep.Server }
                ProtectionSwitch(state)
            }
        },
        confirmButton = {},
        dismissButton = { TextAction(state.t("cancel")) { host.step = null } },
    )
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(14.dp))
            .dnzClickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Dnz.AccentGradient), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Dnz.OnAccent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(body, color = Dnz.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProtectionSwitch(state: LauncherState) {
    val host = state.host
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, null, tint = if (host.ipProtection) Dnz.Success else Dnz.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(state.s("IP koruması", "IP protection", "IP-Schutz"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                if (host.ipProtection) state.s(
                    "Açık: arkadaşların sana özel bir adresle bağlanır (ör. abc.e4mc.link). Gerçek IP adresin hiç görünmez, kimse evinin yerini bulamaz.",
                    "On: friends connect with a special address (e.g. abc.e4mc.link). Your real IP address is never shown, so nobody can find where you live.",
                    "An: Freunde verbinden sich über eine eigene Adresse (z. B. abc.e4mc.link). Deine echte IP wird nie gezeigt.")
                else state.s(
                    "Kapalı: sadece aynı Wi-Fi'daki kişiler bağlanabilir. Dışarıdan bağlanmaları için gerçek IP adresini vermen gerekir, bu da yaklaşık konumunu gösterir.",
                    "Off: only people on the same Wi-Fi can join. For others you would have to share your real IP address, which shows roughly where you are.",
                    "Aus: Nur Leute im selben WLAN können beitreten. Für andere müsstest du deine echte IP teilen, die deinen ungefähren Ort zeigt."),
                color = Dnz.Muted, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(host.ipProtection, { host.ipProtection = it }, modifier = Modifier.handCursor(),
            colors = SwitchDefaults.colors(checkedThumbColor = Dnz.OnAccent, checkedTrackColor = Dnz.Accent))
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).dnzClickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(label, color = Dnz.Muted, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- open a single player world

@Composable
private fun OpenWorldDialog(state: LauncherState) {
    val host = state.host
    val profile = state.profiles[state.selectedProfile]
    val fabric = Versions.loaderOf(profile) == Loader.Fabric
    val protect = host.ipProtection && fabric
    AlertDialog(
        onDismissRequest = { host.step = null },
        containerColor = Dnz.SurfaceHigh,
        title = { Text(state.s("Tek oyunculu dünyanı aç", "Open your singleplayer world", "Einzelspielerwelt öffnen"), color = Dnz.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.s("Profil: ", "Profile: ", "Profil: ") + "${profile.name} • ${profile.version}", color = Dnz.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Step(1, state.s("Oyunu aç ve dünyana gir.", "Start the game and open your world.", "Starte das Spiel und öffne deine Welt."))
                Step(2, state.s("ESC'ye bas → \"LAN'a Aç\" (Open to LAN) → \"LAN Dünyasını Başlat\".", "Press ESC → \"Open to LAN\" → \"Start LAN World\".", "Drücke ESC → \"Im LAN öffnen\" → \"LAN-Welt starten\"."))
                Step(3, if (protect) state.s("Sohbette abc.e4mc.link gibi bir adres çıkar. Arkadaşlarına sadece bu adresi ver.",
                    "An address like abc.e4mc.link appears in chat. Give your friends only that address.",
                    "Im Chat erscheint eine Adresse wie abc.e4mc.link. Gib deinen Freunden nur diese.")
                else state.s("Aynı Wi-Fi'daki arkadaşların oyunun Çok Oyunculu listesinde dünyanı görür.",
                    "Friends on the same Wi-Fi see your world in the game's Multiplayer list.",
                    "Freunde im selben WLAN sehen deine Welt in der Mehrspielerliste."))
                if (host.ipProtection && !fabric) {
                    Text(state.s("Bu profil Fabric değil, IP koruması kullanılamıyor: sadece aynı Wi-Fi'dakiler katılabilir.",
                        "This profile isn't Fabric, so IP protection can't be used: only people on the same Wi-Fi can join.",
                        "Dieses Profil ist nicht Fabric, IP-Schutz geht nicht: nur Leute im selben WLAN."), color = Color(0xFFFFB74D), fontSize = 12.sp)
                }
                Text(state.s("Dünya senin bilgisayarında çalışır; oyunu kapatınca arkadaşların da çıkar.",
                    "The world runs on your computer; when you close the game, your friends leave too.",
                    "Die Welt läuft auf deinem PC; schließt du das Spiel, fliegen deine Freunde raus."), color = Dnz.Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            DnzButton(state.s("Oyunu aç", "Start the game", "Spiel starten")) {
                host.step = null
                openWorld(state, profile, protect)
            }
        },
        dismissButton = { TextAction(state.s("Geri", "Back", "Zurück")) { host.step = HostStep.Choose } },
    )
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Dnz.Accent), contentAlignment = Alignment.Center) {
            Text("$n", color = Dnz.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Dnz.Text, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun openWorld(state: LauncherState, profile: Profile, protect: Boolean) {
    requireSignIn(state) {
        state.scope.launch {
            if (Versions.loaderOf(profile) == Loader.Fabric) {
                val mods = File(GameLauncher.instanceDir(profile), "mods").apply { mkdirs() }
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        if (protect) {
                            GameLauncher.installModrinthMod("e4mc", profile.version, mods)
                        } else {
                            val marker = File(mods, ".dnz-e4mc")
                            if (marker.exists()) {
                                File(mods, marker.readText().trim()).delete()
                                marker.delete()
                            }
                        }
                    }
                }
                if (ok.isFailure) {
                    state.dialog = state.s("IP koruması hazırlanamadı", "IP protection couldn't be set up", "IP-Schutz nicht bereit") to (ok.exceptionOrNull()?.message ?: "")
                    return@launch
                }
            }
            state.screen = Screen.Home
            play(state)
        }
    }
}

// ---------------------------------------------------------------- run a server on this computer

@Composable
private fun ServerFormDialog(state: LauncherState) {
    val host = state.host
    val profile = state.profiles[state.selectedProfile]
    var name by remember { mutableStateOf(state.account?.name?.let { "$it's server" } ?: "DNZ Server") }
    var version by remember { mutableStateOf(profile.version) }
    var ram by remember { mutableStateOf(2) }
    var gamemode by remember { mutableStateOf("survival") }
    var difficulty by remember { mutableStateOf("normal") }
    var maxPlayers by remember { mutableStateOf(10) }
    var pvp by remember { mutableStateOf(true) }
    var whitelist by remember { mutableStateOf(false) }
    var eula by remember { mutableStateOf(false) }
    var eulaWarn by remember { mutableStateOf(false) }
    val protectable = LocalServers.canProtect(version)
    AlertDialog(
        onDismissRequest = { host.step = null },
        containerColor = Dnz.SurfaceHigh,
        title = { Text(state.s("Sunucuyu bu bilgisayarda çalıştır", "Run the server locally", "Server lokal starten"), color = Dnz.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.width(560.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormRow(state.s("İsim", "Name", "Name")) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        BasicTextField(name, { name = it.take(40) }, singleLine = true, textStyle = TextStyle(color = Dnz.Text, fontSize = 13.sp), cursorBrush = SolidColor(Dnz.Accent), modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(Modifier.dnzClickable(highlight = false) { eula = !eula; eulaWarn = false }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(eula, { eula = it; eulaWarn = false }, modifier = Modifier.handCursor(),
                        colors = CheckboxDefaults.colors(checkedColor = Dnz.Accent, uncheckedColor = Dnz.Muted, checkmarkColor = Dnz.OnAccent))
                    Text(state.s("Minecraft EULA'yı kabul ediyorum", "I accept the Minecraft EULA", "Ich akzeptiere die Minecraft-EULA"), color = Dnz.Text, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("aka.ms/MinecraftEULA ↗", color = Dnz.Accent, fontSize = 12.sp,
                        modifier = Modifier.dnzClickable(highlight = false) { openInBrowser("https://aka.ms/MinecraftEULA") })
                }
                if (eulaWarn) {
                    Text(state.s("Sunucuyu başlatmak için önce Minecraft EULA'yı kabul et (yukarıdaki kutu).", "Accept the Minecraft EULA first (the box above) to start the server.",
                        "Akzeptiere zuerst die Minecraft-EULA (Kästchen oben)."), color = Dnz.Danger, fontSize = 12.sp)
                }
                FormRow(state.s("Sürüm", "Version", "Version")) {
                    Chips(state.versions, version) { version = it }
                }
                FormRow(state.s("Oyun modu", "Game mode", "Spielmodus")) {
                    Chips(listOf("survival", "creative", "adventure"), gamemode, label = { mode ->
                        when (mode) { "survival" -> state.s("Hayatta kalma", "Survival", "Überleben"); "creative" -> state.s("Yaratıcı", "Creative", "Kreativ"); else -> state.s("Macera", "Adventure", "Abenteuer") }
                    }) { gamemode = it }
                }
                FormRow(state.s("Zorluk", "Difficulty", "Schwierigkeit")) {
                    Chips(listOf("peaceful", "easy", "normal", "hard"), difficulty, label = { d ->
                        when (d) { "peaceful" -> state.s("Barışçıl", "Peaceful", "Friedlich"); "easy" -> state.s("Kolay", "Easy", "Leicht"); "normal" -> state.s("Normal", "Normal", "Normal"); else -> state.s("Zor", "Hard", "Schwer") }
                    }) { difficulty = it }
                }
                FormRow(state.s("Oyuncu", "Players", "Spieler") + " ($maxPlayers)") {
                    Slider(maxPlayers.toFloat(), { maxPlayers = it.toInt() }, valueRange = 2f..20f, steps = 17, modifier = Modifier.handCursor(),
                        colors = SliderDefaults.colors(thumbColor = Dnz.Accent, activeTrackColor = Dnz.Accent, inactiveTrackColor = Dnz.Surface))
                }
                FormRow("RAM ($ram GB)") {
                    Slider(ram.toFloat(), { ram = it.toInt() }, valueRange = 1f..8f, steps = 6, modifier = Modifier.handCursor(),
                        colors = SliderDefaults.colors(thumbColor = Dnz.Accent, activeTrackColor = Dnz.Accent, inactiveTrackColor = Dnz.Surface))
                }
                Toggle(state.s("PvP açık", "PvP on", "PvP an"), pvp) { pvp = it }
                Toggle(state.s("Beyaz liste (sadece eklediklerin girebilir)", "Whitelist (only players you add can join)", "Whitelist (nur hinzugefügte Spieler)"), whitelist) { whitelist = it }
                ProtectionSwitch(state)
                if (host.ipProtection && !protectable) {
                    Text(state.s("$version sürümünde IP koruması yok: sunucuya sadece aynı Wi-Fi'dakiler girebilir.",
                        "No IP protection on $version: only people on the same Wi-Fi can join.",
                        "Kein IP-Schutz für $version: nur Leute im selben WLAN."), color = Color(0xFFFFB74D), fontSize = 12.sp)
                }
                Text(state.s("Sunucu bu bilgisayarda çalışır. Bilgisayarı ya da DNZ Launcher'ı kapatırsan sunucu da kapanır.",
                    "The server runs on this computer. If you turn off the computer or close DNZ Launcher, the server stops too.",
                    "Der Server läuft auf diesem PC. Schaltest du ihn aus oder schließt DNZ Launcher, stoppt der Server."), color = Dnz.Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            DnzButton(state.s("Oluştur ve başlat", "Create and start", "Erstellen und starten")) {
                if (!eula) {
                    eulaWarn = true
                } else {
                    val config = LocalServerConfig(name.trim().ifBlank { "DNZ Server" }, version, LocalServers.freeFolder(name.trim().ifBlank { "DNZ Server" }), ram, gamemode, difficulty, maxPlayers, pvp, whitelist,
                        25565, host.ipProtection && protectable)
                    host.step = null
                    startServer(state, config)
                }
            }
        },
        dismissButton = { TextAction(state.s("Geri", "Back", "Zurück")) { host.step = HostStep.Choose } },
    )
}

@Composable
private fun FormRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, color = Dnz.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Chips(items: List<String>, selected: String, label: (String) -> String = { it }, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (item in items) {
            val on = item == selected
            Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(if (on) Dnz.Accent else Dnz.Surface).border(1.dp, if (on) Dnz.Accent else Dnz.Border, RoundedCornerShape(9.dp))
                    .dnzClickable { onPick(item) }.padding(horizontal = 12.dp, vertical = 7.dp),
            ) { Text(label(item), color = if (on) Dnz.OnAccent else Dnz.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Dnz.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(value, onChange, modifier = Modifier.handCursor(), colors = SwitchDefaults.colors(checkedThumbColor = Dnz.OnAccent, checkedTrackColor = Dnz.Accent))
    }
}

private fun startServer(state: LauncherState, config: LocalServerConfig) {
    val host = state.host
    if (LocalServers.running?.stopped == false) {
        state.dialog = state.s("Bir sunucu zaten açık", "A server is already running", "Ein Server läuft schon") to
            state.s("Yenisini başlatmadan önce açık olanı durdur.", "Stop the running one before starting another.", "Stoppe ihn zuerst.")
        return
    }
    host.starting = state.s("Hazırlanıyor...", "Getting ready...", "Wird vorbereitet...")
    state.scope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching { LocalServers.start(config, state.account) { text -> host.starting = text } }
        }
        host.starting = null
        host.saved = withContext(Dispatchers.IO) { LocalServers.list() }
        result.onFailure { state.dialog = state.s("Sunucu başlatılamadı", "The server couldn't start", "Server konnte nicht starten") to (it.message ?: it.toString()) }
    }
}

// ---------------------------------------------------------------- the local servers on the Servers page

@Composable
fun LocalServersSection(state: LauncherState) {
    val host = state.host
    LaunchedEffect(Unit) { host.saved = withContext(Dispatchers.IO) { LocalServers.list() } }
    val running = LocalServers.running?.takeIf { !it.stopped }
    host.starting?.let { text ->
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Dnz.Accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(state.s("Sunucu hazırlanıyor: ", "Preparing the server: ", "Server wird vorbereitet: ") + text, color = Dnz.Text, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (running != null) {
        RunningCard(state, running)
        Spacer(Modifier.height(12.dp))
    }
    val others = host.saved.filter { it.folder != running?.config?.folder }
    if (others.isNotEmpty()) {
        Text(state.s("Bu bilgisayardaki sunucuların", "Your servers on this computer", "Deine Server auf diesem PC"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (config in others) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Home, null, tint = Dnz.Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(config.name, color = Dnz.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${config.version} • " + (if (config.ipProtection) state.s("IP korumalı", "IP protected", "IP-geschützt") else state.s("Sadece yerel ağ", "Home network only", "Nur Heimnetz")),
                            color = Dnz.Muted, fontSize = 11.sp)
                    }
                    SmallButton(state.s("Başlat", "Start", "Starten"), accent = true) { startServer(state, config.copy(ipProtection = config.ipProtection && LocalServers.canProtect(config.version))) }
                    Spacer(Modifier.width(6.dp))
                    SmallButton(state.s("Klasör", "Folder", "Ordner")) { runCatching { java.awt.Desktop.getDesktop().open(LocalServers.folder(config)) } }
                    Spacer(Modifier.width(6.dp))
                    SmallButton(state.s("Sil", "Delete", "Löschen")) {
                        state.confirm = Confirm(
                            state.s("\"${config.name}\" silinsin mi?", "Delete \"${config.name}\"?", "\"${config.name}\" löschen?"),
                            state.s("Sunucunun dünyası ve bütün dosyaları silinir.", "The server's world and all its files are deleted.", "Welt und Dateien des Servers werden gelöscht."),
                            state.s("Sil", "Delete", "Löschen"),
                        ) {
                            state.scope.launch {
                                withContext(Dispatchers.IO) { LocalServers.delete(config) }
                                host.saved = withContext(Dispatchers.IO) { LocalServers.list() }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RunningCard(state: LauncherState, server: RunningServer) {
    val host = state.host
    val config = server.config
    val lan = remember { LocalServers.lanAddress() }
    val portSuffix = if (config.port == 25565) "" else ":${config.port}"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Dnz.Surface)
            .border(1.dp, (if (server.ready) Dnz.Success else Dnz.Accent).copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (server.ready) Dnz.Success else Color(0xFFFFB74D)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(config.name, color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    (if (server.ready) state.s("Açık", "Online", "Online") else state.s("Başlıyor...", "Starting...", "Startet...")) + " • ${config.version}",
                    color = Dnz.Muted, fontSize = 12.sp,
                )
            }
            SmallButton(state.s("Katıl", "Join", "Beitreten"), accent = true) {
                val profile = state.profiles[state.selectedProfile]
                state.scope.launch {
                    withContext(Dispatchers.IO) { runCatching { if (Servers.list(profile).none { it.address == "localhost$portSuffix" }) Servers.add(profile, config.name, "localhost$portSuffix") } }
                    join(state, profile, SavedServer(config.name, "localhost$portSuffix", null))
                }
            }
            Spacer(Modifier.width(6.dp))
            SmallButton(state.s("Konsol", "Console", "Konsole")) { host.consoleOpen = !host.consoleOpen }
            Spacer(Modifier.width(6.dp))
            SmallButton(state.s("Durdur", "Stop", "Stoppen")) { LocalServers.stop() }
        }
        Spacer(Modifier.height(14.dp))
        if (config.ipProtection) {
            AddressRow(
                state,
                state.s("Arkadaşlarına bu adresi ver", "Give your friends this address", "Gib deinen Freunden diese Adresse"),
                server.domain ?: state.s("adres hazırlanıyor...", "getting the address...", "Adresse wird erstellt..."),
                copy = server.domain,
                good = true,
            )
            Text(state.s("IP koruması açık: arkadaşların senin gerçek IP adresini görmez.", "IP protection is on: your friends never see your real IP address.",
                "IP-Schutz an: Freunde sehen deine echte IP nie."), color = Dnz.Success, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
        if (lan != null) {
            AddressRow(state, state.s("Aynı Wi-Fi'dakiler için", "For people on the same Wi-Fi", "Für Leute im selben WLAN"), "$lan$portSuffix", copy = "$lan$portSuffix", good = false)
        }
        AddressRow(state, state.s("Bu bilgisayardan sen", "You, on this computer", "Du, auf diesem PC"), "localhost$portSuffix", copy = null, good = false)
        if (!config.ipProtection) {
            Text(state.s("IP koruması kapalı: dışarıdaki arkadaşların ancak modemde port yönlendirip gerçek IP adresini verirsen girebilir. Bu, yaklaşık konumunu gösterir.",
                "IP protection is off: friends outside your home can only join if you forward the port on your router and share your real IP address, which shows roughly where you are.",
                "IP-Schutz aus: Freunde außerhalb können nur per Portweiterleitung und deiner echten IP beitreten."), color = Color(0xFFFFB74D), fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(state.s("Sunucu bu bilgisayarda çalışıyor. Bilgisayarı ya da DNZ Launcher'ı kapatırsan sunucu da kapanır.",
            "The server runs on this computer. If you turn off the computer or close DNZ Launcher, it stops.",
            "Der Server läuft auf diesem PC. Schaltest du ihn aus oder schließt DNZ Launcher, stoppt er."), color = Dnz.Muted, fontSize = 12.sp)
        if (host.consoleOpen) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF07090D))
                    .verticalScroll(rememberScrollState(Int.MAX_VALUE)).padding(10.dp),
            ) {
                for (line in server.lines.takeLast(80)) {
                    Text(line, color = Color(0xFFB8C0D0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun AddressRow(state: LauncherState, label: String, value: String, copy: String?, good: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Dnz.Muted, fontSize = 12.sp, modifier = Modifier.width(220.dp))
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (good) Dnz.Success.copy(alpha = 0.12f) else Dnz.SurfaceHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) { Text(value, color = if (good) Dnz.Success else Dnz.Text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold) }
        if (copy != null) {
            Spacer(Modifier.width(8.dp))
            SmallButton(state.s("Kopyala", "Copy", "Kopieren")) {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(copy), null)
                state.toast = state.s("Kopyalandı: ", "Copied: ", "Kopiert: ") + copy
            }
        }
    }
}
