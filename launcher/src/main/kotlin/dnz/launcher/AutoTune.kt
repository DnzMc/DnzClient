package dnz.launcher

import java.io.File

/**
 * The "Auto" button: picks a quality level from the computer's hardware and writes the game settings
 * (options.txt) of every profile, tuned for high FPS and low input delay (PvP), plus the RAM for the game.
 * The old options.txt is kept as a backup, so everything can be undone.
 */
object AutoTune {
    enum class Tier(val key: String) {
        Low("auto.tier.low"),
        Medium("auto.tier.medium"),
        High("auto.tier.high"),
        Ultra("auto.tier.ultra"),
    }

    data class Plan(
        val specs: PcSpecs,
        val tier: Tier,
        val ramGb: Int,
        val renderDistance: Int,
        val simulationDistance: Int,
        /** 0 = all, 1 = decreased, 2 = minimal (Minecraft's own numbers). */
        val particles: Int,
        /** options.txt lines to set, exactly as Minecraft writes them. */
        val options: Map<String, String>,
    )

    data class Result(val plan: Plan, val applied: List<String>, val skipped: List<String>, val previousRamGb: Float)

    /** A game folder whose options.txt Auto sets. */
    data class Target(val name: String, val dir: File)

    /** The launcher's profiles plus Modrinth App profiles that use DNZ Client (e.g. an installed DNZ .mrpack). */
    fun targets(profiles: List<Profile>): List<Target> =
        profiles.map { Target(it.name, GameLauncher.instanceDir(it)) } + modrinthAppProfiles()

    private fun modrinthAppProfiles(): List<Target> {
        val root = File(Platform.appData, "ModrinthApp/profiles")
        return root.listFiles { f -> f.isDirectory }?.filter { dir ->
            File(dir, "mods").listFiles { f -> f.name.startsWith("dnz-client") }?.isNotEmpty() == true
        }?.sortedBy { it.name }?.map { Target("Modrinth: ${it.name}", it) } ?: emptyList()
    }

    private const val BACKUP = "options.txt.dnz-before-auto"
    /** Marks an options.txt that Auto created itself (undo deletes it again). */
    private const val CREATED = "options.txt.dnz-created-by-auto"

    fun plan(specs: PcSpecs): Plan {
        // The weakest part decides: a strong graphics card doesn't help with too little RAM.
        val gpuTier = specs.bestGpu?.score ?: 0
        val ramTier = when {
            specs.ramGb < 7.5 -> 0
            specs.ramGb < 11.5 -> 1
            specs.ramGb < 15.0 -> 2
            else -> 3
        }
        val cpuTier = when {
            specs.cpuThreads <= 4 -> 0
            specs.cpuThreads <= 6 -> 1
            specs.cpuThreads <= 8 -> 2
            else -> 3
        }
        val tier = Tier.entries[minOf(gpuTier, ramTier, cpuTier)]
        val t = tier.ordinal

        val ram = when {
            specs.ramGb < 6.5 -> 2
            specs.ramGb < 9 -> 3
            specs.ramGb < 13 -> 4
            specs.ramGb < 20 -> 5
            specs.ramGb < 40 -> 6
            else -> 8
        }.coerceAtMost(maxOf(2, (specs.ramGb / 2).toInt())).coerceIn(2, 16)

        val render = listOf(6, 10, 14, 20)[t]
        val simulation = listOf(5, 8, 10, 12)[t]
        val particles = listOf(2, 1, 0, 0)[t]
        val options = linkedMapOf(
            "graphicsPreset" to "\"custom\"",
            "renderDistance" to render.toString(),
            "simulationDistance" to simulation.toString(),
            // 260 = "Unlimited". No VSync: less input delay, which matters most in PvP.
            // Mac: capped at the screen's refresh rate. Unlimited FPS only heats a MacBook up until it slows itself
            // down; at the cap the FPS stays steady and the fans quiet.
            "maxFps" to if (Platform.isMac) (specs.refreshRate ?: 60).coerceIn(30, 250).toString() else "260",
            "enableVsync" to "false",
            "particles" to particles.toString(),
            "entityDistanceScaling" to listOf("0.75", "1.0", "1.0", "1.25")[t],
            "entityShadows" to (t >= 1).toString(),
            "renderClouds" to listOf("\"false\"", "\"fast\"", "\"true\"", "\"true\"")[t],
            "biomeBlendRadius" to listOf(0, 1, 2, 3)[t].toString(),
            "mipmapLevels" to listOf(2, 4, 4, 4)[t].toString(),
            "ao" to (t >= 1).toString(),
            "cutoutLeaves" to (t >= 1).toString(),
            "weatherRadius" to listOf(5, 8, 10, 10)[t].toString(),
            "chunkSectionFadeInTime" to if (t == 0) "0.0" else "0.75",
            "menuBackgroundBlurriness" to if (t == 0) "0" else "5",
            "improvedTransparency" to "false",
            // Measured 01.10.2026 (RTX 3050 Ti, driver 617): OpenGL was faster than Vulkan (normal world 246 vs 187 FPS)
            // and Vulkan failed to close cleanly; until that changes, AUTO picks OpenGL.
            "preferredGraphicsBackend" to "\"opengl\"",
        )
        return Plan(specs, tier, ram, render, simulation, particles, options)
    }

    /**
     * Writes the plan into every profile's options.txt. Profiles whose game is open right now are skipped,
     * because the game would overwrite the file when it closes.
     */
    fun apply(plan: Plan, targets: List<Target>, previousRamGb: Float): Result {
        val running = plan.specs.runningGameDirs.map { normalize(File(it)) }.toSet()
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        for (target in targets) {
            if (normalize(target.dir) in running) {
                skipped += target.name
                continue
            }
            if (runCatching { writeOptions(target.dir, plan.options) }.isSuccess) applied += target.name else skipped += target.name
        }
        return Result(plan, applied, skipped, previousRamGb)
    }

    /** Merges [options] into dir/options.txt: those keys change, every other line stays as it was. */
    fun writeOptions(dir: File, options: Map<String, String>) {
        dir.mkdirs()
        val file = File(dir, "options.txt")
        val backup = File(dir, BACKUP)
        val created = File(dir, CREATED)
        if (file.exists()) {
            if (!backup.exists() && !created.exists()) file.copyTo(backup)
        } else {
            created.writeText("created")
        }
        val lines = if (file.exists()) file.readLines(Charsets.UTF_8) else emptyList()
        val remaining = LinkedHashMap(options)
        val out = lines.map { line ->
            val key = line.substringBefore(':')
            remaining.remove(key)?.let { "$key:$it" } ?: line
        } + remaining.map { (key, value) -> "$key:$value" }
        val temp = File(dir, "options.txt.tmp")
        temp.writeText(out.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        file.delete()
        check(temp.renameTo(file)) { "options.txt could not be written" }
    }

    /** Puts back the options.txt files from before Auto (skipping games that are open). Returns the restored profiles. */
    fun undo(targets: List<Target>, runningGameDirs: List<String>): List<String> {
        val running = runningGameDirs.map { normalize(File(it)) }.toSet()
        val restored = mutableListOf<String>()
        for (target in targets) {
            val dir = target.dir
            if (normalize(dir) in running) continue
            val file = File(dir, "options.txt")
            val backup = File(dir, BACKUP)
            val created = File(dir, CREATED)
            when {
                backup.exists() -> {
                    file.delete()
                    if (backup.renameTo(file)) restored += target.name
                }
                created.exists() -> {
                    file.delete()
                    created.delete()
                    restored += target.name
                }
            }
        }
        return restored
    }

    private fun normalize(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath).lowercase().trimEnd('\\', '/')
}
