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
    )

    fun snapshot(state: LauncherState) = Saved(
        state.language, state.ramGb, state.launchMode, state.profiles.toList(), state.selectedProfile, Dnz.theme, state.macAutoDone, state.mojangNoticeHidden,
    )

    fun load(state: LauncherState) {
        val saved = runCatching { gson.fromJson(file.readText(), Saved::class.java) }.getOrNull() ?: return
        saved.language?.let { state.language = it }
        saved.ramGb?.let { state.ramGb = it.coerceIn(2f, 16f) }
        saved.launchMode?.let { state.launchMode = it }
        saved.theme?.let { Dnz.theme = it }
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
