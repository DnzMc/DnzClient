# DNZ Client

Minecraft client and launcher. Minecraft 26.2 / 26.3 (Fabric) and 1.8.9 (Forge), Windows and macOS.

Download: [dnzclient.com](https://dnzclient.com) · Discord: [discord.gg/vaPUBjUnZN](https://discord.gg/vaPUBjUnZN)

## Folders

- `launcher/` - DNZ Launcher (Kotlin, Compose Desktop) and the Windows installer
- `client/` - the in-game mod for 26.x (Fabric)
- `client-189/` - the 1.8.9 version (Forge)
- `schematic/` - DNZ Schematic (.litematic helper)
- `docs/` - DNZ Script mods

## Building

Needs Java 25.

```
cd client
gradlew build -Pminecraft_version=26.2

cd launcher
gradlew run
```

`gradlew installer` builds the Windows setup, `gradlew macZip` the macOS app.

Microsoft sign-in needs your own Azure app id: copy `launcher/dnz-keys.properties.example` to
`launcher/src/main/resources/dnz-keys.properties` and fill it in. That file is gitignored.

## License

GPL-3.0, see [LICENSE](LICENSE). The DNZ name and logo are not covered by it, forks need their own
([TRADEMARKS.md](TRADEMARKS.md)). Third-party licenses: [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
Not affiliated with Mojang or Microsoft.
