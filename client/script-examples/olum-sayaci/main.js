// Ölüm Sayacı: kaç kez öldüğünü HUD'da gösterir ve oyunu kapatsan da hatırlar.

let deaths = storage.get("deaths", 0);

// HUD'da "Ölüm 3" gibi görünür. DNZ Menü > HUD'dan yeri değiştirilebilir.
hud.add("deaths", "Ölüm", () => deaths);

on("death", () => {
  deaths++;
  storage.set("deaths", deaths);
  screen.title("Öldün!", { subtitle: "Toplam: " + deaths, color: "red", seconds: 2 });
  sound.play("entity.player.hurt");
});

// Sohbete /olumsifirla yazınca sayaç sıfırlanır (sunucuya gönderilmez).
command("olumsifirla", () => {
  deaths = 0;
  storage.set("deaths", 0);
  chat.show("Ölüm sayacı sıfırlandı.", "green");
});
