# Privacy Policy

*Applies to DNZ Client, DNZ Launcher, DNZ Schematic and the website dnzclient.com.
Effective date: 6 October 2026.*

## Summary

This program will not transfer any information to other networked systems unless specifically requested by the
user or the person installing or operating it.

- There is no tracking, no analytics, no advertising and no telemetry. DNZ servers only store what you choose to send:
  the optional **Cloud save** (off by default, see section 3a).
- Everything DNZ needs to remember is stored **only on your computer**.
- DNZ only connects to the services you use through it (Microsoft, Mojang, Modrinth...), and only to do what you ask.
- DNZ is free, open source software (GPL-3.0). Anyone can check these statements in the source code:
  https://github.com/DnzMc/DnzClient

## 1. Who we are

DNZ Client is an independent open source project (DNZ). It is not affiliated with Mojang AB or Microsoft.
"Minecraft" is a trademark of Mojang AB / Microsoft.

## 2. Information DNZ does not collect

DNZ does not collect, sell, rent or share personal information. We do not receive your name, e-mail address,
IP address, hardware details, chat messages, gameplay, screenshots or crash reports. The apps contain no analytics,
advertising or tracking libraries.

## 3. Information stored on your computer only

The following is saved in the `.dnzlauncher` folder in your user folder (and, for the Minecraft game, in its game
folders) and is never sent to DNZ (except the settings listed in section 3a, only if you turn on Cloud save):

- **Minecraft sign-in.** When you sign in with Microsoft, you enter your password on Microsoft's own page in your
  browser; DNZ never sees it. DNZ keeps only the sign-in token Microsoft gives back, so you stay signed in. On
  Windows it is encrypted with Windows' own data protection (only your Windows user on this computer can read it);
  on macOS it is kept in the macOS Keychain. Signing out deletes it.
- **Accounts of the official Minecraft Launcher.** If you choose to start the game through the official launcher,
  DNZ reads the names and profile IDs of the accounts in that launcher's files, to show them and to switch the active
  account when you ask. It does not read or copy passwords or tokens from there.
- **Profiles and settings:** your game profiles, launcher settings, client settings, HUD layout, installed mods,
  servers list, worlds and screenshots.
- **PC scan:** when you use the automatic settings, DNZ looks at your RAM, graphics card and screen to pick good game
  settings. The result stays on your computer.
- **Script mods** you add run inside the game, in a sandbox without access to your files or the internet.

## 3a. Cloud save (optional, off by default)

Only if you turn on **Cloud save** (DNZ Launcher → Settings, or DNZ menu → Preferences), DNZ Client stores a copy of
these settings on DNZ's server (cloud.dnzclient.com, run on Cloudflare) so they load on your other computers:
your key bindings and a few control options (mouse sensitivity, toggle sprint/crouch, auto-jump, invert mouse, raw
input) from options.txt, and your DNZ Client settings (menu look, HUD layout, modules, theme). The copy is stored
under your Minecraft player ID (UUID).

- **Sign-in without tokens:** the game signs a one-time challenge from DNZ's server with its Mojang-signed player
  certificate (the same key Minecraft uses to sign chat messages); DNZ's server checks Mojang's signature. Your
  Minecraft access token, Microsoft password and e-mail address never reach DNZ.
- Nothing else is sent: no worlds, chat, screenshots, servers list or other game options.
- Turn it off at any time; the copy is then no longer updated. To delete the stored copy, write to info@dnzclient.com
  with your Minecraft name (a delete button in the apps is planned).

## 3b. Friends

Your friend list is saved only on your computer (config/dnzclient-friends.json). DNZ uses it to mark friends in the
player list of the server you are on; nothing about it is sent anywhere.

## 4. Services DNZ connects to

DNZ connects to these services only when needed for what you do. They receive the usual technical information of
any internet request (such as your IP address) and have their own privacy policies:

| Service | Why |
|---|---|
| Microsoft / Xbox Live (login.microsoftonline.com, xboxlive.com) | Signing in with your Microsoft account |
| Mojang / Minecraft (minecraftservices.com, mojang.com, minecraft.net) | Checking your Minecraft account, skins, downloading the game |
| Fabric (fabricmc.net) | Downloading the Fabric mod loader |
| Modrinth (modrinth.com) | Mod library: searching and downloading mods, the performance mods of the DNZ pack |
| Adoptium (adoptium.net, macOS only) | Downloading Java on the first start of the macOS app |
| DNZ downloads (download.dnzclient.com, Cloudflare) | Checking for and downloading DNZ Launcher updates (signed by DNZ) |
| DNZ Cloud (cloud.dnzclient.com, Cloudflare) | Only with Cloud save on (section 3a) |
| e4mc relay (e4mc.link) | Only when you open your world with Open World: friends join through this relay |
| Minecraft servers you join, websites you open | Because you chose to |

## 5. Changes to your system

DNZ Launcher only changes your system where needed for a feature:

- **Installing:** files go to your user folder (no administrator rights); shortcuts are created only if you tick them.
  Uninstalling removes the program; your profiles stay in `.dnzlauncher` until you delete that folder.
- **Start with Windows** (only if you turn it on): adds DNZ Launcher to your Windows startup list.
- **Graphics card:** when you start the game, DNZ asks Windows to run the game's Java on your dedicated graphics
  card for better FPS (Windows' own graphics setting for apps; you can change it there at any time).

## 6. Website (dnzclient.com)

- The website is hosted by Cloudflare, which processes standard request logs (such as IP addresses) to deliver and
  protect the site.
- Fonts are loaded from Google Fonts, so your browser connects to Google's font servers.
- If you click "Don't show again" on the notice, your browser remembers that choice in its local storage. We use no
  cookies, analytics or advertising on the website.

## 7. Children

DNZ does not knowingly collect any information from anyone, including children. Signing in to Minecraft is handled
by Microsoft under Microsoft's rules for child accounts.

## 8. Your choices and rights

Because DNZ does not collect personal data, there is no data about you held by DNZ to access, correct or delete.
You stay in control of the data on your computer:

- Sign out in DNZ Launcher to delete the saved sign-in.
- Uninstall DNZ Launcher and delete the `.dnzlauncher` folder to remove everything.
- For data held by Microsoft, Mojang, Modrinth or other services, use their own privacy tools.

These rights apply under data protection laws such as the GDPR (EU) and KVKK (Türkiye).

## 9. Changes to this policy

If this policy changes, the new version is published on this page and in the source code repository with a new
effective date. Changes will never introduce data collection without making it clear in the app first.

## 10. Contact

Questions about privacy: e-mail **info@dnzclient.com**, or open an issue at https://github.com/DnzMc/DnzClient/issues.

---

# Gizlilik Politikası

*DNZ Client, DNZ Launcher, DNZ Schematic ve dnzclient.com web sitesi için geçerlidir.
Yürürlük tarihi: 6 Ekim 2026.*

## Özet

Bu program, kullanıcı ya da programı kuran/kullanan kişi özellikle istemedikçe hiçbir bilgiyi başka sistemlere
göndermez.

- Takip, analiz, reklam ya da kullanım verisi toplama yoktur. DNZ sunucuları sadece senin göndermeyi seçtiklerini
  saklar: isteğe bağlı **Bulut kaydetme** (varsayılan olarak kapalı, bkz. bölüm 3a).
- DNZ'nin hatırlaması gereken her şey **sadece senin bilgisayarında** saklanır.
- DNZ yalnızca senin kullandığın hizmetlere (Microsoft, Mojang, Modrinth...) ve sadece istediğin iş için bağlanır.
- DNZ ücretsiz ve açık kaynaklıdır (GPL-3.0). Bu yazılanları herkes kaynak kodunda kontrol edebilir:
  https://github.com/DnzMc/DnzClient

## 1. Biz kimiz

DNZ Client bağımsız bir açık kaynak projesidir (DNZ). Mojang AB ya da Microsoft ile bağlantılı değildir.
"Minecraft", Mojang AB / Microsoft'un markasıdır.

## 2. DNZ'nin toplamadığı bilgiler

DNZ kişisel bilgi toplamaz, satmaz, kiralamaz ve paylaşmaz. Adın, e-postan, IP adresin, donanım bilgilerin,
sohbet mesajların, oyun içi davranışın, ekran görüntülerin ya da çökme raporların bize gelmez. Uygulamalarda
analiz, reklam ya da takip kütüphanesi yoktur.

## 3. Sadece bilgisayarında saklananlar

Aşağıdakiler kullanıcı klasöründeki `.dnzlauncher` klasöründe (oyun için de oyunun klasörlerinde) saklanır ve
DNZ'ye hiç gönderilmez (Bulut kaydetmeyi açarsan bölüm 3a'daki ayarlar hariç):

- **Minecraft girişi.** Microsoft ile giriş yaparken şifreni tarayıcında Microsoft'un kendi sayfasına yazarsın;
  DNZ şifreni hiç görmez. DNZ sadece Microsoft'un geri verdiği giriş anahtarını saklar ki tekrar giriş yapman
  gerekmesin. Windows'ta bu anahtar Windows'un kendi veri korumasıyla şifrelenir (sadece bu bilgisayardaki senin
  Windows kullanıcın okuyabilir); macOS'te Anahtar Zinciri'nde tutulur. Çıkış yapınca silinir.
- **Resmi Minecraft Launcher'daki hesaplar.** Oyunu resmi launcher üzerinden başlatmayı seçersen DNZ, o launcher'ın
  dosyalarındaki hesapların adlarını ve profil kimliklerini okur; bunları göstermek ve istediğinde etkin hesabı
  değiştirmek için. Oradan şifre ya da giriş anahtarı okumaz, kopyalamaz.
- **Profiller ve ayarlar:** oyun profillerin, launcher ve client ayarların, HUD düzenin, yüklü modların, sunucu
  listen, dünyaların ve ekran görüntülerin.
- **Bilgisayar taraması:** otomatik ayarları kullanınca DNZ, iyi oyun ayarları seçmek için RAM'ine, ekran kartına ve
  ekranına bakar. Sonuç bilgisayarında kalır.
- **Script modlar** oyunun içinde, dosyalarına ve internete erişemeyen kapalı bir alanda çalışır.

## 3a. Bulut kaydetme (isteğe bağlı, varsayılan olarak kapalı)

Sadece **Bulut kaydetme**yi açarsan (DNZ Launcher → Ayarlar ya da DNZ menüsü → Tercihler), DNZ Client şu ayarların
bir kopyasını DNZ'nin sunucusunda (cloud.dnzclient.com, Cloudflare üzerinde) saklar ki diğer bilgisayarlarında da
yüklensin: options.txt'deki tuş atamaların ve birkaç kontrol ayarı (fare hassasiyeti, koşma/eğilme açma-kapama,
otomatik zıplama, fareyi ters çevirme, ham fare girişi) ile DNZ Client ayarların (menü görünümü, HUD düzeni,
modüller, tema). Kopya Minecraft oyuncu kimliğinle (UUID) saklanır.

- **Anahtarsız giriş:** oyun, DNZ sunucusundan gelen tek kullanımlık bir soruyu Mojang imzalı oyuncu sertifikasıyla
  imzalar (Minecraft'ın sohbet mesajlarını imzaladığı anahtarın aynısı); DNZ sunucusu Mojang'ın imzasını kontrol
  eder. Minecraft giriş anahtarın, Microsoft şifren ve e-posta adresin DNZ'ye hiç gelmez.
- Başka hiçbir şey gönderilmez: dünyalar, sohbet, ekran görüntüleri, sunucu listesi ya da diğer oyun ayarları gitmez.
- İstediğin zaman kapatabilirsin; kopya o zaman güncellenmez. Saklanan kopyanın silinmesi için Minecraft adınla
  info@dnzclient.com adresine yaz (uygulamalara silme düğmesi eklenecek).

## 3b. Arkadaşlar

Arkadaş listen sadece bilgisayarında saklanır (config/dnzclient-friends.json). DNZ bunu, girdiğin sunucunun oyuncu
listesinde arkadaşlarını işaretlemek için kullanır; hiçbir yere gönderilmez.

## 4. DNZ'nin bağlandığı hizmetler

DNZ bu hizmetlere sadece yaptığın iş için gerektiğinde bağlanır. Bu hizmetler her internet isteğindeki olağan teknik
bilgileri (örneğin IP adresi) alır ve kendi gizlilik politikaları vardır:

| Hizmet | Neden |
|---|---|
| Microsoft / Xbox Live | Microsoft hesabınla giriş |
| Mojang / Minecraft | Minecraft hesabının kontrolü, skinler, oyunun indirilmesi |
| Fabric (fabricmc.net) | Fabric mod yükleyicisinin indirilmesi |
| Modrinth (modrinth.com) | Mod kütüphanesi: mod arama ve indirme, DNZ paketindeki performans modları |
| Adoptium (sadece macOS) | macOS uygulamasının ilk açılışında Java'nın indirilmesi |
| DNZ indirmeleri (download.dnzclient.com, Cloudflare) | DNZ Launcher güncellemelerini kontrol etme ve indirme (DNZ imzalı) |
| DNZ Cloud (cloud.dnzclient.com, Cloudflare) | Sadece Bulut kaydetme açıksa (bölüm 3a) |
| e4mc aktarıcısı (e4mc.link) | Sadece dünyanı Open World ile açınca: arkadaşların bu aktarıcı üzerinden katılır |
| Girdiğin Minecraft sunucuları, açtığın siteler | Sen seçtiğin için |

## 5. Sisteminde yapılan değişiklikler

DNZ Launcher sisteminde sadece bir özellik için gerektiğinde değişiklik yapar:

- **Kurulum:** dosyalar kullanıcı klasörüne kurulur (yönetici izni gerekmez); kısayollar sadece işaretlersen oluşur.
  Kaldırınca program silinir; profillerin `.dnzlauncher` klasörünü silene kadar kalır.
- **Windows ile başlat** (sadece açarsan): DNZ Launcher'ı Windows'un başlangıç listesine ekler.
- **Ekran kartı:** oyunu başlatınca, daha yüksek FPS için Windows'tan oyunun Java'sını güçlü ekran kartında
  çalıştırmasını ister (Windows'un uygulamalar için grafik ayarı; oradan istediğin zaman değiştirebilirsin).

## 6. Web sitesi (dnzclient.com)

- Site Cloudflare'de barınır; Cloudflare siteyi sunmak ve korumak için olağan istek kayıtlarını (IP adresi gibi) işler.
- Yazı tipleri Google Fonts'tan yüklenir, yani tarayıcın Google'ın yazı tipi sunucularına bağlanır.
- Uyarı penceresinde "Don't show again"e basarsan bu seçim tarayıcının yerel depolamasında saklanır. Sitede çerez,
  analiz ya da reklam kullanmıyoruz.

## 7. Çocuklar

DNZ, çocuklar dahil kimseden bilerek bilgi toplamaz. Minecraft girişi, Microsoft'un çocuk hesapları kurallarıyla
Microsoft tarafından yapılır.

## 8. Seçimlerin ve hakların

DNZ kişisel veri toplamadığı için, DNZ'de senin hakkında görebileceğin, düzeltebileceğin ya da sildirebileceğin
bir veri yoktur. Bilgisayarındaki veriler tamamen senin kontrolündedir:

- Kayıtlı girişi silmek için DNZ Launcher'da çıkış yap.
- Her şeyi silmek için DNZ Launcher'ı kaldır ve `.dnzlauncher` klasörünü sil.
- Microsoft, Mojang, Modrinth ya da diğer hizmetlerdeki verilerin için onların gizlilik araçlarını kullan.

Bu haklar GDPR (AB) ve KVKK (Türkiye) gibi kişisel verilerin korunması yasaları kapsamındadır.

## 9. Bu politikadaki değişiklikler

Politika değişirse yeni hali yeni yürürlük tarihiyle bu sayfada ve kaynak kodu deposunda yayınlanır. Bir değişiklik,
uygulamada açıkça gösterilmeden hiçbir zaman veri toplamaya başlamaz.

## 10. İletişim

Gizlilikle ilgili sorular için: **info@dnzclient.com** adresine e-posta gönder ya da
https://github.com/DnzMc/DnzClient/issues adresinde bir konu aç.
