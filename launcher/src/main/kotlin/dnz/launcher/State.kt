package dnz.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext

enum class Screen { Home, Profiles, Mods, Servers, Settings, Account }

/** Official = hand off to the Minecraft Launcher (no Mojang approval needed). Direct = DNZ starts the game itself. */
enum class LaunchMode { Official, Direct }

data class Profile(
    val name: String,
    val version: String,
    val description: String,
    /** Folder under instances/, fixed when the profile is made, so renaming keeps its worlds and mods. */
    val dir: String? = null,
    /** Color of the letter icon (index into ProfileColors). */
    val color: Int? = null,
    /** Own picture: file name in ~/.dnzlauncher/icons, or null for the letter icon. */
    val icon: String? = null,
    /** RAM for this profile in GB; null = the RAM from Settings. */
    val ramGb: Int? = null,
) {
    val folder: String get() = dir ?: name.replace(Regex("[^A-Za-z0-9_-]"), "_")
}

/** A yes/no question; [onConfirm] runs when the player presses [button]. */
data class Confirm(val title: String, val body: String, val button: String, val onConfirm: () -> Unit)

/** Everything the UI shows; kept in one place so screens stay simple. */
class LauncherState {
    /** Work that must finish even when the player switches pages (installs, launching). */
    val scope = MainScope()

    var screen by mutableStateOf(Screen.Home)
    private var languageState by mutableStateOf(Lang.TR)
    var language: Lang
        get() = languageState
        set(value) {
            languageState = value
            Strings.currentLang = value
        }
    var version by mutableStateOf("26.3")
    var ramGb by mutableStateOf(4f)

    /** RAM for starting [profile]: its own setting, or the one from Settings. */
    fun ramFor(profile: Profile): Int = profile.ramGb ?: ramGb.toInt()

    /** Profile being edited in the profile window (-1 = a new one), or null. */
    var editingProfile by mutableStateOf<Int?>(null)
    /** Profile asked to be deleted, or null. */
    var deletingProfile by mutableStateOf<Int?>(null)
    var launchMode by mutableStateOf(LaunchMode.Official)
    var account by mutableStateOf<Account?>(null)
    /** Open while the player signs in on Microsoft's page. */
    var msLogin by mutableStateOf<MsLogin?>(null)
    val accountName: String? get() = account?.name

    /** Accounts of the official Minecraft Launcher (names and profile ids only) and which one is active there. */
    var officialAccounts by mutableStateOf<List<McAccount>>(emptyList())
    var activeOfficialId by mutableStateOf<String?>(null)

    /** The account shown top right: the Minecraft Launcher's active one, or DNZ's own when DNZ starts the game. */
    val currentAccount: McAccount?
        get() = if (launchMode == LaunchMode.Direct && account != null) dnzAccount
        else officialAccounts.firstOrNull { it.localId == activeOfficialId } ?: officialAccounts.firstOrNull()

    /** DNZ's own Microsoft sign-in, shown in the list while DNZ starts the game itself. */
    private val dnzAccount: McAccount?
        get() = account?.let { McAccount(it.name, it.uuid.replace("-", ""), null, null) }

    val switchableAccounts: List<McAccount>
        get() = officialAccounts + listOfNotNull(dnzAccount.takeIf { launchMode == LaunchMode.Direct })

    suspend fun refreshAccounts() {
        val official = withContext(Dispatchers.IO) { Accounts.readOfficial() }
        officialAccounts = official.accounts
        activeOfficialId = official.activeLocalId
    }

    suspend fun switchAccount(target: McAccount) {
        if (target.localId == null) return // DNZ's own sign-in is already the one in use
        val ok = withContext(Dispatchers.IO) { Accounts.switchOfficial(target) }
        if (ok) {
            refreshAccounts()
            // A Minecraft Launcher account can only play through the Minecraft Launcher.
            val modeChanged = launchMode != LaunchMode.Official
            launchMode = LaunchMode.Official
            toast = t("acc.switched").replace("%s", target.name) + if (modeChanged) " " + t("acc.mode_changed") else ""
        } else {
            dialog = t("acc.switch_failed.title") to t("acc.switch_failed.body")
        }
    }
    var dialog by mutableStateOf<Pair<String, String>?>(null)
    var confirm by mutableStateOf<Confirm?>(null)
    /** Short message at the bottom that hides itself. */
    var toast by mutableStateOf<String?>(null)
    val library = LibraryState()
    val imports = ImportState()
    val servers = ServersState()

    /** "Auto" button on the Settings page: scanning right now / what it found and changed. */
    var autoBusy by mutableStateOf(false)

    /** Launcher starts hidden with Windows (Startup folder shortcut). Read from Windows, not from launcher.json. */
    var startWithWindows by mutableStateOf(Autostart.isEnabled())
    var autoResult by mutableStateOf<AutoTune.Result?>(null)
    /** Mac: AUTO ran once by itself (first start), so the game starts with settings made for this Mac. */
    var macAutoDone by mutableStateOf(false)
    /** Home page notice about Mojang's approval: hidden for good (saved) / closed until the next start. */
    var mojangNoticeHidden by mutableStateOf(false)
    var mojangNoticeClosed by mutableStateOf(false)

    /** Install / launch progress shown on the Home screen. */
    var busy by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var progress by mutableStateOf(0f)
    var gameRunning by mutableStateOf(false)

    val versions = listOf("26.3", "26.2")
    val profiles = mutableStateListOf(
        Profile("DNZ PvP", "26.3", "DNZ Client + Sodium"),
        Profile("DNZ Survival", "26.2", "DNZ Client + Sodium + Mod Menu"),
    )
    var selectedProfile by mutableStateOf(0)

    fun t(key: String): String = Strings.get(language, key)

    init {
        Settings.load(this)
    }
}

enum class Lang { TR, EN }

object Strings {
    private val tr = mapOf(
        "home" to "Ana Sayfa", "profiles" to "Profiller", "mods" to "Modlar", "settings" to "Ayarlar", "account" to "Hesap",
        "play" to "OYNA", "version" to "Sürüm", "profile" to "Profil",
        "hero.title" to "DNZ Client", "hero.subtitle" to "Modern arayüz, HUD, Zoom ve daha fazlası. Powered by Sodium.",
        "news" to "Haberler",
        "news1.title" to "DNZ Client 1.18: Modüller", "news1.body" to "35 modül: tuş göstergesi, menzil, kombo, hedef bilgisi, pusula, sayaçlar, doygunluk ve yemek göstergesi. Hepsi aç/kapa, sürükle, büyüt.",
        "news2.title" to "DNZ Schematic", "news2.body" to "M tuşuna bas: .litematic yapıları hayalet blok olarak gör, yanlış ve eksik blokları renkli kutularla bul.",
        "news3.title" to "Otomatik Ayar", "news3.body" to "Ayarlar'daki AUTO butonu bilgisayarını tarar, en iyi FPS için ayarlar.",
        "not_signed" to "Giriş yapılmadı", "sign_in" to "Microsoft ile Giriş Yap",
        "sign_in.info" to "Giriş, Microsoft'un resmi sayfasında yapılır. Şifreni DNZ asla görmez.",
        "play.pending.title" to "Yakında",
        "play.pending.body" to "Oyunu başlatma bir sonraki aşamada eklenecek. Microsoft girişi Mojang onayından sonra açılacak.",
        "signin.pending.body" to "Microsoft girişi, Mojang'ın DNZ Launcher'ı onaylamasından sonra açılacak. Giriş her zaman Microsoft'un resmi sayfasında yapılır.",
        "profiles.new" to "Yeni Profil", "profiles.selected" to "Seçili",
        "mods.search" to "Modrinth'te mod ara...", "mods.searching" to "Aranıyor...", "mods.install" to "Yükle",
        "mods.error" to "Arama başarısız. İnternet bağlantını kontrol et.", "mods.downloads" to "indirme",
        "settings.ram" to "Bellek (RAM)", "settings.language" to "Dil", "settings.theme" to "Tema", "theme.color" to "Renkli", "theme.mono" to "Siyah Beyaz", "settings.folder" to "Oyun klasörü",
        "settings.ram.hint" to "PvP için 4 GB yeterli. Shader kullanıyorsan 6-8 GB önerilir.",
        "account.title" to "Microsoft Hesabı",
        "account.safe1" to "Şifren sadece Microsoft'un resmi sayfasına girilir.",
        "account.safe2" to "DNZ sadece Microsoft'un verdiği giriş anahtarını saklar, şifreli olarak.",
        "account.safe3" to "İstediğin zaman account.microsoft.com'dan izni kaldırabilirsin.",
        "ok" to "Tamam",
        "need_account.title" to "Önce giriş yap",
        "need_account.body" to "Oynamak için Hesap sayfasından Microsoft ile giriş yap. Mojang onayı gelene kadar Ayarlar'dan Resmi Minecraft Launcher yöntemiyle oynayabilirsin.",
        "sign_out" to "Çıkış yap",
        "running" to "Oyun açık",
        "launch_error" to "Oyun başlatılamadı",
        "settings.mode" to "Başlatma yöntemi",
        "mode.official" to "Resmi Minecraft Launcher",
        "mode.official.info" to "Önerilen. Hesabın Minecraft Launcher'da kalır, DNZ hesabına hiç dokunmaz. OYNA'ya basınca Minecraft Launcher açılır, orada DNZ profilini seçip Oyna'ya bas.",
        "mode.direct" to "DNZ ile direkt başlat",
        "mode.direct.info" to "Tek tıkla oyunu açar. Microsoft hesabıyla giriş gerekir (Mojang onayından sonra).",
        "official.done" to "Minecraft Launcher'da \"%s\" profilini seç ve Oyna'ya bas.",
        "account.official" to "Resmi Minecraft Launcher yöntemi seçili: hesabın Minecraft Launcher'da. Burada giriş yapmana gerek yok.",
    )
    private val en = mapOf(
        "home" to "Home", "profiles" to "Profiles", "mods" to "Mods", "settings" to "Settings", "account" to "Account",
        "play" to "PLAY", "version" to "Version", "profile" to "Profile",
        "hero.title" to "DNZ Client", "hero.subtitle" to "Modern menus, HUD, Zoom and more. Powered by Sodium.",
        "news" to "News",
        "news1.title" to "DNZ Client 1.18: Modules", "news1.body" to "35 modules: keystrokes, reach, combo, target info, compass, counters, food display. Switch, drag and resize each one.",
        "news2.title" to "DNZ Schematic", "news2.body" to "Press M: see .litematic buildings as ghost blocks and find wrong or missing blocks by color.",
        "news3.title" to "Automatic Settings", "news3.body" to "The AUTO button in Settings scans your computer and tunes it for the best FPS.",
        "not_signed" to "Not signed in", "sign_in" to "Sign in with Microsoft",
        "sign_in.info" to "You sign in on Microsoft's official page. DNZ never sees your password.",
        "play.pending.title" to "Coming soon",
        "play.pending.body" to "Launching the game comes in the next stage. Microsoft sign-in opens after Mojang approval.",
        "signin.pending.body" to "Microsoft sign-in opens once Mojang approves DNZ Launcher. You always sign in on Microsoft's official page.",
        "profiles.new" to "New Profile", "profiles.selected" to "Selected",
        "mods.search" to "Search mods on Modrinth...", "mods.searching" to "Searching...", "mods.install" to "Install",
        "mods.error" to "Search failed. Check your internet connection.", "mods.downloads" to "downloads",
        "settings.ram" to "Memory (RAM)", "settings.language" to "Language", "settings.theme" to "Theme", "theme.color" to "Colorful", "theme.mono" to "Black & White", "settings.folder" to "Game folder",
        "settings.ram.hint" to "4 GB is enough for PvP. Use 6-8 GB with shaders.",
        "account.title" to "Microsoft Account",
        "account.safe1" to "Your password is only entered on Microsoft's official page.",
        "account.safe2" to "DNZ only keeps the sign-in key Microsoft gives it, encrypted.",
        "account.safe3" to "You can remove access anytime at account.microsoft.com.",
        "ok" to "OK",
        "need_account.title" to "Sign in first",
        "need_account.body" to "Sign in with Microsoft on the Account page to play. Until Mojang approves DNZ, play with the official Minecraft Launcher method in Settings.",
        "sign_out" to "Sign out",
        "running" to "Game running",
        "launch_error" to "Could not start the game",
        "settings.mode" to "Launch method",
        "mode.official" to "Official Minecraft Launcher",
        "mode.official.info" to "Recommended. Your account stays in the Minecraft Launcher; DNZ never touches it. PLAY opens the Minecraft Launcher: pick the DNZ profile there and press Play.",
        "mode.direct" to "Start directly with DNZ",
        "mode.direct.info" to "Opens the game in one click. Needs a Microsoft sign-in (after Mojang approval).",
        "official.done" to "In the Minecraft Launcher, pick the \"%s\" profile and press Play.",
        "account.official" to "Official Minecraft Launcher mode is selected: your account lives in the Minecraft Launcher. No sign-in needed here.",
    )

    private val trLibrary = mapOf(
        "lib.discover" to "Keşfet", "lib.installed_tab" to "Yüklü", "lib.profile" to "Profil", "lib.sort" to "Sırala",
        "sort.relevance" to "Önerilen", "sort.downloads" to "En çok indirilen", "sort.follows" to "En çok takip edilen",
        "sort.newest" to "En yeni", "sort.updated" to "Son güncellenen",
        "cat.all" to "Tümü", "cat.optimization" to "Optimizasyon", "cat.utility" to "Araçlar", "cat.decoration" to "Dekorasyon",
        "cat.adventure" to "Macera", "cat.equipment" to "Ekipman", "cat.game-mechanics" to "Oyun Mekaniği", "cat.library" to "Kütüphane",
        "cat.magic" to "Büyü", "cat.management" to "Yönetim", "cat.mobs" to "Moblar", "cat.social" to "Sosyal", "cat.storage" to "Depolama",
        "cat.technology" to "Teknoloji", "cat.transportation" to "Ulaşım", "cat.worldgen" to "Dünya Üretimi", "cat.food" to "Yemek",
        "cat.economy" to "Ekonomi", "cat.minigame" to "Mini Oyun",
        "lib.results" to "%s mod", "lib.load_more" to "Daha fazla göster", "lib.no_results" to "Sonuç yok. Başka bir şey ara ya da kategoriyi değiştir.",
        "lib.by" to "%s tarafından", "lib.installed_badge" to "Yüklü", "lib.installing" to "Yükleniyor...",
        "lib.resolving" to "Gerekli modlar kontrol ediliyor...", "lib.downloading" to "%s indiriliyor...",
        "lib.no_version" to "Bu modun Minecraft %s için Fabric sürümü yok.", "lib.untrusted" to "Güvenilmeyen indirme adresi.",
        "lib.bad_name" to "Geçersiz dosya adı.", "lib.install_failed" to "Yüklenemedi",
        "lib.installed_toast" to "%s, \"%p\" profiline yüklendi.", "lib.deps" to "Gerekli modlar da yüklendi:",
        "lib.restart" to "Oyun açık: değişiklikler oyunu yeniden başlatınca geçerli olur.",
        "lib.back" to "Geri", "lib.followers" to "takipçi", "lib.gallery" to "Galeri", "lib.description" to "Açıklama",
        "lib.versions" to "Sürümler (Minecraft %s)", "lib.more_versions" to "Tüm sürümleri göster (%s)",
        "lib.read_more" to "Devamını Modrinth'te oku", "lib.license" to "Lisans",
        "lib.link.modrinth" to "Modrinth", "lib.link.source" to "Kaynak Kod", "lib.link.issues" to "Hata Bildir",
        "lib.link.wiki" to "Wiki", "lib.link.discord" to "Discord",
        "lib.release" to "Kararlı", "lib.beta" to "Beta", "lib.alpha" to "Alfa",
        "lib.page_loading" to "Mod sayfası açılıyor...", "lib.page_error" to "Mod sayfası açılamadı. İnternet bağlantını kontrol et.",
        "lib.retry" to "Tekrar dene", "lib.unavailable" to "Minecraft %s için yok",
        "lib.count" to "%s mod  •  %a açık", "lib.refresh" to "Yenile", "lib.open_folder" to "Klasörü Aç",
        "lib.update_all" to "Hepsini Güncelle (%s)", "lib.update" to "Güncelle", "lib.update_to" to "Yeni sürüm: %s",
        "lib.bundled" to "DNZ ile gelir", "lib.required" to "Gerekli", "lib.not_on_modrinth" to "Modrinth'te değil",
        "lib.remove" to "Kaldır", "lib.remove_confirm.title" to "Mod kaldırılsın mı?",
        "lib.remove_confirm.body" to "\"%s\" bu profilden silinecek. İstersen sonra tekrar yükleyebilirsin.",
        "lib.removed" to "%s kaldırıldı.", "lib.scanning" to "Modlar okunuyor...",
        "lib.empty" to "Bu profilde henüz mod yok. DNZ Client, Sodium ve diğer temel modlar ilk OYNA'da otomatik kurulur. Keşfet sekmesinden istediğin modu ekleyebilirsin.",
        "lib.toggle_failed" to "Mod açılıp kapatılamadı. Oyun açıksa kapatıp tekrar dene.",
        "lib.enabled_toast" to "%s açıldı.", "lib.disabled_toast" to "%s kapatıldı.",
        "cancel" to "İptal",
        // Auto button (Settings)
        "auto.title" to "Otomatik Ayar", "auto.button" to "AUTO", "auto.scanning" to "Taranıyor...",
        "auto.caption" to "Bilgisayarının RAM'ini, işlemcisini ve ekran kartını tarar. Oyuna ayrılan RAM'i ve oyunun görüntü ayarlarını (görüş mesafesi, FPS sınırı, parçacıklar...) bilgisayarına göre, yüksek FPS ve düşük gecikme için ayarlar. Eski ayarların yedeklenir, Geri Al ile dönebilirsin.",
        "auto.your_pc" to "Bilgisayarın", "auto.changes" to "Yapılan ayarlar", "auto.unknown" to "bulunamadı",
        "auto.spec.ram" to "RAM", "auto.spec.cpu" to "İşlemci", "auto.spec.gpu" to "Ekran kartı", "auto.laptop" to "laptop",
        "auto.cores" to "%c çekirdek / %t iş parçacığı",
        "auto.tier" to "Seviye", "auto.tier.low" to "Düşük", "auto.tier.medium" to "Orta", "auto.tier.high" to "Yüksek", "auto.tier.ultra" to "Çok Yüksek",
        "auto.set.ram" to "Oyuna ayrılan RAM: %s GB",
        "auto.set.render" to "Görüş mesafesi: %s chunk (simülasyon %d)",
        "auto.set.fps" to "FPS sınırı: Sınırsız, VSync kapalı (daha az gecikme)",
        "auto.set.particles" to "Parçacıklar: %s",
        "auto.particles.0" to "Hepsi", "auto.particles.1" to "Azaltılmış", "auto.particles.2" to "En az",
        "auto.set.details" to "Bulutlar, gölgeler, yumuşak ışık: %s seviyesine göre",
        "auto.applied_to" to "Uygulanan profiller: %s",
        "auto.skipped" to "Şu an açık olduğu için atlandı: %s. Oyunu kapatıp AUTO'ya tekrar bas.",
        "mojang_notice" to "Mojang onayı alınana kadar Microsoft hesabıyla giriş kullanılamıyor. O zamana kadar Ayarlar'dan Resmi Minecraft Launcher yöntemiyle oynayabilirsin.",
        "dont_show_again" to "Bir daha gösterme",
        "auto.mac_lowpower" to "Mac'inde Düşük Güç Modu açık: Mac bilerek yavaş çalışıyor ve FPS çok düşüyor. Sistem Ayarları → Pil → Düşük Güç Modu'nu \"Asla\" yap.",
        "auto.battery" to "Mac şu an pilden çalışıyor. Şarja takınca işlemci ve ekran kartı tam güçle çalışır, FPS belirgin şekilde artar.",
        "auto.hybrid" to "Bilgisayarında 2 ekran kartı var. Minecraft'ın güçlü kartı (%s) kullandığından emin ol: Windows Grafik Ayarları'nda Java (javaw.exe) için \"Yüksek performans\"ı seç.",
        "auto.open_graphics" to "Grafik Ayarlarını Aç", "auto.undo" to "Geri Al",
        "auto.undone" to "Eski ayarlara dönüldü.", "auto.done_toast" to "Auto: %s seviye ayarlar uygulandı.",
        "auto.error" to "Tarama başarısız: %s",
        "startup.title" to "Bilgisayar açılınca başlat",
        "startup.caption" to "Bilgisayar açılınca DNZ Launcher arka planda, görünmeden başlar; kısayola basınca anında açılır. Açıkken ✕ launcher'ı kapatmaz, gizler: DNZ simgesinden (Windows'ta sağ altta, Mac'te üst menü çubuğunda) açabilir veya çıkabilirsin. Arka planda yaklaşık 200 MB RAM kullanır.",
        "startup.failed" to "Başlangıç ayarı değiştirilemedi.",
        "tray.open" to "DNZ Launcher'ı Aç", "tray.quit" to "Çıkış",
        "ms.step1" to "1. Bu kodu kopyala (zaten kopyalandı):", "ms.step2" to "2. Açılan Microsoft sayfasına (microsoft.com/link) kodu yapıştır ve hesabınla giriş yap. Şifreni DNZ görmez.",
        "ms.open" to "Kodu Kopyala ve Sayfayı Aç", "ms.reopen" to "Sayfayı Tekrar Aç", "ms.use_code" to "Sayfa açılmadı mı? Kodla giriş yap",
        "ms.browser" to "Tarayıcında Microsoft'un giriş sayfası açıldı. Hesabını seç ve giriş yap. Şifreni DNZ görmez.",
"ms.waiting" to "Giriş bekleniyor...", "ms.success" to "%s olarak giriş yapıldı.",
        "ms.err.not_approved" to "Microsoft girişi çalıştı ama Mojang, DNZ Launcher'ı henüz onaylamadı. Onay gelince tekrar dene. O zamana kadar Ayarlar'dan Resmi Minecraft Launcher yöntemiyle oynayabilirsin.",
        "ms.err.no_xbox" to "Bu hesabın Xbox profili yok. xbox.com'a bir kez giriş yapıp profil oluştur, sonra tekrar dene.", "ms.err.child" to "Bu hesap 18 yaş altı: bir yetişkinin Microsoft aile ayarlarından izin vermesi gerekiyor.",
        "ms.err.no_minecraft" to "Bu Microsoft hesabında Minecraft: Java Edition yok.", "ms.err.expired" to "Kodun süresi doldu. Tekrar dene.", "ms.err.other" to "Giriş yapılamadı: %s",
        "profiles.edit" to "Profili Düzenle", "profiles.mods" to "%s mod", "profiles.ram_default" to "varsayılan", "profiles.duplicate" to "Kopyala", "profiles.delete" to "Sil",
        "profiles.last" to "Son profil silinemez.", "profiles.copy_suffix" to "(kopya)", "profiles.duplicated" to "%s oluşturuldu (dünyalar hariç her şey kopyalandı).",
        "profiles.picture" to "Resim Seç", "profiles.picture_remove" to "Resmi Kaldır", "profiles.name" to "Ad", "profiles.description" to "Açıklama",
        "profiles.version_warn" to "Sürüm değişince modlar bir sonraki OYNA'da o sürüme göre yeniden kurulur. Dünyaların yedeğini almak iyi olur.",
        "profiles.ram_hint" to "En sola çekersen Ayarlar'daki RAM kullanılır.",
        "profiles.delete_title" to "\"%s\" silinsin mi?", "profiles.delete_body" to "Profil listeden kaldırılır. Dosyaları (dünyalar, modlar, ayarlar) istersen Geri Dönüşüm Kutusu'na gönderilir, oradan geri alabilirsin.",
        "profiles.delete_files" to "Dosyalarını da Geri Dönüşüm Kutusu'na gönder", "profiles.delete_running" to "Oyun açıkken bu profil silinemez.",
        "profiles.deleted" to "%s silindi.", "profiles.trash_failed" to "%s listeden kaldırıldı ama dosyaları Geri Dönüşüm Kutusu'na gönderilemedi.",
        "servers" to "Sunucular", "servers.refresh" to "Yenile", "servers.add" to "Sunucu Ekle", "servers.profile" to "Profil:",
        "servers.empty" to "Bu profilde kayıtlı sunucu yok. Sunucu Ekle ile ekleyebilirsin (oyunun Multiplayer listesiyle aynı liste).",
        "servers.add_title" to "Yeni sunucu", "servers.name" to "Ad (örn. DonutSMP)", "servers.address" to "Adres (örn. donutsmp.net)", "servers.save" to "Kaydet",
        "servers.added" to "%s eklendi.", "servers.save_failed" to "Sunucu kaydedilemedi. Oyun açıksa kapatıp tekrar dene.",
        "servers.offline" to "Ulaşılamıyor (kapalı ya da adres yanlış)", "servers.pinging" to "Bağlanılıyor...", "servers.join" to "Katıl",
        "servers.remove.title" to "Sunucu silinsin mi?", "servers.remove.body" to "\"%s\" bu profilin sunucu listesinden silinecek.",
        "servers.game_open" to "Oyun zaten açık. Sunucuya oyunun içinden gir.", "servers.joining" to "%s için oyun açılıyor, ana menü gelince otomatik girilecek.",
        "acc.official_title" to "Hesaplar", "acc.dnz_title" to "Hesaplar", "acc.none" to "Hesap bulunamadı",
        "acc.add" to "Hesap ekle", "acc.page" to "Hesap sayfası", "acc.switched" to "Hesap değişti: %s.", "acc.mode_changed" to "Başlatma yöntemi Resmi Minecraft Launcher yapıldı.",
        "acc.add_hint" to "Minecraft Launcher açıldı: sol üstteki hesap adına tıklayıp yeni hesabı ekle, sonra buraya dön.",
        "acc.switch_failed.title" to "Hesap değiştirilemedi",
        "acc.switch_failed.body" to "Minecraft Launcher açıkken hesap değiştirilemez (kapanırken eski hesabı geri yazar). Minecraft Launcher'ı kapatıp tekrar dene.",
        // Import (Profiles)
        "import.title" to "Başka yerden içe aktar",
        "import.caption" to "Bilgisayarındaki diğer Minecraft kurulumlarını (başka launcher'lar, mod siteleri, resmi launcher) bulur. Oyun ayarlarını, modlarını, sunucularını ve paketlerini seçtiğin DNZ profiline taşır. Oradaki hiçbir şey değişmez veya silinmez.",
        "import.scan" to "TARA", "import.scanning" to "Taranıyor...", "import.rescan" to "Tekrar Tara",
        "import.none" to "Başka bir Minecraft kurulumu bulunamadı.",
        "import.found" to "%s kurulum bulundu. Birini seç:",
        "import.mods" to "%s mod", "import.settings" to "Ayarlar", "import.servers" to "%s sunucu", "import.packs" to "%s paket",
        "import.mods_only" to "Mod klasörü", "import.version" to "MC %s",
        "import.target" to "Hangi profile aktarılsın?", "import.what" to "Neler taşınsın?",
        "part.options" to "Oyun ayarları (tuşlar, fare hassasiyeti, FOV, ses...)",
        "part.mods" to "Modlar (profilin Minecraft sürümüne uygun hali indirilir)",
        "part.servers" to "Sunucu listesi (mevcut sunucular kalır)",
        "part.packs" to "Kaynak paketleri ve shader'lar",
        "part.configs" to "Mod ayarları (sadece profilde olmayanlar)",
        "import.button" to "İÇE AKTAR", "import.working" to "Aktarılıyor...",
        "import.step.options" to "Oyun ayarları kopyalanıyor...", "import.step.servers" to "Sunucular ekleniyor...",
        "import.step.packs" to "Paketler kopyalanıyor...", "import.step.configs" to "Mod ayarları kopyalanıyor...",
        "import.step.mods_check" to "Modlar Modrinth'te aranıyor...",
        "import.running" to "\"%s\" profilinin oyunu şu an açık. Oyunu kapatıp tekrar dene.",
        "import.done_toast" to "İçe aktarma bitti: \"%s\"",
        "import.r.title" to "Sonuç",
        "import.r.options" to "Oyun ayarları taşındı (eski ayarlar yedeklendi)",
        "import.r.servers" to "%s sunucu eklendi", "import.r.packs" to "%s paket kopyalandı",
        "import.r.configs" to "%s mod ayar dosyası kopyalandı",
        "import.r.mods" to "%s mod yüklendi", "import.r.already" to "%s mod profilde zaten vardı",
        "import.r.no_version" to "Minecraft %v için sürümü yok: %s",
        "import.r.unknown" to "Modrinth'te bulunamadığı için taşınamadı: %s",
        "import.r.errors" to "Sorun çıkan adımlar: %s",
        "import.r.nothing" to "Taşınacak yeni bir şey yoktu.",
    )
    private val enLibrary = mapOf(
        "lib.discover" to "Discover", "lib.installed_tab" to "Installed", "lib.profile" to "Profile", "lib.sort" to "Sort",
        "sort.relevance" to "Relevance", "sort.downloads" to "Most downloaded", "sort.follows" to "Most followed",
        "sort.newest" to "Newest", "sort.updated" to "Recently updated",
        "cat.all" to "All", "cat.optimization" to "Optimization", "cat.utility" to "Utility", "cat.decoration" to "Decoration",
        "cat.adventure" to "Adventure", "cat.equipment" to "Equipment", "cat.game-mechanics" to "Game Mechanics", "cat.library" to "Library",
        "cat.magic" to "Magic", "cat.management" to "Management", "cat.mobs" to "Mobs", "cat.social" to "Social", "cat.storage" to "Storage",
        "cat.technology" to "Technology", "cat.transportation" to "Transportation", "cat.worldgen" to "World Generation", "cat.food" to "Food",
        "cat.economy" to "Economy", "cat.minigame" to "Minigame",
        "lib.results" to "%s mods", "lib.load_more" to "Show more", "lib.no_results" to "No results. Try another search or category.",
        "lib.by" to "by %s", "lib.installed_badge" to "Installed", "lib.installing" to "Installing...",
        "lib.resolving" to "Checking required mods...", "lib.downloading" to "Downloading %s...",
        "lib.no_version" to "This mod has no Fabric version for Minecraft %s.", "lib.untrusted" to "Untrusted download address.",
        "lib.bad_name" to "Invalid file name.", "lib.install_failed" to "Could not install",
        "lib.installed_toast" to "%s installed to the \"%p\" profile.", "lib.deps" to "Required mods were installed too:",
        "lib.restart" to "The game is running: changes apply after a restart.",
        "lib.back" to "Back", "lib.followers" to "followers", "lib.gallery" to "Gallery", "lib.description" to "Description",
        "lib.versions" to "Versions (Minecraft %s)", "lib.more_versions" to "Show all versions (%s)",
        "lib.read_more" to "Read more on Modrinth", "lib.license" to "License",
        "lib.link.modrinth" to "Modrinth", "lib.link.source" to "Source", "lib.link.issues" to "Report issue",
        "lib.link.wiki" to "Wiki", "lib.link.discord" to "Discord",
        "lib.release" to "Release", "lib.beta" to "Beta", "lib.alpha" to "Alpha",
        "lib.page_loading" to "Opening mod page...", "lib.page_error" to "Could not open the mod page. Check your internet connection.",
        "lib.retry" to "Try again", "lib.unavailable" to "Not available for Minecraft %s",
        "lib.count" to "%s mods  •  %a on", "lib.refresh" to "Refresh", "lib.open_folder" to "Open Folder",
        "lib.update_all" to "Update All (%s)", "lib.update" to "Update", "lib.update_to" to "New version: %s",
        "lib.bundled" to "Comes with DNZ", "lib.required" to "Required", "lib.not_on_modrinth" to "Not on Modrinth",
        "lib.remove" to "Remove", "lib.remove_confirm.title" to "Remove this mod?",
        "lib.remove_confirm.body" to "\"%s\" will be deleted from this profile. You can install it again later.",
        "lib.removed" to "%s removed.", "lib.scanning" to "Reading mods...",
        "lib.empty" to "No mods in this profile yet. DNZ Client, Sodium and the other base mods are installed on the first PLAY. Add any mod from the Discover tab.",
        "lib.toggle_failed" to "Could not turn the mod on or off. If the game is running, close it and try again.",
        "lib.enabled_toast" to "%s turned on.", "lib.disabled_toast" to "%s turned off.",
        "cancel" to "Cancel",
        // Auto button (Settings)
        "auto.title" to "Automatic Settings", "auto.button" to "AUTO", "auto.scanning" to "Scanning...",
        "auto.caption" to "Scans your computer's RAM, processor and graphics card. Sets the game's RAM and graphics settings (render distance, FPS limit, particles...) to fit your computer, for high FPS and low input delay. Your old settings are backed up; Undo brings them back.",
        "auto.your_pc" to "Your computer", "auto.changes" to "Changed settings", "auto.unknown" to "not found",
        "auto.spec.ram" to "RAM", "auto.spec.cpu" to "Processor", "auto.spec.gpu" to "Graphics card", "auto.laptop" to "laptop",
        "auto.cores" to "%c cores / %t threads",
        "auto.tier" to "Level", "auto.tier.low" to "Low", "auto.tier.medium" to "Medium", "auto.tier.high" to "High", "auto.tier.ultra" to "Ultra",
        "auto.set.ram" to "RAM for the game: %s GB",
        "auto.set.render" to "Render distance: %s chunks (simulation %d)",
        "auto.set.fps" to "FPS limit: Unlimited, VSync off (less input delay)",
        "auto.set.particles" to "Particles: %s",
        "auto.particles.0" to "All", "auto.particles.1" to "Decreased", "auto.particles.2" to "Minimal",
        "auto.set.details" to "Clouds, shadows, smooth lighting: set for the %s level",
        "auto.applied_to" to "Applied to profiles: %s",
        "auto.skipped" to "Skipped because it is open right now: %s. Close the game and press AUTO again.",
        "mojang_notice" to "Signing in with a Microsoft account is not available until Mojang approves DNZ. Until then, play with the official Minecraft Launcher method in Settings.",
        "dont_show_again" to "Don't show again",
        "auto.mac_lowpower" to "Low Power Mode is on: your Mac runs slower on purpose and FPS drops a lot. Set System Settings → Battery → Low Power Mode to \"Never\".",
        "auto.battery" to "Your Mac is running on battery. Plugged in, the CPU and graphics run at full power and FPS goes up noticeably.",
        "auto.hybrid" to "Your computer has 2 graphics cards. Make sure Minecraft uses the strong one (%s): in Windows Graphics settings choose \"High performance\" for Java (javaw.exe).",
        "auto.open_graphics" to "Open Graphics Settings", "auto.undo" to "Undo",
        "auto.undone" to "Old settings restored.", "auto.done_toast" to "Auto: %s level settings applied.",
        "auto.error" to "Scan failed: %s",
        "startup.title" to "Start with the computer",
        "startup.caption" to "DNZ Launcher starts hidden in the background when the computer starts, so the shortcut opens it instantly. While on, ✕ hides the launcher instead of closing it: open it or quit from the DNZ icon (bottom right on Windows, menu bar on Mac). Uses about 200 MB of RAM in the background.",
        "startup.failed" to "Could not change the startup setting.",
        "tray.open" to "Open DNZ Launcher", "tray.quit" to "Quit",
        "ms.step1" to "1. Copy this code (already copied):", "ms.step2" to "2. Paste it on the Microsoft page that opened (microsoft.com/link) and sign in. DNZ never sees your password.",
        "ms.open" to "Copy Code and Open Page", "ms.reopen" to "Open Page Again", "ms.use_code" to "Page did not open? Sign in with a code",
        "ms.browser" to "Microsoft's sign-in page opened in your browser. Pick your account and sign in. DNZ never sees your password.",
"ms.waiting" to "Waiting for sign-in...", "ms.success" to "Signed in as %s.",
        "ms.err.not_approved" to "Microsoft sign-in worked, but Mojang has not approved DNZ Launcher yet. Try again once it is approved; until then play with the official Minecraft Launcher method (Settings).",
        "ms.err.no_xbox" to "This account has no Xbox profile. Sign in to xbox.com once to create it, then try again.", "ms.err.child" to "This account is under 18: an adult has to allow it in Microsoft family settings.",
        "ms.err.no_minecraft" to "This Microsoft account does not own Minecraft: Java Edition.", "ms.err.expired" to "The code expired. Try again.", "ms.err.other" to "Could not sign in: %s",
        "profiles.edit" to "Edit Profile", "profiles.mods" to "%s mods", "profiles.ram_default" to "default", "profiles.duplicate" to "Duplicate", "profiles.delete" to "Delete",
        "profiles.last" to "The last profile can't be deleted.", "profiles.copy_suffix" to "(copy)", "profiles.duplicated" to "%s created (everything but worlds was copied).",
        "profiles.picture" to "Pick Picture", "profiles.picture_remove" to "Remove Picture", "profiles.name" to "Name", "profiles.description" to "Description",
        "profiles.version_warn" to "After a version change the mods are reinstalled for it on the next PLAY. Backing up your worlds is a good idea.",
        "profiles.ram_hint" to "All the way left = the RAM from Settings.",
        "profiles.delete_title" to "Delete \"%s\"?", "profiles.delete_body" to "The profile is removed from the list. Its files (worlds, mods, settings) can go to the Recycle Bin, where you can get them back.",
        "profiles.delete_files" to "Also move its files to the Recycle Bin", "profiles.delete_running" to "This profile can't be deleted while the game is open.",
        "profiles.deleted" to "%s deleted.", "profiles.trash_failed" to "%s was removed from the list, but its files could not be moved to the Recycle Bin.",
        "servers" to "Servers", "servers.refresh" to "Refresh", "servers.add" to "Add Server", "servers.profile" to "Profile:",
        "servers.empty" to "No servers saved in this profile yet. Add one with Add Server (it is the same list as the game's Multiplayer screen).",
        "servers.add_title" to "New server", "servers.name" to "Name (e.g. DonutSMP)", "servers.address" to "Address (e.g. donutsmp.net)", "servers.save" to "Save",
        "servers.added" to "%s added.", "servers.save_failed" to "Could not save the server. If the game is open, close it and try again.",
        "servers.offline" to "Can't reach it (offline or wrong address)", "servers.pinging" to "Connecting...", "servers.join" to "Join",
        "servers.remove.title" to "Remove this server?", "servers.remove.body" to "\"%s\" will be removed from this profile's server list.",
        "servers.game_open" to "The game is already open. Join the server from inside the game.", "servers.joining" to "Starting the game for %s; it joins automatically at the main menu.",
        "acc.official_title" to "Accounts", "acc.dnz_title" to "Accounts", "acc.none" to "No accounts found",
        "acc.add" to "Add account", "acc.page" to "Account page", "acc.switched" to "Switched to %s.", "acc.mode_changed" to "Launch method set to the official Minecraft Launcher.",
        "acc.add_hint" to "The Minecraft Launcher is open: click the account name at the top left to add the new account, then come back here.",
        "acc.switch_failed.title" to "Could not switch account",
        "acc.switch_failed.body" to "Accounts can't be switched while the Minecraft Launcher is open (it writes the old account back when it closes). Close it and try again.",
        // Import (Profiles)
        "import.title" to "Import from elsewhere",
        "import.caption" to "Finds the other Minecraft installs on your computer (other launchers, mod platforms, the official launcher). Moves their game settings, mods, servers and packs into the DNZ profile you pick. Nothing there is changed or deleted.",
        "import.scan" to "SCAN", "import.scanning" to "Scanning...", "import.rescan" to "Scan again",
        "import.none" to "No other Minecraft install was found.",
        "import.found" to "%s installs found. Pick one:",
        "import.mods" to "%s mods", "import.settings" to "Settings", "import.servers" to "%s servers", "import.packs" to "%s packs",
        "import.mods_only" to "Mods folder", "import.version" to "MC %s",
        "import.target" to "Import into which profile?", "import.what" to "What to bring over?",
        "part.options" to "Game settings (keys, mouse sensitivity, FOV, sound...)",
        "part.mods" to "Mods (the version for the profile's Minecraft is downloaded)",
        "part.servers" to "Server list (your current servers stay)",
        "part.packs" to "Resource packs and shaders",
        "part.configs" to "Mod settings (only ones the profile doesn't have)",
        "import.button" to "IMPORT", "import.working" to "Importing...",
        "import.step.options" to "Copying game settings...", "import.step.servers" to "Adding servers...",
        "import.step.packs" to "Copying packs...", "import.step.configs" to "Copying mod settings...",
        "import.step.mods_check" to "Looking up mods on Modrinth...",
        "import.running" to "The game of the \"%s\" profile is open right now. Close it and try again.",
        "import.done_toast" to "Import finished: \"%s\"",
        "import.r.title" to "Result",
        "import.r.options" to "Game settings moved (old settings backed up)",
        "import.r.servers" to "%s servers added", "import.r.packs" to "%s packs copied",
        "import.r.configs" to "%s mod settings files copied",
        "import.r.mods" to "%s mods installed", "import.r.already" to "%s mods were already in the profile",
        "import.r.no_version" to "No version for Minecraft %v: %s",
        "import.r.unknown" to "Not found on Modrinth, so not moved: %s",
        "import.r.errors" to "Steps with problems: %s",
        "import.r.nothing" to "There was nothing new to bring over.",
    )

    /** Language used by code that has no LauncherState at hand (e.g. install messages). */
    @Volatile
    var currentLang = Lang.TR

    fun current(key: String): String = get(currentLang, key)

    fun get(lang: Lang, key: String): String =
        (if (lang == Lang.TR) tr[key] ?: trLibrary[key] else en[key] ?: enLibrary[key]) ?: key
}
