# DNZ Theme format (version 2)

A DNZ theme changes the colors of **DNZ Launcher** and of the **in-game DNZ menu**, and can add **decorations**
(background picture, a pattern on the menu panel, stickers around the panel). A theme has no program code: only
colors, numbers and pictures, so DNZ Workshop can publish it right away.

## File

`<name>.dnztheme`: a ZIP file with

| Path in the ZIP | Required | What |
|---|---|---|
| `theme.json` | yes | colors, style and decorations (below) |
| `images/*.png` / `images/*.jpg` | no | pictures used by `theme.json` |

Limits: whole file at most **4 MB**; each picture at most **1 MB** and **2048 x 2048** pixels; at most **8** stickers.
Only `theme.json` and pictures under `images/` are read; anything else in the ZIP is ignored.

A plain `.json` file in the old format (`"dnzLauncherTheme": 1`, launcher colors only) still works.

## theme.json

```json
{
  "dnzTheme": 2,
  "name": "Night Blue",
  "author": "Steve",

  "launcher": {
    "background": "#0A0D12", "sidebar": "#0A0D12", "surface": "#151B25", "surfaceHigh": "#1C2330",
    "accent": "#4FA3FF", "accent2": "#2F7FE0", "text": "#F2F4FA", "muted": "#8C95AB",
    "backgroundImage": "images/launcher.png"
  },

  "game": {
    "colors": {
      "accent": "#4FA3FF",
      "panel": "#0A0D12", "panelEdge": "#1B212C", "panelGradient": "#101826",
      "card": "#121821", "cardHover": "#161D28", "cardEdge": "#1B212C",
      "search": "#151B25", "text": "#F1F3F7", "muted": "#8A93A6"
    },
    "style": {
      "radius": 10, "cardRadius": 8, "panelOpacity": 0.98, "glow": 0.3
    },
    "background": { "image": "images/menu-bg.png", "dim": 0.4 },
    "pattern": { "image": "images/stars.png", "opacity": 0.08 },
    "decorations": [
      { "image": "images/cat.png", "anchor": "top-right", "x": -12, "y": -18, "size": 48, "float": true },
      { "image": "images/leaf.png", "anchor": "bottom-left", "x": 6, "y": 6, "size": 32, "spin": 0 }
    ]
  }
}
```

Every part is optional except `dnzTheme` and `name`; anything left out keeps DNZ's own look.

- **Colors**: `#RRGGBB` (opaque) or `#AARRGGBB` (with transparency).
- **launcher**: the launcher's 8 colors (the same keys as version 1) and an optional picture behind the home page.
- **game.colors**: the in-game DNZ menu (Right Shift → MODS).
  `panelGradient` (optional) fades the panel from `panel` at the top to this color at the bottom.
- **game.style**: `radius` / `cardRadius` corner roundness (0–20), `panelOpacity` (0.5–1), `glow` (0–1, a soft
  accent-colored light around the panel).
- **game.background**: a picture filling the screen behind the menu; `dim` (0–1) darkens it.
- **game.pattern**: a picture laid over the panel; `opacity` (0–0.5).
- **game.decorations**: stickers drawn around/over the panel. `anchor`: `top-left`, `top-right`, `bottom-left`,
  `bottom-right` (corners of the panel); `x`, `y` move it (menu units); `size` (8–128); `float` bobs it up and
  down gently; `spin` turns it (degrees).

## Where an applied theme lives

DNZ Launcher unpacks the applied theme to `~/.dnzlauncher/theme/` (`theme.json` and `images/`). DNZ Client reads it
from there when its menu opens (26.x and 1.8.9), so one theme dresses the launcher and the game.
