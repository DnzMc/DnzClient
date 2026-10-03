// @name Can Uyarısı
// @author DNZ
// @description Canın 3 kalbin altına inince uyarır. K tuşu açar/kapatır.

let active = true;
let warned = false;

on("tick", () => {
  if (!active) return;
  const hp = player.health();
  if (hp <= 6 && !warned) {
    warned = true;
    screen.actionbar("Canın azaldı! (" + hp + ")", "red");
    sound.play("block.note_block.bell", 1, 0.8);
  } else if (hp > 6) {
    warned = false;
  }
});

key("K", () => {
  active = !active;
  chat.show(active ? "Can uyarısı açık." : "Can uyarısı kapalı.", active ? "green" : "gray");
});
