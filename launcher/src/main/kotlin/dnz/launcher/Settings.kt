package dnz.launcher

import com.google.gson.GsonBuilder
import java.io.File

/** Launcher preferences in .dnzlauncher/launcher.json, so language, RAM and profiles survive a restart. */
object Settings {
    /** Changed only by tests, so they never touch the player's real settings. */
    var file = File(GameLauncher.root, "launcher.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** Everything that is saved. Fields are nullable because Gson leaves missing ones empty. */
    data class Saved(
        val language: Lang? = null,
        val ramGb: Float? = null,
        val launchMode: LaunchMode? = null,
        val profiles: List<Profile>? = null,
        val selectedProfile: Int? = null,
        val theme: LauncherTheme? = null,
        /** Mac: AUTO already ran once by itself on the first start. */
        val macAutoDone: Boolean? = null,
        val mojangNoticeHidden: Boolean? = null,
        /** Colors of the DNZ Workshop theme in use (theme file JSON), with its id below; null when none. */
        val workshopTheme: String? = null,
        /** Accent color picked in Settings (ARGB), null for DNZ orange. */
        val accent: Long? = null,
        val skinAnimation: Boolean? = null,
        val workshopThemeId: String? = null,
        /** Launcher version of the last run (Changelog popup after an update). */
        val lastVersion: String? = null,
    )

    fun snapshot(state: LauncherState) = Saved(
        state.language, state.ramGb, state.launchMode, state.profiles.toList(), state.selectedProfile, Dnz.theme, state.macAutoDone, state.mojangNoticeHidden,
        Dnz.workshopId?.let { Dnz.custom?.toJson() }, Dnz.accent, state.skinAnimation, Dnz.workshopId, state.lastVersion,
    )

    fun load(state: LauncherState) {
        val saved = runCatching { gson.fromJson(file.readText(), Saved::class.java) }.getOrNull() ?: return
        state.hadSettings = true
        state.lastVersion = saved.lastVersion
        saved.language?.let { state.language = it }
        saved.ramGb?.let { state.ramGb = it.coerceIn(2f, 16f) }
        saved.launchMode?.let { state.launchMode = it }
        saved.theme?.let { Dnz.theme = it }
        saved.accent?.let { Dnz.pickAccent(it) }
        // A Workshop theme wins over the accent color (installing one clears the accent).
        val workshopColors = saved.workshopTheme?.let { ThemeColors.fromJson(it) }
        if (saved.workshopThemeId != null && workshopColors != null) Dnz.useWorkshopTheme(saved.workshopThemeId, workshopColors)
        saved.skinAnimation?.let { state.skinAnimation = it }
        saved.macAutoDone?.let { state.macAutoDone = it }
        saved.mojangNoticeHidden?.let { state.mojangNoticeHidden = it }
        // Profiles from an old or edited file may miss fields; keep only complete ones.
        @Suppress("SENSELESS_COMPARISON")
        val profiles = saved.profiles?.filter { it.name != null && it.version != null && it.version in state.versions }
            ?.map { if (it.description == null) it.copy(description = "DNZ Client") else it }
            // Older files have no folder: fix it to today's one, so a later rename keeps the worlds and mods.
            ?.map { if (it.dir == null) it.copy(dir = it.folder) else it }
        if (!profiles.isNullOrEmpty()) {
            state.profiles.clear()
            state.profiles.addAll(profiles)
        }
        state.selectedProfile = (saved.selectedProfile ?: 0).coerceIn(0, state.profiles.size - 1)
        state.version = state.profiles[state.selectedProfile].version
    }

    fun save(saved: Saved) {
        runCatching {
            val temp = File(file.path + ".tmp")
            temp.writeText(gson.toJson(saved))
            file.delete()
            temp.renameTo(file)
        }
    }
}
