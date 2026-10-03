package dnz.launcher

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * The mods of one profile (its instance "mods" folder): what is installed, installing from Modrinth
 * (with required dependencies), turning mods on/off (".jar.disabled", same as the in-game DNZ menu) and removing them.
 */
object Library {
    data class Installed(
        val file: File,
        val enabled: Boolean,
        val sha1: String,
        val projectId: String?,
        val versionId: String?,
        val title: String,
        val iconUrl: String?,
        val versionNumber: String?,
        /** Installed by DNZ on every PLAY (DNZ Client, Fabric API, Sodium...). */
        val bundled: Boolean,
        /** Bundled and required, can't be turned off. */
        val locked: Boolean,
        val update: Modrinth.Version?,
    )

    /** SHA-1 -> Modrinth version (null = not on Modrinth), so a folder is only looked up once. */
    private val versionCache = ConcurrentHashMap<String, Result<Modrinth.Version?>>()
    private val projectCache = ConcurrentHashMap<String, Modrinth.ProjectBrief>()

    fun modsDir(profile: Profile) = File(GameLauncher.instanceDir(profile), "mods")

    private fun jarFiles(mods: File): List<File> =
        mods.listFiles { f -> f.isFile && (f.name.endsWith(".jar") || f.name.endsWith(".jar.disabled")) }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()

    /** Lists the profile's mods. Works offline too (then only file names are known). */
    suspend fun scan(profile: Profile, checkUpdates: Boolean): List<Installed> = withContext(Dispatchers.IO) {
        val mods = modsDir(profile)
        val files = jarFiles(mods)
        val hashes = files.associateWith { Net.sha1(it) }
        val unknown = hashes.values.filter { !versionCache.containsKey(it) }
        if (unknown.isNotEmpty()) {
            runCatching { Modrinth.versionsByHash(unknown) }.onSuccess { found ->
                unknown.forEach { versionCache[it] = Result.success(found[it]) }
            }
        }
        val versions = hashes.mapValues { (_, sha1) -> versionCache[sha1]?.getOrNull() }
        val projectIds = versions.values.mapNotNull { it?.projectId }.filter { !projectCache.containsKey(it) }.toSet()
        runCatching { Modrinth.projects(projectIds) }.onSuccess { list -> list.forEach { projectCache[it.id] = it } }

        val updates: Map<String, Modrinth.Version> = if (checkUpdates) {
            val known = hashes.filter { versions[it.key] != null }.values
            val latest = runCatching { Modrinth.latestByHash(known, profile.version) }.getOrDefault(emptyMap())
            latest.mapNotNull { (sha1, newest) ->
                val current = versionCache[sha1]?.getOrNull() ?: return@mapNotNull null
                // Someone on a stable (release) version is only offered stable updates, never alpha/beta.
                val offer = if (current.type == "release" && newest.type != "release") {
                    runCatching { Modrinth.versions(current.projectId, profile.version).firstOrNull { it.type == "release" } }.getOrNull()
                } else newest
                offer?.takeIf { it.id != current.id && it.date > current.date }?.let { sha1 to it }
            }.toMap()
        } else emptyMap()

        val bundled = GameLauncher.bundledFiles(mods)
        files.map { file ->
            val sha1 = hashes.getValue(file)
            val version = versions[file]
            val project = version?.projectId?.let { projectCache[it] }
            val baseName = file.name.removeSuffix(".disabled")
            val latest = updates[sha1]?.takeIf { version != null && it.id != version.id }
            Installed(
                file = file,
                enabled = !file.name.endsWith(".disabled"),
                sha1 = sha1,
                projectId = version?.projectId,
                versionId = version?.id,
                title = project?.title ?: fabricName(file) ?: baseName.removeSuffix(".jar"),
                iconUrl = project?.iconUrl,
                versionNumber = version?.number,
                bundled = baseName in bundled,
                locked = bundled[baseName] == true,
                update = latest.takeIf { baseName !in bundled }, // bundled mods are updated by PLAY
            )
        }
    }

    /** Mod name from the jar's fabric.mod.json, for mods that are not on Modrinth. */
    private fun fabricName(file: File): String? = fabricField(file, "name")

    private fun fabricField(file: File, key: String): String? = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("fabric.mod.json") ?: return null
            zip.getInputStream(entry).bufferedReader().use { JsonParser.parseReader(it).asJsonObject[key]?.asString }
        }
    }.getOrNull()

    data class InstallResult(val main: String, val dependencies: List<String>)

    /**
     * Installs [projectId] ([version], or the newest compatible one) into the profile, plus every required
     * dependency that is missing. An older file of the same mod is replaced.
     */
    suspend fun install(profile: Profile, projectId: String, version: Modrinth.Version? = null, progress: (String) -> Unit = {}): InstallResult =
        installLock.withLock { withContext(Dispatchers.IO) { installNow(profile, projectId, version, progress) } }

    /** One install at a time, so two installs never download the same dependency into the same folder at once. */
    private val installLock = Mutex()

    private suspend fun installNow(profile: Profile, projectId: String, version: Modrinth.Version?, progress: (String) -> Unit): InstallResult =
        run {
            val mc = profile.version
            val mods = modsDir(profile).apply { mkdirs() }
            val installed = scan(profile, checkUpdates = false)
            val installedIds = installed.mapNotNull { it.projectId }.toSet()
            // Updating a mod that is turned off keeps it off.
            val keepOff = installed.filter { it.projectId == projectId }.let { old -> old.isNotEmpty() && old.none { it.enabled } }

            progress(Strings.current("lib.resolving"))
            // Mods PLAY installs itself (Fabric API, Sodium...) are never pulled in as dependencies:
            // a second copy next to PLAY's own would crash the game.
            val bundledIds = bundledProjectIds()
            val plan = LinkedHashMap<String, Modrinth.Version>()
            fun resolve(pid: String, pinned: Modrinth.Version?) {
                if (pid in plan) return
                val chosen = pinned ?: bestVersion(pid, mc) ?: error(Strings.current("lib.no_version").replace("%s", mc))
                plan[pid] = chosen
                for (dep in chosen.dependencies.filter { it.type == "required" }) {
                    val depVersion = dep.versionId?.let { runCatching { Modrinth.version(it) }.getOrNull() }
                    val depId = dep.projectId ?: depVersion?.projectId ?: continue
                    if (depId in installedIds || depId in bundledIds) continue
                    resolve(depId, depVersion)
                }
            }
            resolve(projectId, version)

            val names = runCatching { Modrinth.projects(plan.keys).associate { it.id to it.title } }.getOrDefault(emptyMap())
            for ((pid, v) in plan) {
                val file = v.primaryFile ?: continue
                check(file.url.startsWith("https://cdn.modrinth.com/")) { Strings.current("lib.untrusted") }
                val safeName = file.filename.isNotBlank() && !file.filename.contains('/') && !file.filename.contains('\\') && !file.filename.startsWith(".")
                check(safeName) { Strings.current("lib.bad_name") }
                progress(Strings.current("lib.downloading").replace("%s", names[pid] ?: file.filename))
                val old = installed.filter { it.projectId == pid && it.file.name != file.filename }
                // Replace older files of the same mod (also when they were turned off).
                old.forEach { it.file.delete() }
                File(mods, file.filename + ".disabled").delete()
                Net.download(file.url, File(mods, file.filename), file.sha1)
                file.sha1?.let { versionCache[it] = Result.success(v) }
                if (pid == projectId && keepOff) File(mods, file.filename).renameTo(File(mods, file.filename + ".disabled"))
            }
            // Required dependencies that were installed but turned off are turned back on.
            val needed = plan.values.flatMap { v -> v.dependencies.filter { it.type == "required" }.mapNotNull { it.projectId } }.toSet()
            installed.filter { !it.enabled && it.projectId in needed }.forEach { setEnabled(it, true) }

            InstallResult(names[projectId] ?: projectId, plan.keys.filter { it != projectId }.map { names[it] ?: it })
        }

    @Volatile
    private var bundledIdsCache: Set<String>? = null

    /** Modrinth project ids of the mods every DNZ profile gets on PLAY (the API accepts slugs in place of ids). */
    fun bundledProjectIds(): Set<String> =
        bundledIdsCache ?: Modrinth.projects(GameLauncher.modrinthMods).map { it.id }.toSet().also { bundledIdsCache = it }

    private fun bestVersion(projectId: String, mc: String): Modrinth.Version? {
        val list = Modrinth.versions(projectId, mc)
        return list.firstOrNull { it.type == "release" } ?: list.firstOrNull()
    }

    /** Turns a mod on or off by renaming "x.jar" <-> "x.jar.disabled". Returns the new file, or null if it could not be renamed. */
    fun setEnabled(mod: Installed, enabled: Boolean): File? {
        if (mod.locked) return null
        if (mod.enabled == enabled) return mod.file
        val name = mod.file.name
        val target = File(mod.file.parentFile, if (enabled) name.removeSuffix(".disabled") else "$name.disabled")
        return target.takeIf { mod.file.renameTo(it) }
    }

    fun remove(mod: Installed): Boolean = !mod.bundled && mod.file.delete()
}
