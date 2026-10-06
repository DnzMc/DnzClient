package dnz.launcher

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.system.exitProcess

/** One picture: [setup] prepares the state, [waitMs] lets internet content (mod lists, icons) arrive. */
private class Shot(val name: String, val waitMs: Long = 0, val setup: LauncherState.() -> Unit)

/** Renders each screen to a PNG so the design can be checked without opening a window. */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "screenshots").apply { mkdirs() }
    val shots = listOf(
        Shot("home", 3000) { screen = Screen.Home },
        Shot("profiles") { screen = Screen.Profiles },
        Shot("profiles-edit") { screen = Screen.Profiles; editingProfile = 0 },
        Shot("mods", 9000) { screen = Screen.Mods },
        Shot("mods-page", 9000) { screen = Screen.Mods; library.openProject = "sodium" },
        Shot("mods-cf-page", 9000) { screen = Screen.Mods; library.openProject = "cf:248787" },
        Shot("mods-installed", 7000) { screen = Screen.Mods; library.tab = LibraryTab.Installed },
        // Real search of this computer's installs; nothing is imported.
        Shot("profiles-import") {
            screen = Screen.Profiles
            imports.sources = Importer.find().also { imports.selected = it.firstOrNull() }
        },
        Shot("servers", 6000) { screen = Screen.Servers },
        Shot("settings") { screen = Screen.Settings },
        // Real scan of this computer, but nothing is changed (plan only).
        Shot("settings-auto") {
            screen = Screen.Settings
            autoResult = AutoTune.Result(AutoTune.plan(PcScan.scan()), AutoTune.targets(profiles.toList()).map { it.name }, emptyList(), ramGb)
        },
        Shot("account") { screen = Screen.Account },
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
        val state = LauncherState().apply(shot.setup)
        val scene = ImageComposeScene(1200, if (shot.name == "profiles-import") 1300 else 740, Density(1f)) {
            LauncherApp(state, dragArea = { it() }, onMinimize = {}, onClose = {})
        }
        // Render a few frames so async content can appear.
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
