package dnz.launcher

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.system.exitProcess

private class Shot(val name: String, val waitMs: Long = 0, val setup: LauncherState.() -> Unit)

/** Renders each screen to a PNG so the design can be checked without opening a window. */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "screenshots").apply { mkdirs() }
    val shots = listOf(
        Shot("home", 3000) { screen = Screen.Home },
        Shot("profiles") { screen = Screen.Profiles },
        Shot("profiles-edit") { screen = Screen.Profiles; editingProfile = 0 },
        Shot("mods", 9000) { screen = Screen.Mods },
        Shot("mods-all", 9000) { screen = Screen.Mods; library.query = "pvp" },
        Shot("mods-packs", 9000) { screen = Screen.Mods; library.kind = Kind.Packs },
        Shot("mods-packs-cf", 12000) { screen = Screen.Mods; library.kind = Kind.Packs; CurseForge.check(); library.source = ModSource.CurseForge },
        Shot("mods-page", 9000) { screen = Screen.Mods; library.openProject = "sodium" },
        Shot("mods-cf-page", 9000) { screen = Screen.Mods; library.openProject = "cf:248787" },
        Shot("mods-installed", 7000) { screen = Screen.Mods; library.tab = LibraryTab.Installed },
        // Real search of this computer's installs; nothing is imported
        Shot("profiles-import") {
            screen = Screen.Profiles
            imports.sources = Importer.find().also { imports.selected = it.firstOrNull() }
        },
        Shot("servers", 6000) { screen = Screen.Servers },
        Shot("presence", 1500) { screen = Screen.Servers; Presence.preview(12, 5, 340) },
        Shot("presence-open", 1500) { screen = Screen.Servers; Presence.preview(12, 5, 340); presenceOpen = true },
        Shot("servers-make", 1500) { screen = Screen.Servers; host.step = HostStep.Choose },
        Shot("servers-world", 1500) { screen = Screen.Servers; host.step = HostStep.World },
        Shot("servers-server", 1500) { screen = Screen.Servers; host.step = HostStep.Server },
        Shot("settings") { screen = Screen.Settings },
        // the whole Settings page in one tall picture (appearance cards at the bottom)
        Shot("settings-all") { screen = Screen.Settings },
        // Home with another accent color picked in Settings
        Shot("home-de") { screen = Screen.Home; language = Lang.DE },
        Shot("settings-de") { screen = Screen.Settings; language = Lang.DE },
        Shot("home-purple") { screen = Screen.Home; Dnz.pickAccent(0xFF9D6BFF) },
        // Real scan of this computer, but nothing is changed (plan only)
        Shot("settings-auto") {
            screen = Screen.Settings
            autoResult = AutoTune.Result(AutoTune.plan(PcScan.scan()), AutoTune.targets(profiles.toList()).map { it.name }, emptyList(), ramGb)
        },
        Shot("account") { screen = Screen.Account },
        Shot("changelog") { screen = Screen.Home; changelogTo = "1.0.14"; changelogFrom = "1.0.12" },
        Shot("changelog-tr", 2000) { screen = Screen.Home; language = Lang.TR; changelogTo = "1.0.14"; changelogFrom = "?" },
        // Black & white theme
        Shot("mono-home") { Dnz.theme = LauncherTheme.Mono; screen = Screen.Home },
        Shot("mono-settings") { Dnz.theme = LauncherTheme.Mono; screen = Screen.Settings },
        Shot("mono-mods", 9000) { Dnz.theme = LauncherTheme.Mono; screen = Screen.Mods },
        Shot("mono-profiles") {
            Dnz.theme = LauncherTheme.Mono
            screen = Screen.Profiles
            imports.sources = Importer.find().also { imports.selected = it.firstOrNull() }
        },
    )
    for (shot in shots) {
        if (args.size > 1 && !shot.name.startsWith(args[1])) continue
        Dnz.theme = LauncherTheme.Colorful
        val state = LauncherState().apply { setupDone = true }.apply(shot.setup)
        val scene = ImageComposeScene(1200, when (shot.name) { "profiles-import" -> 1300; "settings-all" -> 1900; else -> 740 }, Density(1f)) {
            LauncherApp(state, dragArea = { it() }, onMinimize = {}, onClose = {})
        }
        // Render a few frames so async content can appear
        var time = 0L
        var image = scene.render(time)
        val end = System.currentTimeMillis() + shot.waitMs
        while (System.currentTimeMillis() < end) {
            Thread.sleep(500)
            time += 500_000_000L
            image = scene.render(time)
        }
        File(out, "${shot.name}.png").writeBytes(image.encodeToData()!!.bytes)
        scene.close()
    }
    // first-run setup pages (the install page with nothing to install)
    if (args.size < 2 || args[1].startsWith("setup")) {
        for (page in 0 until 6) {
            Dnz.theme = LauncherTheme.Colorful
            val state = LauncherState()
            val scene = ImageComposeScene(1200, 740, Density(1f)) {
                androidx.compose.material3.MaterialTheme { FirstRunSetup(state, dragArea = { it() }, onMinimize = {}, onClose = {}, startPage = page) }
            }
            var time = 0L
            var image = scene.render(time)
            repeat(if (page == 3) 16 else 3) {
                Thread.sleep(500)
                time += 500_000_000L
                image = scene.render(time)
            }
            File(out, "setup-$page.png").writeBytes(image.encodeToData()!!.bytes)
            scene.close()
        }
    }
    if (args.size < 2 || args[1].startsWith("custom")) {
        val state = LauncherState().apply { screen = Screen.Home }
        Dnz.custom = ThemeColors("Purple Night", 0xFF0E0A18, 0xFF0B0814, 0xFF1A1428, 0xFF241C38, 0xFFA56BFF, 0xFF6B4BFF, 0xFFF2EEFA, 0xFF9A90B5)
        val scene = ImageComposeScene(1200, 740, Density(1f)) { LauncherApp(state, dragArea = { it() }, onMinimize = {}, onClose = {}) }
        var image = scene.render(0)
        repeat(4) { i -> Thread.sleep(500); image = scene.render((i + 1) * 500_000_000L) }
        File(out, "custom-theme-home.png").writeBytes(image.encodeToData()!!.bytes)
        scene.close()
        Dnz.custom = null
    }
    println("Saved to ${out.absolutePath}")
    exitProcess(0)
}
