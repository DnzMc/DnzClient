// @name Saat
// @author DNZ
// @description Gerçek saati ve oyundaki saati HUD'da gösterir.

function twoDigits(n) {
  return (n < 10 ? "0" : "") + n;
}

hud.add("clock", "Saat", () => {
  const now = new Date();
  return twoDigits(now.getHours()) + ":" + twoDigits(now.getMinutes());
});

hud.add("gametime", "Oyun", () => {
  // Oyunda 0 = sabah 06:00, bir gün 24000 tick.
  const t = (game.time() + 6000) % 24000;
  const hours = Math.floor(t / 1000);
  const minutes = Math.floor((t % 1000) * 60 / 1000);
  return twoDigits(hours) + ":" + twoDigits(minutes) + "  (Gün " + game.day() + ")";
});
