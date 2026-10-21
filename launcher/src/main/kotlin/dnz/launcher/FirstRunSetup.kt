@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dnz.launcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class SetupPage { Language, Theme, Privacy, Profile, SignIn, Install }

/** What the player picked on the Profile page (null = skipped). */
private class ProfileChoice(val name: String, val version: String, val loader: Loader, val mods: List<Modrinth.Hit>)

internal fun openInBrowser(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

private fun LauncherState.s(tr: String, en: String, de: String) = when (language) {
    Lang.TR -> tr
    Lang.EN -> en
    Lang.DE -> de
}

@Composable
fun FirstRunSetup(
    state: LauncherState, dragArea: @Composable (@Composable () -> Unit) -> Unit, onMinimize: () -> Unit, onClose: () -> Unit,
    /** Page to start on (screenshots only). */
    startPage: Int = 0,
) {
    var page by remember { mutableStateOf(SetupPage.entries[startPage]) }
    var choice by remember { mutableStateOf<ProfileChoice?>(null) }
    val pages = SetupPage.entries
    val shown by animateFloatAsState((page.ordinal + 1f) / pages.size, tween(400))

    fun next() {
        page = pages[(page.ordinal + 1).coerceAtMost(pages.size - 1)]
    }

    Column(Modifier.fillMaxSize().background(Dnz.Background).border(1.dp, Dnz.Border)) {
        dragArea {
            Row(Modifier.fillMaxWidth().height(32.dp).background(Dnz.Background).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("DNZ Launcher", color = Dnz.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                SetupWindowButton("—", onMinimize)
                SetupWindowButton("✕", onClose)
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            TutorialPanel(state, page)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AnimatedContent(page, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }) { p ->
                        Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Column(Modifier.widthIn(max = 700.dp).fillMaxWidth()) {
                                when (p) {
                                    SetupPage.Theme -> ThemePage(state)
                                    SetupPage.Language -> LanguagePage(state)
                                    SetupPage.Privacy -> PrivacyPage(state) { next() }
                                    SetupPage.Profile -> ProfilePage(state, onSkip = { choice = null; next() }, onDone = { choice = it; next() })
                                    SetupPage.SignIn -> SignInPage(state, onDone = { next() })
                                    SetupPage.Install -> InstallPage(state, choice, onFinish = { state.setupDone = true })
                                }
                            }
                        }
                    }
                }
                // bottom: progress bar, step text, Back / Next
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp)) {
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Dnz.Surface)) {
                        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Dnz.AccentGradient))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.s("Adım", "Step", "Schritt") + " ${page.ordinal + 1} / ${pages.size}", color = Dnz.Muted, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        val canGoBack = page.ordinal > 0 && page != SetupPage.Install
                        if (canGoBack) {
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).dnzClickable { page = pages[page.ordinal - 1] }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                                Text(state.s("Geri", "Back", "Zurück"), color = Dnz.Muted, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        // Profile and sign-in pages have their own buttons; the install page finishes by itself
                        when (page) {
                            SetupPage.Theme, SetupPage.Language -> DnzButton(state.s("İleri", "Next", "Weiter")) { next() }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialPanel(state: LauncherState, page: SetupPage) {
    val (title, body) = when (page) {
        SetupPage.Theme -> state.s("Görünümünü seç", "Choose your look", "Wähle dein Aussehen") to state.s(
            "DNZ Launcher'ın renklerini seç. Renkli tema bir vurgu rengi kullanır, Siyah Beyaz sade ve sakindir. İstediğin zaman Ayarlar'dan değiştirebilir, Workshop'tan tema yükleyebilirsin.",
            "Pick the colors of DNZ Launcher. Colorful uses an accent color; Black & White is plain and calm. You can change it any time in Settings or load a theme from the Workshop.",
            "Wähle die Farben von DNZ Launcher. Bunt nutzt eine Akzentfarbe, Schwarz-Weiß ist schlicht und ruhig. Du kannst es jederzeit in den Einstellungen ändern oder ein Theme aus dem Workshop laden.")
        SetupPage.Language -> state.s("DNZ Launcher'a hoş geldin!", "Welcome to DNZ Launcher!", "Willkommen bei DNZ Launcher!") to state.s(
            "Birkaç adımda her şeyi hazırlayalım. Önce dilini seç: launcher'daki bütün yazılar bu dilde olur.",
            "Let's set everything up in a few steps. First, choose your language: every text in the launcher uses it.",
            "Lass uns alles in ein paar Schritten einrichten. Wähle zuerst deine Sprache: Alle Texte im Launcher nutzen sie.")
        SetupPage.Privacy -> state.s("Gizliliğin", "Your privacy", "Deine Privatsphäre") to state.s(
            "DNZ'nin verilerinle ne yaptığını oku: sen istemedikçe hiçbir şey bilgisayarından çıkmaz. Devam etmek için Kabul et'e bas.",
            "Read what DNZ does with your data: nothing leaves your computer unless you ask. Press Accept to continue.",
            "Lies, was DNZ mit deinen Daten macht: Nichts verlässt deinen Computer, außer du willst es. Drücke Akzeptieren, um fortzufahren.")
        SetupPage.Profile -> state.s("İlk profilini oluştur", "Create your first profile", "Erstelle dein erstes Profil") to state.s(
            "Profil, bir Minecraft kurulumudur: sürüm, yükleyici ve modlar. Her profilin kendi modları ve dünyaları olur. DNZ Client ve FPS modları kendiliğinden gelir; istersen popüler modlardan seç ya da bu adımı atla.",
            "A profile is one Minecraft setup: version, loader and mods. Each profile has its own mods and worlds. DNZ Client and the FPS mods come by themselves; pick popular mods if you like, or skip this step.",
            "Ein Profil ist eine Minecraft-Einrichtung: Version, Loader und Mods. Jedes Profil hat eigene Mods und Welten. DNZ Client und die FPS-Mods kommen von selbst; wähle beliebte Mods oder überspringe diesen Schritt.")
        SetupPage.SignIn -> state.s("DNZ'ye giriş yap", "Sign in to DNZ", "Bei DNZ anmelden") to state.s(
            "Minecraft'ı aldığın Microsoft hesabıyla giriş yap. Tarayıcında Microsoft'un kendi sayfası açılır; şifreni oraya yazarsın, DNZ şifreni hiç görmez. Şimdi istemezsen sonra da giriş yapabilirsin.",
            "Sign in with the Microsoft account that owns Minecraft. Microsoft's own page opens in your browser; you type your password there and DNZ never sees it. You can also sign in later.",
            "Melde dich mit dem Microsoft-Konto an, das Minecraft besitzt. Microsofts eigene Seite öffnet sich im Browser; dort gibst du dein Passwort ein, DNZ sieht es nie. Du kannst dich auch später anmelden.")
        SetupPage.Install -> state.s("Kuruluyor", "Installing", "Installation") to state.s(
            "DNZ profilini hazırlıyor: DNZ Client, FPS modları ve seçtiğin modlar indiriliyor. Bitince launcher açılır.",
            "DNZ is preparing your profile: DNZ Client, the FPS mods and the mods you picked are downloading. The launcher opens when it's done.",
            "DNZ bereitet dein Profil vor: DNZ Client, die FPS-Mods und deine Mods werden geladen. Danach öffnet sich der Launcher.")
    }
    val steps = listOf(
        state.s("Dil", "Language", "Sprache"), state.s("Görünüm", "Look", "Aussehen"), state.s("Gizlilik", "Privacy", "Datenschutz"),
        state.s("Profil", "Profile", "Profil"), state.s("Giriş", "Sign in", "Anmelden"), state.s("Kurulum", "Install", "Installation"),
    )
    Column(Modifier.width(340.dp).fillMaxHeight().background(Dnz.Sidebar).padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(AppIcon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
            Text("DNZ Launcher", color = Dnz.Text, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(36.dp))
        Text(state.s("ADIM", "STEP", "SCHRITT") + " ${page.ordinal + 1}", color = Dnz.Accent, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Dnz.Text, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
        Spacer(Modifier.height(12.dp))
        Text(body, color = Dnz.Muted, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.weight(1f))
        steps.forEachIndexed { i, label ->
            val done = i < page.ordinal
            val current = i == page.ordinal
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (done || current) Dnz.Accent else Dnz.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Filled.Check, null, tint = Dnz.OnAccent, modifier = Modifier.size(14.dp))
                    else Text("${i + 1}", color = if (current) Dnz.OnAccent else Dnz.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(label, color = if (current) Dnz.Text else Dnz.Muted, fontSize = 13.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun SetupWindowButton(label: String, onClick: () -> Unit) {
    Box(Modifier.width(46.dp).fillMaxHeight().dnzClickable(highlight = true, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Dnz.Text, fontSize = 12.sp)
    }
}

@Composable
private fun PageTitle(@Suppress("UNUSED_PARAMETER") title: String, task: String) {
    Text(task, color = Dnz.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ChoiceCard(selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) Dnz.SurfaceHigh else Dnz.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) Dnz.Accent else Dnz.Border, RoundedCornerShape(16.dp))
            .dnzClickable(onClick = onClick).padding(18.dp),
        content = content,
    )
}

// ---------------------------------------------------------------- pages

@Composable
private fun ThemePage(state: LauncherState) {
    PageTitle("", state.s("Bir tema seç", "Pick a theme", "Wähle ein Theme"))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for (theme in LauncherTheme.entries) {
            val selected = Dnz.theme == theme && Dnz.custom == null
            ChoiceCard(selected, Modifier.weight(1f), onClick = { ThemePack.clear(); Dnz.pickAccent(null); Dnz.theme = theme }) {
                val colors = if (theme == LauncherTheme.Mono) listOf(Color(0xFF050505), Color(0xFFF2F2F2)) else listOf(Color(0xFF0A0D12), Color(0xFFFF8A3D))
                Box(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(10.dp)).background(colors[0]).border(1.dp, Dnz.Border, RoundedCornerShape(10.dp))) {
                    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(width = 60.dp, height = 18.dp).clip(RoundedCornerShape(6.dp)).background(colors[1]))
                }
                Spacer(Modifier.height(10.dp))
                Text(state.t(theme.key), color = Dnz.Text, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    Text(state.s("Vurgu rengi", "Accent color", "Akzentfarbe"), color = Dnz.Muted, fontSize = 13.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Dnz.ACCENTS.forEachIndexed { i, color ->
            val selected = (Dnz.accent ?: Dnz.ACCENTS[0]) == color && Dnz.theme == LauncherTheme.Colorful
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color(color))
                    .border(if (selected) 3.dp else 0.dp, Dnz.Text, CircleShape)
                    .dnzClickable { ThemePack.clear(); Dnz.pickAccent(if (i == 0) null else color); Dnz.theme = LauncherTheme.Colorful },
                contentAlignment = Alignment.Center,
            ) { if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(state.s("Bunu sonra Ayarlar'dan değiştirebilirsin. Workshop'tan tema da yükleyebilirsin.",
        "You can change this later in Settings, or load a theme from the Workshop.",
        "Das kannst du später in den Einstellungen ändern oder ein Theme aus dem Workshop laden."), color = Dnz.Muted, fontSize = 12.sp)
}

@Composable
private fun LanguagePage(state: LauncherState) {
    PageTitle(state.s("Dil", "Language", "Sprache"), state.s("Launcher hangi dilde olsun?", "Which language should the launcher use?", "In welcher Sprache soll der Launcher sein?"))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for ((lang, label, flag) in listOf(Triple(Lang.TR, "Türkçe", "TR"), Triple(Lang.EN, "English", "EN"), Triple(Lang.DE, "Deutsch", "DE"))) {
            ChoiceCard(state.language == lang, Modifier.weight(1f), onClick = { state.language = lang }) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Dnz.AccentGradient), contentAlignment = Alignment.Center) {
                    Text(flag, color = Dnz.OnAccent, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                Text(label, color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun PrivacyPage(state: LauncherState, onAccept: () -> Unit) {
    // the document: app icon with a page badge, its name, then the text
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(58.dp)) {
            Image(AppIcon, null, Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)))
            Box(
                Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(Dnz.Accent).border(2.dp, Dnz.Background, CircleShape),
                contentAlignment = Alignment.Center,
            ) { DocumentIcon(Dnz.OnAccent, 14.dp) }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(state.s("Gizlilik Politikası", "Privacy Policy", "Datenschutzerklärung"), color = Dnz.Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("DNZ Launcher · DNZ Client · dnzclient.com", color = Dnz.Muted, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(18.dp))
    val points = when (state.language) {
        Lang.TR -> listOf(
            "DNZ hiçbir takip, reklam, analiz veya telemetri içermez.",
            "DNZ'nin hatırlaması gereken her şey sadece senin bilgisayarında saklanır.",
            "Microsoft ile girişte şifreni Microsoft'un kendi sayfasına yazarsın; DNZ şifreni hiç görmez.",
            "DNZ sadece senin kullandığın servislere bağlanır (Microsoft, Mojang, Modrinth...) ve sadece istediğin işi yapmak için.",
            "Bulut kayıt isteğe bağlıdır ve kapalı başlar. Açarsan sadece tuşların ve DNZ ayarların saklanır.",
            "DNZ ücretsiz ve açık kaynaklıdır (GPL-3.0); bu söylenenleri herkes kaynak kodda kontrol edebilir.",
        )
        Lang.DE -> listOf(
            "DNZ enthält kein Tracking, keine Werbung, keine Analysen und keine Telemetrie.",
            "Alles, was DNZ sich merken muss, wird nur auf deinem Computer gespeichert.",
            "Bei der Microsoft-Anmeldung gibst du dein Passwort auf Microsofts eigener Seite ein; DNZ sieht es nie.",
            "DNZ verbindet sich nur mit den Diensten, die du darüber nutzt (Microsoft, Mojang, Modrinth...), und nur für das, was du willst.",
            "Cloud-Speichern ist freiwillig und anfangs aus. Wenn du es einschaltest, werden nur deine Tasten und DNZ-Einstellungen gespeichert.",
            "DNZ ist kostenlos und Open Source (GPL-3.0); jeder kann diese Aussagen im Quellcode prüfen.",
        )
        Lang.EN -> listOf(
            "DNZ has no tracking, advertising, analytics or telemetry.",
            "Everything DNZ needs to remember is stored only on your computer.",
            "When you sign in with Microsoft you type your password on Microsoft's own page; DNZ never sees it.",
            "DNZ only connects to the services you use through it (Microsoft, Mojang, Modrinth...), and only to do what you ask.",
            "Cloud save is optional and starts off. If you turn it on, only your key bindings and DNZ settings are stored.",
            "DNZ is free and open source (GPL-3.0); anyone can check these statements in the source code.",
        )
    }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(14.dp))
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (p in points) {
            Row {
                Icon(Icons.Filled.CheckCircle, null, tint = Dnz.Accent, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Spacer(Modifier.width(10.dp))
                Text(p, color = Dnz.Text, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            state.s("Tam metni oku: dnzclient.com/privacy-policy", "Read the full text: dnzclient.com/privacy-policy", "Ganzen Text lesen: dnzclient.com/privacy-policy"),
            color = Dnz.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.dnzClickable(highlight = false) { openInBrowser("https://dnzclient.com/privacy-policy") },
        )
    }
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(state.s("Kabul etmeden DNZ Launcher kullanılamaz.", "DNZ Launcher can't be used without accepting.", "Ohne Zustimmung kann DNZ Launcher nicht genutzt werden."),
            color = Dnz.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Dnz.Danger.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .dnzClickable {
                    state.confirm = Confirm(
                        state.s("Reddetmek istiyor musun?", "Decline the privacy policy?", "Datenschutzerklärung ablehnen?"),
                        state.s("Gizlilik politikasını kabul etmeden DNZ Launcher kullanılamaz; launcher kapanacak. Sonra tekrar açıp kabul edebilirsin.",
                            "DNZ Launcher can't be used without accepting the privacy policy, so it will close. You can open it again and accept later.",
                            "Ohne Zustimmung kann DNZ Launcher nicht genutzt werden; er wird geschlossen. Du kannst ihn später erneut öffnen und zustimmen."),
                        state.s("Reddet ve kapat", "Decline and close", "Ablehnen und schließen"),
                    ) { kotlin.system.exitProcess(0) }
                }
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) { Text(state.s("Reddet", "Decline", "Ablehnen"), color = Dnz.Danger, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        DnzButton(state.s("Kabul et", "Accept", "Akzeptieren"), onClick = onAccept)
    }
}

@Composable
private fun ProfilePage(state: LauncherState, onSkip: () -> Unit, onDone: (ProfileChoice) -> Unit) {
    var name by remember { mutableStateOf("DNZ") }
    var version by remember { mutableStateOf(state.versions.first()) }
    var loader by remember { mutableStateOf(Versions.loader(version)) }
    if (loader !in Versions.loaders(version)) loader = Versions.loader(version)
    val picked = remember { mutableStateListOf<String>() }
    var mods by remember { mutableStateOf<List<Modrinth.Hit>?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Popular mods for this version (not the ones DNZ installs anyway, no libraries)
    LaunchedEffect(version, loader) {
        mods = null
        failed = false
        picked.clear()
        if (loader != Loader.Fabric) return@LaunchedEffect
        val bundled = GameLauncher.modrinthMods.toSet()
        runCatching { withContext(Dispatchers.IO) { Modrinth.search("", version, Modrinth.Sort.Downloads, null, 0, 40) } }
            .onSuccess { page -> mods = page.hits.filter { it.slug !in bundled && "library" !in it.categories }.take(12) }
            .onFailure { failed = true }
    }

    PageTitle(state.s("İlk profilin", "Your first profile", "Dein erstes Profil"),
        state.s("Bir sürüm seç, istersen birkaç mod ekle. İstemezsen atlayabilirsin.", "Pick a version and add a few mods if you like. You can also skip this.",
            "Wähle eine Version und füge ein paar Mods hinzu, wenn du willst. Du kannst das auch überspringen."))
    Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.s("İsim", "Name", "Name"), color = Dnz.Muted, fontSize = 13.sp, modifier = Modifier.width(80.dp))
            Box(Modifier.width(280.dp).clip(RoundedCornerShape(10.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                BasicTextField(name, { name = it.take(32) }, singleLine = true, textStyle = TextStyle(color = Dnz.Text, fontSize = 14.sp), cursorBrush = SolidColor(Dnz.Accent), modifier = Modifier.fillMaxWidth())
            }
        }
        Row {
            Text(state.s("Sürüm", "Version", "Version"), color = Dnz.Muted, fontSize = 13.sp, modifier = Modifier.width(80.dp).padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (v in state.versions) SetupChip(v, v == version) { version = v }
            }
        }
        Row {
            Text(state.s("Yükleyici", "Loader", "Loader"), color = Dnz.Muted, fontSize = 13.sp, modifier = Modifier.width(80.dp).padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in Versions.loaders(version)) SetupChip(l.name, l == loader) { loader = l }
            }
        }
        if (loader == Loader.Fabric) {
            Text(state.s("Popüler modlar (DNZ Client ve FPS modları zaten kurulur)", "Popular mods (DNZ Client and the FPS mods are installed anyway)",
                "Beliebte Mods (DNZ Client und die FPS-Mods kommen sowieso dazu)"), color = Dnz.Muted, fontSize = 13.sp)
            val list = mods
            when {
                failed -> Text(state.s("Modlar yüklenemedi; sonra Modlar sayfasından ekleyebilirsin.", "Couldn't load mods; you can add them later on the Mods page.",
                    "Mods konnten nicht geladen werden; du kannst sie später auf der Mods-Seite hinzufügen."), color = Dnz.Muted, fontSize = 12.sp)
                list == null -> CircularProgressIndicator(color = Dnz.Accent, modifier = Modifier.size(24.dp))
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (hit in list) {
                        val on = hit.id in picked
                        Row(
                            Modifier.width(220.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Dnz.SurfaceHigh else Dnz.Surface)
                                .border(if (on) 2.dp else 1.dp, if (on) Dnz.Accent else Dnz.Border, RoundedCornerShape(12.dp))
                                .dnzClickable { if (on) picked.remove(hit.id) else picked.add(hit.id) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RemoteImage(hit.iconUrl, Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))) {
                                Box(Modifier.fillMaxSize().background(Dnz.SurfaceHigh))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(hit.title, color = Dnz.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(hit.description, color = Dnz.Muted, fontSize = 11.sp, maxLines = 1)
                            }
                            if (on) Icon(Icons.Filled.CheckCircle, null, tint = Dnz.Accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(10.dp)).dnzClickable(onClick = onSkip).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(state.s("Atla", "Skip", "Überspringen"), color = Dnz.Muted, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        DnzButton(state.s("Profili oluştur", "Create profile", "Profil erstellen")) {
            onDone(ProfileChoice(name.trim().ifBlank { "DNZ" }, version, loader, mods.orEmpty().filter { it.id in picked }))
        }
    }
}

@Composable
private fun DocumentIcon(color: Color, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.11f
        val left = w * 0.16f
        val right = w * 0.84f
        val fold = w * 0.26f
        val page = androidx.compose.ui.graphics.Path().apply {
            moveTo(left, 0f); lineTo(right - fold, 0f); lineTo(right, fold); lineTo(right, h); lineTo(left, h); close()
        }
        drawPath(page, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        for (k in 1..3) {
            val y = h * (0.3f + k * 0.17f)
            drawLine(color, androidx.compose.ui.geometry.Offset(left + w * 0.14f, y), androidx.compose.ui.geometry.Offset(right - w * 0.14f, y), stroke * 0.8f)
        }
    }
}

@Composable
private fun SetupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Dnz.Accent else Dnz.Surface)
            .border(1.dp, if (selected) Dnz.Accent else Dnz.Border, RoundedCornerShape(10.dp))
            .dnzClickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = if (selected) Dnz.OnAccent else Dnz.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun SignInPage(state: LauncherState, onDone: () -> Unit) {
    // Signed in on Microsoft's page: on to the next step by itself
    val account = state.account
    LaunchedEffect(account) { if (account != null && !account.offline) onDone() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.msLogin != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Dnz.Accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(state.s("Tarayıcıda girişi bitir...", "Finish signing in in your browser...", "Schließe die Anmeldung im Browser ab..."), color = Dnz.Text, fontSize = 14.sp)
            }
        } else {
            DnzButton(state.s("Microsoft ile giriş yap", "Sign in with Microsoft", "Mit Microsoft anmelden"), big = true) { signInWithMicrosoft(state) }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.clip(RoundedCornerShape(10.dp)).dnzClickable(onClick = onDone).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(state.s("Daha sonra giriş yap", "Sign in later", "Später anmelden"), color = Dnz.Muted, fontWeight = FontWeight.SemiBold)
        }
        Text(state.s("Oyna'ya bastığında, profil oluştururken ya da mod eklerken giriş yapman istenir.",
            "You'll be asked to sign in when you press Play, make a profile or add a mod.",
            "Du wirst gefragt, wenn du Spielen drückst, ein Profil erstellst oder einen Mod hinzufügst."), color = Dnz.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun InstallPage(state: LauncherState, choice: ProfileChoice?, onFinish: () -> Unit) {
    var step by remember { mutableStateOf(state.s("Hazırlanıyor...", "Getting ready...", "Wird vorbereitet...")) }
    var value by remember { mutableStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val shown by animateFloatAsState(value, tween(300))
    LaunchedEffect(Unit) {
        if (choice != null) {
            val folder = Profiles.freeFolder(choice.name, state.profiles.map { it.folder })
            val profile = Profile(choice.name, choice.version, "DNZ Client", folder, state.profiles.size % ProfileColors.size, null, null, choice.loader.name)
            state.profiles.add(profile)
            state.selectedProfile = state.profiles.size - 1
            state.version = profile.version
            val total = choice.mods.size + 1
            runCatching {
                if (choice.loader == Loader.Fabric) {
                    withContext(Dispatchers.IO) {
                        GameLauncher.installMods(choice.version, File(GameLauncher.instanceDir(profile), "mods")) { text, v ->
                            step = text
                            value = v / total
                        }
                    }
                }
                choice.mods.forEachIndexed { i, hit ->
                    step = state.s("Mod kuruluyor: ", "Installing mod: ", "Mod wird installiert: ") + hit.title
                    value = (i + 1f) / total
                    Library.install(profile, hit.id)
                }
            }.onFailure { problem = it.message ?: it.toString() }
        }
        value = 1f
        step = state.s("Her şey hazır!", "All set!", "Alles bereit!")
        done = true
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (done) Icon(Icons.Filled.CheckCircle, null, tint = Dnz.Success, modifier = Modifier.size(56.dp))
        else CircularProgressIndicator(color = Dnz.Accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(18.dp))
        Text(state.s("Kurulum", "Installing", "Installation"), color = Dnz.Text, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(step, color = Dnz.Muted, fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.width(460.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Dnz.Surface)) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(Dnz.AccentGradient))
        }
        problem?.let {
            Spacer(Modifier.height(12.dp))
            Text(state.s("Bazı dosyalar indirilemedi; ilk Oyna'da tekrar denenecek.", "Some files couldn't be downloaded; they'll be tried again on the first Play.",
                "Einige Dateien konnten nicht geladen werden; beim ersten Spielen wird es erneut versucht."), color = Color(0xFFFFB74D), fontSize = 12.sp)
        }
        if (done) {
            Spacer(Modifier.height(24.dp))
            DnzButton(state.s("DNZ Launcher'ı aç", "Open DNZ Launcher", "DNZ Launcher öffnen"), big = true, onClick = onFinish)
        }
    }
}
