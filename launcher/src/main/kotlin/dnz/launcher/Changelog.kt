package dnz.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Changelog" popup: shown once on the first start after the launcher updated itself, with the new version
 * and what came with it (every version newer than the one used before).
 */
object Changelog {
    private class Release(val version: String, val tr: List<String>, val en: List<String>, val de: List<String>)

    private val RELEASES = listOf(
        Release(
            "1.0.17",
            tr = listOf(
                "Sağ üstte \"Aktif oyuncu\": şu an kaç kişi DNZ Launcher'da. Tıklayınca kaç kişinin oyunda olduğunu ve toplam oyuncu sayısını gösterir.",
                "DNZ rozeti yeniden çalışıyor: DNZ kullanan oyuncuların isminin önünde D logosu.",
            ),
            en = listOf(
                "\"Active players\" at the top right: how many people have DNZ Launcher open. Click it to see how many are in game and the overall player count.",
                "The DNZ badge works again: a D logo before the names of players using DNZ.",
            ),
            de = listOf(
                "\"Aktive Spieler\" oben rechts: wie viele DNZ Launcher gerade offen haben. Klick zeigt, wie viele im Spiel sind und alle Spieler insgesamt.",
                "Das DNZ-Abzeichen funktioniert wieder: ein D-Logo vor den Namen von DNZ-Spielern.",
            ),
        ),
        Release(
            "1.0.16",
            tr = listOf(
                "Sürüm desteği: 1.8.9'dan 26.3'e kadar. Profil oluştururken Vanilla, Fabric veya Forge seç (sadece sürümün desteklediği çıkar).",
                "DNZ Client artık 1.8.9'da da var: aynı menüler, aynı modlar, D rozeti ve FPS için DNZ Engine.",
                "Temalar: Ayarlar → \"Tema dosyası yükle\". Bir .dnztheme dosyası launcher'ın ve oyun içi DNZ menüsünün renklerini ve süslerini değiştirir.",
                "Skin: oyuncu adıyla skin ara veya dosyadan yükle, tek tıkla kendi skinin yap.",
                "Profillerde \"Logları aç\" butonu (oyun açıkken de çalışır). Discord'da \"DNZ Client oynuyor\" yazar.",
                "Yeni görev çubuğu simgesi ve sağ tık menüsü.",
                "DNZ Client 1.40.0: Sohbet Kafaları (mesajı yazanın skin kafası mesajın yanında) ve Blok Çerçevesi (baktığın bloğun çerçevesi; renk, gökkuşağı, kalınlık, dolgu).",
                "Yeni modlar otomatik kurulur: Simple Voice Chat (sesli sohbet), ItemPhysic Lite (yere düşen eşyalar yatar), 3D Skin Layers, Not Enough Crashes (çökünce oyun kapanmaz, menüye döner), ScalableLux ve Fast Noise (daha hızlı ışık ve dünya oluşturma).",
            ),
            en = listOf(
                "Version support from 1.8.9 to 26.3. Choose Vanilla, Fabric or Forge when making a profile (only what the version supports).",
                "DNZ Client on 1.8.9 too: the same menus and modules, the D badge and DNZ Engine for more FPS.",
                "Themes: Settings → \"Load theme file\". A .dnztheme changes the colors and decorations of the launcher and the in-game DNZ menu.",
                "Skins: search a skin by player name or load one from a file, and wear it with one click.",
                "\"Open logs\" button on profiles (works while the game runs). Discord shows \"Playing DNZ Client\".",
                "New tray icon and right-click menu.",
                "DNZ Client 1.40.0: Chat Heads (the writer's skin head next to each message) and Block Outline (the frame around the block you look at; color, rainbow, width, fill).",
                "New mods installed automatically: Simple Voice Chat, ItemPhysic Lite (dropped items lie flat), 3D Skin Layers, Not Enough Crashes (a crash returns to the menu instead of closing the game), ScalableLux and Fast Noise (faster lighting and world generation).",
            ),
            de = listOf(
                "Versionen von 1.8.9 bis 26.3. Beim Erstellen eines Profils Vanilla, Fabric oder Forge wählen (nur was die Version unterstützt).",
                "DNZ Client jetzt auch für 1.8.9: gleiche Menüs und Module, das D-Abzeichen und DNZ Engine für mehr FPS.",
                "Themes: Einstellungen → \"Theme-Datei laden\". Eine .dnztheme ändert Farben und Deko des Launchers und des DNZ-Menüs im Spiel.",
                "Skins: Skin per Spielername suchen oder aus einer Datei laden und mit einem Klick tragen.",
                "\"Logs öffnen\" bei Profilen (auch während das Spiel läuft). Discord zeigt \"Spielt DNZ Client\".",
                "Neues Taskleisten-Symbol und Rechtsklick-Menü.",
                "DNZ Client 1.40.0: Chat-Köpfe (Skin-Kopf des Schreibers neben jeder Nachricht) und Block-Umrandung (Farbe, Regenbogen, Dicke, Füllung).",
                "Neue Mods werden automatisch installiert: Simple Voice Chat, ItemPhysic Lite, 3D Skin Layers, Not Enough Crashes (nach einem Absturz zurück ins Menü), ScalableLux und Fast Noise (schnelleres Licht und Weltgenerierung).",
            ),
        ),
        Release(
            "1.0.15",
            tr = listOf(
                "Modpack desteği: Mods → Modpackler. Modrinth'teki Fabric modpacklerini tek tıkla yeni bir profil olarak kur.",
                "Birden fazla Microsoft hesabı: Hesap sayfasında \"Başka hesap ekle\", sağ üstteki menüden tek tıkla hesap değiştir.",
                "DNZ Client 1.39.0: Serbest Bakış (Freelook). Sol Alt'a basılı tut, karakterin etrafına bak. Sadece tek oyunculu dünyada çalışır, sunucularda kapalı ve açılamaz.",
                "Değişiklikler penceresi artık her açılışta tekrar çıkmıyor.",
            ),
            en = listOf(
                "Modpack support: Mods → Modpacks. Install Fabric modpacks from Modrinth as a new profile with one click.",
                "Several Microsoft accounts: \"Add another account\" on the Account page, switch with one click in the top-right menu.",
                "DNZ Client 1.39.0: Freelook. Hold Left Alt to look around your player. Single player only, off and locked on servers.",
                "The changelog no longer opens again on every start.",
            ),
            de = listOf(
                "Modpack-Unterstützung: Mods → Modpacks. Fabric-Modpacks von Modrinth mit einem Klick als neues Profil installieren.",
                "Mehrere Microsoft-Konten: \"Weiteres Konto hinzufügen\" auf der Kontoseite, Wechsel mit einem Klick oben rechts.",
                "DNZ Client 1.39.0: Freelook. Linke Alt-Taste halten, um dich umzusehen. Nur im Einzelspieler, auf Servern aus und gesperrt.",
                "Diese Übersicht erscheint nicht mehr bei jedem Start.",
            ),
        ),
        Release(
            "1.0.14",
            tr = listOf(
                "DNZ Client 1.38.0: DNZ kullanan oyuncuların isminin önünde D logosu (başlarının üstünde ve Tab listesinde).",
                "F5'te kendi ismin de logoyla birlikte görünür.",
                "Yeni Kalkan modu: kalkanı birinci şahısta indir ve küçült.",
                "Her modun artık kendi simgesi var.",
                "Yeni HUD modları: Gün, Hava Durumu, Pil, Shader ve Oyun İstatistikleri (öldürme, ölüm, K/D).",
                "Güncellemeden sonra bu değişiklik penceresi açılır.",
            ),
            en = listOf(
                "DNZ Client 1.38.0: a D logo before the names of players using DNZ (above their head and in Tab).",
                "Your own name with the logo shows in third person (F5).",
                "New Shield module: lower and shrink the shield in first person.",
                "Every module now has its own icon.",
                "New HUD modules: Day, Weather, Battery, Shader and Session Stats (kills, deaths, K/D).",
                "This changelog opens after an update.",
            ),
            de = listOf(
                "DNZ Client 1.38.0: ein D-Logo vor den Namen von Spielern mit DNZ (über dem Kopf und in der Tab-Liste).",
                "Dein eigener Name mit Logo ist in der dritten Person (F5) zu sehen.",
                "Neues Schild-Modul: Schild in der Ego-Sicht tiefer und kleiner.",
                "Jedes Modul hat jetzt ein eigenes Symbol.",
                "Neue HUD-Module: Tag, Wetter, Akku, Shader und Statistik (Kills, Tode, K/D).",
                "Nach einem Update öffnet sich diese Übersicht.",
            ),
        ),
        Release(
            "1.0.13",
            tr = listOf("Mods → Workshop: DNZ launcher temaları. Kütüphanem, dnzclient.com'daki kütüphanenle aynı; Yükle ile tema launcher'a gelir."),
            en = listOf("Mods → Workshop: DNZ launcher themes. My library is the same as on dnzclient.com; Install puts a theme on."),
            de = listOf("Mods → Workshop: DNZ-Launcher-Themes. Meine Bibliothek ist dieselbe wie auf dnzclient.com; Installieren setzt ein Theme."),
        ),
    )

    /** Notes of the versions after [from] up to [to] (newest first), or empty. */
    fun notes(lang: Lang, from: String?, to: String): List<Pair<String, List<String>>> =
        RELEASES.filter { !newer(it.version, to) && (from == null || newer(it.version, from)) }
            .map { r -> r.version to when (lang) { Lang.TR -> r.tr; Lang.EN -> r.en; Lang.DE -> r.de } }

    private fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** At start: an update happened since the last run → open the popup once; remembers this version either way. */
    fun checkAtStart(state: LauncherState) {
        val current = Updater.current ?: return
        val last = state.lastVersion
        if (last == current) return
        // no version saved yet: an older launcher (from before this popup) if settings already existed, else a fresh install
        if (last != null || state.hadSettings) {
            if (notes(state.language, last, current).isNotEmpty()) {
                state.changelogTo = current
                state.changelogFrom = last ?: "?"
            }
        }
        state.lastVersion = current
        // Saved at once: the automatic save skips changes made this early, and the popup would come back every start
        val saved = Settings.snapshot(state)
        Thread { Settings.save(saved) }.start()
    }
}

@Composable
fun ChangelogDialog(state: LauncherState) {
    val from = state.changelogFrom ?: return
    val to = state.changelogTo ?: return
    val close = { state.changelogFrom = null }
    AlertDialog(
        onDismissRequest = close,
        containerColor = Dnz.SurfaceHigh,
        title = { Text(state.t("changelog.title"), color = Dnz.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (from == "?") state.t("changelog.now").replace("%s", to) else state.t("changelog.updated").replace("%1", from).replace("%2", to),
                    color = Dnz.Accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
                for ((version, lines) in Changelog.notes(state.language, from.takeIf { it != "?" }, to)) {
                    Spacer(Modifier.height(12.dp))
                    Text(version, color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    for (line in lines) {
                        Row(Modifier.padding(top = 5.dp)) {
                            Text("•", color = Dnz.Accent, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(line, color = Dnz.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { DnzButton(state.t("ok"), onClick = close) },
    )
}
