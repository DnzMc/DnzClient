# Hata Bildirim Sayfası — Kurulum

Bu klasör Cloudflare Pages'e sürükle-bırak ile yüklenir. Gelen bildirimler bir Discord kanalına düşer.

## 1. Discord webhook oluştur
1. Discord'da bildirimlerin geleceği kanalın ayarlarını aç (⚙️).
2. **Entegrasyonlar → Webhook'lar → Yeni Webhook**.
3. **Webhook URL'sini Kopyala**'ya bas. Bu adresi kimseyle paylaşma.

## 2. Cloudflare Pages'e yükle
1. https://dash.cloudflare.com → **Workers & Pages → Create → Pages → Upload assets**.
2. Proje adı yaz (ör. `dnz-bug`).
3. `bug-sayfasi` klasörünü sürükle → **Deploy site**.

## 3. Webhook'u ekle
1. Pages projesi → **Settings → Variables and Secrets → Add**.
2. Tür: **Secret**, ad: `DISCORD_WEBHOOK`, değer: kopyaladığın webhook adresi → **Save**.
3. Ayarın çalışması için klasörü bir kez daha yükle: **Deployments → Create deployment** → klasörü tekrar sürükle.

## 4. (İsteğe bağlı) Spam sınırı
Her kişi saatte en fazla 5 bildirim gönderebilsin istersen:
1. **Storage & Databases → KV → Create namespace** (ör. `dnz-bug-kv`).
2. Pages projesi → **Settings → Bindings → Add → KV namespace**, ad: `BUG_KV`, az önce oluşturduğunu seç.
3. Klasörü bir kez daha yükle.

## 5. (İsteğe bağlı) Kendi adresin
Pages projesi → **Custom domains** → ör. `bug.dnzclient.com`.
