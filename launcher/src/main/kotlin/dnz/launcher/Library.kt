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
        /** Simplified names (mod id, name, Modrinth short name): the same mod shows as installed on Modrinth and CurseForge. */
        val names: Set<String> = emptySet(),
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
        // Mods installed from CurseForge that Modrinth does not know.
        val sources = readSources(mods)

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
            val source = sources[baseName]?.takeIf { version == null && it.sha1.equals(sha1, ignoreCase = true) }
            Installed(
                file = file,
                enabled = !file.name.endsWith(".disabled"),
                sha1 = sha1,
                projectId = version?.projectId ?: source?.projectId,
                versionId = version?.id ?: source?.versionId,
                title = project?.title ?: source?.title ?: fabricName(file) ?: baseName.removeSuffix(".jar"),
                iconUrl = project?.iconUrl ?: source?.iconUrl,
                versionNumber = version?.number ?: source?.number,
                bundled = baseName in bundled,
                locked = bundled[baseName] == true,
                update = latest.takeIf { baseName !in bundled }, // bundled mods are updated by PLAY
                names = listOfNotNull(fabricField(file, "id"), fabricName(file), project?.title, project?.slug, source?.title).map { simpleName(it) }.filter { it.isNotEmpty() }.toSet(),
            )
        }
    }

    /**
     * A name reduced for comparing the same mod across Modrinth and CurseForge: "Entity Culling", "EntityCulling" and
     * "entityculling" all become "entityculling".
     */
    fun simpleName(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

    /** True when [hit] (a search result or mod page) is a mod that is already in the profile, from either site. */
    fun isInstalled(installed: List<Installed>, id: String, slug: String, title: String): Boolean {
        val keys = setOf(simpleName(slug), simpleName(title)) - ""
        return installed.any { it.projectId == id || it.names.any { n -> n in keys } }
    }

    /** Mod name from the jar's fabric.mod.json, for mods that are not on Modrinth. */
    private fun fabricName(file: File): String? = fabricField(file, "name")

    private fun fabricField(file: File, key: String): String? = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("fabric.mod.json") ?: return null
            zip.getInputStream(entry).bufferedReader().use { JsonParser.parseReader(it).asJsonObject[key]?.asString }
        }
    }.getOrNull()

    // ---- where CurseForge mods came from (mods/.dnz-sources.json, file name -> project)

    private data class Source(val projectId: String, val versionId: String, val title: String, val iconUrl: String?, val number: String, val sha1: String)

    private fun sourcesFile(mods: File) = File(mods, ".dnz-sources.json")

    private fun readSources(mods: File): Map<String, Source> = runCatching {
        val o = JsonParser.parseString(sourcesFile(mods).readText()).asJsonObject
        o.entrySet().associate { (name, v) ->
            val s = v.asJsonObject
            fun str(k: String) = s[k]?.takeUnless { it.isJsonNull }?.asString
            name to Source(str("project")!!, str("version")!!, str("title") ?: name, str("icon"), str("number") ?: "", str("sha1") ?: "")
        }
    }.getOrDefault(emptyMap())

    private fun writeSource(mods: File, fileName: String, source: Source?) {
        val all = readSources(mods).toMutableMap()
        if (source == null) all.remove(fileName) else all[fileName] = source
        // Forget files that are gone.
        all.keys.removeAll { !File(mods, it).exists() && !File(mods, "$it.disabled").exists() && it != fileName }
        val o = com.google.gson.JsonObject()
        all.forEach { (name, s) ->
            o.add(name, com.google.gson.JsonObject().apply {
                addProperty("project", s.projectId); addProperty("version", s.versionId); addProperty("title", s.title)
                addProperty("icon", s.iconUrl); addProperty("number", s.number); addProperty("sha1", s.sha1)
            })
        }
        sourcesFile(mods).writeText(o.toString())
    }

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

            val cfIds = plan.keys.filter { CurseForge.isCf(it) }
            val cfBriefs = cfIds.mapNotNull { id -> runCatching { CurseForge.brief(id) }.getOrNull() }.associateBy { it.id }
            val names = runCatching { Modrinth.projects(plan.keys - cfIds.toSet()).associate { it.id to it.title } }.getOrDefault(emptyMap()) +
                cfBriefs.mapValues { it.value.title }
            // Fabric mod ids already in the folder (bundled mods too), so a dependency from either site is never a second copy.
            val presentIds = installed.mapNotNull { fabricField(it.file, "id") }.toMutableSet()
            val bundledNames = GameLauncher.modrinthMods.map { simpleName(it) }.toSet()
            val skipped = mutableSetOf<String>()
            for ((pid, v) in plan) {
                val file = v.primaryFile ?: continue
                val cf = CurseForge.isCf(pid)
                if (cf && file.url.isBlank()) error(Strings.current("lib.cf_blocked").replace("%s", names[pid] ?: file.filename))
                check(if (cf) CurseForge.trusted(file.url) else file.url.startsWith("https://cdn.modrinth.com/")) { Strings.current("lib.untrusted") }
                val safeName = file.filename.isNotBlank() && !file.filename.contains('/') && !file.filename.contains('\\') && !file.filename.startsWith(".")
                check(safeName) { Strings.current("lib.bad_name") }
                progress(Strings.current("lib.downloading").replace("%s", names[pid] ?: file.filename))
                // Download next to the mods first and look inside before it counts as installed.
                val temp = File(mods, ".${file.filename}.new")
                Net.download(file.url, temp, file.sha1)
                val fabricId = fabricField(temp, "id")
                // Two copies of one mod crash the game. The same mod is found by its project or by the mod id inside
                // the jar, so a copy from the other site (Modrinth <-> CurseForge) counts too.
                val old = installed.filter { it.projectId == pid || (fabricId != null && fabricField(it.file, "id") == fabricId) }
                // A dependency that is already there (from either site), or a mod PLAY installs itself (also before the
                // first PLAY, when it is not in the folder yet): kept as it is.
                val playInstalls = fabricId != null && simpleName(fabricId) in bundledNames
                if ((pid != projectId && fabricId != null && fabricId in presentIds && old.none { it.projectId == pid }) || old.any { it.bundled } || playInstalls) {
                    temp.delete()
                    skipped += pid
                    continue
                }
                val turnedOff = old.isNotEmpty() && old.none { it.enabled }
                old.forEach { it.file.delete() }
                File(mods, file.filename + ".disabled").delete()
                File(mods, file.filename).delete()
                check(temp.renameTo(File(mods, file.filename))) { Strings.current("lib.bad_name") }
                fabricId?.let { presentIds += it }
                if (cf) {
                    val brief = cfBriefs[pid]
                    writeSource(mods, file.filename, Source(pid, v.id, brief?.title ?: file.filename, brief?.iconUrl, v.number, file.sha1 ?: Net.sha1(File(mods, file.filename))))
                } else {
                    file.sha1?.let { versionCache[it] = Result.success(v) }
                }
                // Updating (or switching the site of) a mod that is turned off keeps it off.
                if (pid == projectId && turnedOff) File(mods, file.filename).renameTo(File(mods, file.filename + ".disabled"))
            }
            skipped.forEach { plan.remove(it) }
            // Required dependencies that were installed but turned off are turned back on.
            val needed = plan.values.flatMap { v -> v.dependencies.filter { it.type == "required" }.mapNotNull { it.projectId } }.toSet()
            installed.filter { !it.enabled && it.projectId in needed }.forEach { setEnabled(it, true) }

            InstallResult(names[projectId] ?: projectId, plan.keys.filter { it != projectId }.map { names[it] ?: it })
        }

    // ---- texture packs (the profile's "resourcepacks" folder; where they came from is kept like for CurseForge mods)

    fun packsDir(profile: Profile) = File(GameLauncher.instanceDir(profile), "resourcepacks")

    /** Project ids of the texture packs installed from the library. */
    fun packIds(profile: Profile): Set<String> = readSources(packsDir(profile)).filterKeys { File(packsDir(profile), it).exists() }.values.map { it.projectId }.toSet()

    /** Simplified titles of the installed texture packs, so a pack shows as installed on both sites. */
    fun packNames(profile: Profile): Set<String> = readSources(packsDir(profile)).filterKeys { File(packsDir(profile), it).exists() }.values.map { simpleName(it.title) }.toSet()

    /**
     * Installs texture pack [projectId] ([version], or the newest release) into the profile. An older file of the
     * same pack is replaced.
     */
    suspend fun installPack(profile: Profile, projectId: String, version: Modrinth.Version? = null, progress: (String) -> Unit = {}): InstallResult =
        installLock.withLock {
            withContext(Dispatchers.IO) {
                val dir = packsDir(profile).apply { mkdirs() }
                val cf = CurseForge.isCf(projectId)
                val v = version ?: run {
                    val list = if (cf) CurseForge.versions(projectId, profile.version, packs = true) else Modrinth.versions(projectId, profile.version, packs = true)
                    list.firstOrNull { it.type == "release" } ?: list.firstOrNull()
                } ?: error(Strings.current("lib.no_version").replace("%s", profile.version))
                val brief = if (cf) CurseForge.brief(projectId) else Modrinth.projects(listOf(projectId)).firstOrNull()
                val title = brief?.title ?: projectId
                val file = v.primaryFile ?: error(Strings.current("lib.no_version").replace("%s", profile.version))
                if (cf && file.url.isBlank()) error(Strings.current("lib.cf_blocked").replace("%s", title))
                check(if (cf) CurseForge.trusted(file.url) else file.url.startsWith("https://cdn.modrinth.com/")) { Strings.current("lib.untrusted") }
                val safeName = file.filename.isNotBlank() && !file.filename.contains('/') && !file.filename.contains('\\') && !file.filename.startsWith(".")
                check(safeName && file.filename.endsWith(".zip", ignoreCase = true)) { Strings.current("lib.bad_name") }
                progress(Strings.current("lib.downloading").replace("%s", title))
                val temp = File(dir, ".${file.filename}.new")
                Net.download(file.url, temp, file.sha1)
                readSources(dir).filter { it.value.projectId == projectId && it.key != file.filename }.keys.forEach { File(dir, it).delete() }
                File(dir, file.filename).delete()
                check(temp.renameTo(File(dir, file.filename))) { Strings.current("lib.bad_name") }
                writeSource(dir, file.filename, Source(projectId, v.id, title, brief?.iconUrl, v.number, file.sha1 ?: Net.sha1(File(dir, file.filename))))
                InstallResult(title, emptyList())
            }
        }

    @Volatile
    private var bundledIdsCache: Set<String>? = null

    /** Modrinth project ids of the mods every DNZ profile gets on PLAY (the API accepts slugs in place of ids). */
    fun bundledProjectIds(): Set<String> =
        bundledIdsCache ?: Modrinth.projects(GameLauncher.modrinthMods).map { it.id }.toSet().also { bundledIdsCache = it }

    private fun bestVersion(projectId: String, mc: String): Modrinth.Version? {
        val list = if (CurseForge.isCf(projectId)) CurseForge.versions(projectId, mc) else Modrinth.versions(projectId, mc)
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
