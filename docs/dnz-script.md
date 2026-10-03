# DNZ Script – Mod Yazma Rehberi (API v1)

DNZ Script, DNZ Client için **JavaScript** ile küçük modlar yazmanı sağlar.
Modlar güvenli bir kutuda çalışır: bilgisayarındaki dosyalara ulaşamaz, hile yapamaz,
sonsuz döngüye girerse oyunu dondurmadan durdurulur.

Bir dosyayı kaydettiğin anda mod oyunda **kendiliğinden yeniden yüklenir**, oyunu kapatıp açmana gerek yok.

---

## Mod nereye konur?

Oyun klasöründeki `dnzclient/scripts` klasörüne.
(DNZ Menü → **Script Modlar** → **Klasörü Aç**)

Üç şekilde olabilir:

| Şekil | Ne zaman |
|---|---|
| `saat.js` | Tek dosyalık hızlı denemeler |
| `olum-sayaci/` klasörü: `dnzmod.json` + `main.js` | Normal modlar |
| `olum-sayaci.dnzmod` (yukarıdaki klasörün zip hali) | Paylaşmak için |

`dnzmod.json` örneği:

```json
{
  "id": "olum-sayaci",
  "name": "Ölüm Sayacı",
  "version": "1.0.0",
  "author": "DNZ",
  "description": "Kaç kez öldüğünü HUD'da gösterir.",
  "main": "main.js",
  "api": 1
}
```

`id` sadece küçük harf, rakam, `-` ve `_` içerebilir ve her modda farklı olmalı.

Tek dosyalık modlarda aynı bilgiler dosyanın en üstüne yazılabilir:

```js
// @name Saat
// @author DNZ
// @description Gerçek saati HUD'da gösterir.
```

---

## İlk mod

`merhaba.js` adında bir dosya oluştur:

```js
on("join", () => {
  chat.show("Hoş geldin " + player.name() + "!", "gold");
});
```

Bir dünyaya girince sohbette `[merhaba] Hoş geldin ...!` yazar.
(Modların sohbet mesajlarının başında her zaman modun adı olur.)

Dosyanın en üstündeki kod, mod yüklenince **bir kez** çalışır. Hazırlık işlerini oraya yaz.

---

## Olaylar: `on(olay, fonksiyon)`

| Olay | Ne zaman çalışır | Gelen değer |
|---|---|---|
| `"tick"` | Saniyede 20 kez (dünyadayken) | – |
| `"join"` | Bir dünyaya / sunucuya girince | – |
| `"leave"` | Dünyadan / sunucudan çıkınca | – |
| `"death"` | Ölünce | – |
| `"respawn"` | Yeniden doğunca | – |
| `"damage"` | Hasar alınca | alınan hasar (sayı) |
| `"chat"` | Sohbete bir mesaj gelince | mesajın yazısı |

```js
on("damage", (amount) => {
  screen.actionbar("-" + amount + " can", "red");
});
```

---

## Bütün komutlar

### HUD
| Komut | Ne yapar |
|---|---|
| `hud.add(id, etiket, değer)` | HUD'a bir panel ekler. `değer` bir fonksiyon olabilir, sürekli güncellenir: `hud.add("kills", "Kill", () => kills)` |
| `hud.remove(id)` | Paneli kaldırır |

Paneller diğer DNZ HUD parçaları gibi **DNZ Menü → HUD**'dan açılıp kapatılır ve sürüklenir.

### Ekran
| Komut | Ne yapar |
|---|---|
| `screen.title(yazı, { subtitle, color, seconds })` | Ekranın ortasına büyük yazı |
| `screen.actionbar(yazı, renk)` | Hotbar'ın üstüne küçük yazı |
| `screen.toast(başlık, yazı)` | Sağ üstte bildirim kutusu |

### Sohbet
| Komut | Ne yapar |
|---|---|
| `chat.show(yazı, renk)` | Sadece senin gördüğün bir mesaj |
| `chat.send(yazı)` | Sohbete mesaj gönderir (`/` ile başlarsa komut). En fazla **2 saniyede 1** |

### Ses
| Komut | Ne yapar |
|---|---|
| `sound.play(ses, ses_yüksekliği, perde)` | Bir Minecraft sesi çalar: `sound.play("entity.player.levelup")`. Ses yüksekliği 0–1, perde 0.5–2 |

### Oyuncu (sadece okunur)
| Komut | Değer |
|---|---|
| `player.inGame()` | Dünyada mısın (true/false) |
| `player.name()` | Oyuncu adı |
| `player.health()` / `player.maxHealth()` | Can (20 = 10 kalp) |
| `player.food()` | Açlık (0–20) |
| `player.armor()` | Zırh puanı |
| `player.xp()` | XP seviyesi |
| `player.pos()` | `{ x, y, z }` |
| `player.dimension()` | `"overworld"`, `"the_nether"`, `"the_end"` |

### Oyun (sadece okunur)
| Komut | Değer |
|---|---|
| `game.fps()` | FPS |
| `game.ping()` | Ping (ms) |
| `game.time()` | Günün saati (0–23999, 0 = sabah 06:00) |
| `game.day()` | Kaçıncı gün |
| `game.server()` | Sunucu adresi ya da `"singleplayer"` |
| `game.version()` | Minecraft sürümü |
| `game.language()` | Oyunun dili (ör. `"tr_tr"`) |

### Zamanlayıcılar
| Komut | Ne yapar |
|---|---|
| `every(saniye, fonksiyon)` | Her X saniyede bir çalışır, bir numara verir |
| `after(saniye, fonksiyon)` | X saniye sonra bir kez çalışır |
| `cancel(numara)` | Zamanlayıcıyı durdurur |

### Tuşlar ve komutlar
| Komut | Ne yapar |
|---|---|
| `key("K", fonksiyon)` | Tuşa basınca çalışır. Tuşlar: harfler, rakamlar, `"F6"`, `"LEFT_ALT"`, `"SPACE"`... (sohbet açıkken çalışmaz) |
| `command("ad", (args) => {})` | Sohbete `/ad bir iki` yazınca çalışır; `args = ["bir", "iki"]`. Sunucuya gönderilmez |

### Kayıt
| Komut | Ne yapar |
|---|---|
| `storage.get(ad, varsayılan)` | Kaydedilmiş bir değeri okur |
| `storage.set(ad, değer)` | Bir değeri kaydeder (oyunu kapatsan da kalır). Sayı, yazı, liste, nesne olabilir. Mod başına en fazla 64 KB |

### Diğer
| Komut | Ne yapar |
|---|---|
| `log(...)` | Oyun kayıtlarına (log) yazar, hata ararken işe yarar |

---

## Renkler

`"white"`, `"red"`, `"green"`, `"blue"`, `"yellow"`, `"gold"`, `"orange"`, `"aqua"`, `"purple"`,
`"gray"`, `"dark_gray"`, `"black"`, `"accent"` (DNZ arayüz rengi) ya da `"#ff8800"` gibi bir renk kodu.

---

## Hatalar

Bir modda hata olursa:
- Sohbette kırmızı bir mesaj çıkar: `[DNZ Script] Ölüm Sayacı: ... (satır 12)`
- **DNZ Menü → Script Modlar**'da mod kırmızı görünür ve hatanın satırı yazar.
- Bir mod çok fazla hata verirse (25) kendiliğinden durdurulur. Düzeltip kaydet, yeniden yüklenir.

---

## Sınırlar (güvenlik için)

| Sınır | Değer |
|---|---|
| Bir olayın çalışma süresi | 40 ms (daha uzun sürerse durdurulur) |
| Mod yüklenirken | 1 saniye |
| HUD paneli / tuş / komut | Mod başına en fazla 16 |
| Zamanlayıcı | Mod başına en fazla 64 |
| `chat.send` | 2 saniyede 1 mesaj, en fazla 256 harf |
| `chat.show` | Saniyede 20 mesaj |
| `sound.play` | Saniyede 10 ses |
| Kod dosyası | En fazla 512 KB |

Modlar Java'ya, dosyalara ve internete **ulaşamaz**. Diğer oyuncuların yerini göremez, oyuncuyu kendiliğinden
hareket ettiremez, vuramaz. Böylece DNZ Script modları sunucularda hile sayılmaz.

---

## Hangi JavaScript?

Motor: Mozilla Rhino 1.8 (ES6'nın büyük kısmı). Test edilmiş durum:

| Çalışır ✅ | Çalışmaz ❌ (yerine bunu kullan) |
|---|---|
| `let`, `const` | `class` → nesne ve fonksiyon kullan |
| Ok fonksiyonları `() => {}` ve `function` | `...` (spread) → `Math.max.apply(null, liste)` ya da döngü |
| Şablon yazılar `` `Can: ${hp}` `` | `for (const x of liste)` → `liste.forEach(x => ...)` ya da `for (let i = 0; ...)` |
| Varsayılan değerler `function f(a, b = 2)` | `import` / `export`, `async` / `await` |
| Parçalama `const { x, y } = player.pos()` | |
| Diziler, nesneler, `Map`, `Set`, `JSON`, `Math`, `Date` | |

---

## Örnekler

`client/script-examples` klasöründe:
- `olum-sayaci/` – ölüm sayacı (HUD, kayıt, komut, ekran yazısı, ses)
- `saat.js` – gerçek saat ve oyun saati (HUD)
- `can-uyarisi.js` – canın azalınca uyarı (tick, tuş)
