# DNZ Client

A lightweight, modern Minecraft client and launcher. Minecraft Java 26.2 / 26.3 (Fabric), for Windows and macOS.

## Folders

| Folder | Contents |
|---|---|
| `launcher/` | DNZ Launcher (Kotlin + Compose Desktop) and its Windows installer |
| `client/` | The in-game DNZ Client mod (Fabric) |
| `schematic/` | The DNZ Schematic mod (.litematic buildings, finds wrong and missing blocks) |
| `docs/` | Guide for writing DNZ Script mods |

## Building

Java 25 is required.

- Client: `cd client` → `gradlew build -Pminecraft_version=26.2` (or `26.3`)
- Launcher: `cd launcher` → `gradlew run`
- Windows installer: `cd launcher` → `gradlew installer`
- macOS app: `cd launcher` → `gradlew macZip`

Signing in with Microsoft needs your own Azure app id:
copy `launcher/dnz-keys.properties.example` to `launcher/src/main/resources/dnz-keys.properties` and fill it in.
That file is never committed to git.

## License

Copyright (C) 2026 DNZ. DNZ Client, DNZ Launcher and DNZ Schematic are free software, licensed under the
**GNU General Public License v3.0** ([LICENSE](LICENSE)): you may use, study, change and share them, and any
version you share, changed or not, must stay under the same license with its source code open.

The DNZ name and logo are not part of that license: forks need their own name and logo, see [TRADEMARKS.md](TRADEMARKS.md).
Sodium and the other projects DNZ uses keep their own licenses, see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
Minecraft is a trademark of Mojang AB / Microsoft; DNZ is not affiliated with Mojang or Microsoft.
