package dnz.launcher

import kotlinx.coroutines.runBlocking

/**
 * Checks the mod library without the window (gradlew libraryTest): installs a mod with its required
 * dependencies into a throwaway profile, turns one off and on, removes it, then deletes the profile.
 */
fun main(args: Array<String>) = runBlocking {
    // Installs and AUTO run on LauncherState.scope (Dispatchers.Main); it needs kotlinx-coroutines-swing.
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { println("Main dispatcher OK") }
    checkSettings()
    checkAutoTune()
    val mc = args.getOrNull(0) ?: "26.3"
    val mod = args.getOrNull(1) ?: "iris"
    val profile = Profile("DNZ_LibraryTest", mc, "test")
    val dir = GameLauncher.instanceDir(profile)
    dir.deleteRecursively()
    try {
        val result = Library.install(profile, mod) { println("  $it") }
        println("Installed ${result.main}; dependencies: ${result.dependencies}")

        val list = Library.scan(profile, checkUpdates = true)
        list.forEach { println(" - ${it.title} ${it.versionNumber} on=${it.enabled} update=${it.update?.number} file=${it.file.name}") }
        check(list.isNotEmpty()) { "nothing installed" }

        val first = list.first()
        val off = Library.setEnabled(first, false)
        println("Turned off: ${off?.name}")
        val again = Library.scan(profile, checkUpdates = false).first { it.projectId == first.projectId }
        check(!again.enabled) { "still on" }
        check(again.title == first.title) { "not recognised after turning off" }
        val on = Library.setEnabled(again, true)
        println("Turned on: ${on?.name}")

        val toRemove = Library.scan(profile, checkUpdates = false).first()
        check(Library.remove(toRemove)) { "could not remove" }
        println("Removed ${toRemove.title}; left: ${Library.scan(profile, checkUpdates = false).map { it.title }}")
        println("OK")
    } finally {
        dir.deleteRecursively()
    }
}

/** The AUTO button picks sensible levels, keeps the player's other options, backs up and undoes (throwaway folders only). */
private fun checkAutoTune() {
    fun pc(ram: Double, threads: Int, gpu: String, vram: Double) =
        PcSpecs(ram, "Test CPU", threads / 2, threads, listOf(PcSpecs.Gpu(gpu, vram, PcScan.isDedicated(gpu))), false, 144, emptyList())
    val cases = listOf(
        Triple(pc(16.0, 20, "NVIDIA GeForce RTX 3050 Ti Laptop GPU", 4.0), AutoTune.Tier.High, 5),
        Triple(pc(8.0, 8, "Intel(R) UHD Graphics", 1.0), AutoTune.Tier.Low, 3),
        Triple(pc(32.0, 16, "NVIDIA GeForce RTX 4070", 12.0), AutoTune.Tier.Ultra, 6),
        Triple(pc(16.0, 12, "AMD Radeon(TM) Graphics", 2.0), AutoTune.Tier.Low, 5),
        Triple(pc(12.0, 6, "AMD Radeon RX 6600", 8.0), AutoTune.Tier.Medium, 4),
    )
    for ((specs, tier, ram) in cases) {
        val plan = AutoTune.plan(specs)
        check(plan.tier == tier && plan.ramGb == ram) {
            "${specs.gpus[0].name}, ${specs.ramGb} GB, ${specs.cpuThreads} threads -> ${plan.tier} ${plan.ramGb} GB (expected $tier $ram GB)"
        }
    }

    // options.txt: only Auto's keys change; a second Auto keeps the first backup.
    val dir = kotlin.io.path.createTempDirectory("dnz-auto").toFile()
    try {
        java.io.File(dir, "options.txt").writeText("version:4000\nrenderDistance:16\nfov:0.5\nkey_key.jump:key.keyboard.space\nmaxFps:120\n")
        AutoTune.writeOptions(dir, AutoTune.plan(cases[0].first).options)
        val text = java.io.File(dir, "options.txt").readText()
        check("renderDistance:14" in text && "maxFps:260" in text && "enableVsync:false" in text) { "values not set:\n$text" }
        check("fov:0.5" in text && "version:4000" in text && "key_key.jump:key.keyboard.space" in text) { "other options lost:\n$text" }
        AutoTune.writeOptions(dir, AutoTune.plan(cases[1].first).options)
        check("renderDistance:16" in java.io.File(dir, "options.txt.dnz-before-auto").readText()) { "backup was overwritten" }
    } finally {
        dir.deleteRecursively()
    }

    // apply + undo on a throwaway profile; a profile whose game is open is skipped.
    val profile = Profile("DNZ_AutoTest", "26.3", "test")
    val instance = GameLauncher.instanceDir(profile)
    instance.deleteRecursively()
    try {
        instance.mkdirs()
        java.io.File(instance, "options.txt").writeText("renderDistance:16\nfov:0.5\n")
        val plan = AutoTune.plan(cases[1].first)
        val result = AutoTune.apply(plan, listOf(AutoTune.Target(profile.name, instance)), 4f)
        check(result.applied == listOf("DNZ_AutoTest")) { "not applied: $result" }
        check("renderDistance:6" in java.io.File(instance, "options.txt").readText()) { "Low level not written" }
        check(AutoTune.undo(listOf(AutoTune.Target(profile.name, instance)), emptyList()) == listOf("DNZ_AutoTest")) { "undo did nothing" }
        check(java.io.File(instance, "options.txt").readText() == "renderDistance:16\nfov:0.5\n") { "undo did not restore the old file" }
        val busy = plan.copy(specs = plan.specs.copy(runningGameDirs = listOf(instance.absolutePath)))
        check(AutoTune.apply(busy, listOf(AutoTune.Target(profile.name, instance)), 4f).skipped == listOf("DNZ_AutoTest")) { "open game was not skipped" }
    } finally {
        instance.deleteRecursively()
    }

    // This computer (read-only scan).
    val real = PcScan.scan()
    println("This PC: ${"%.1f".format(real.ramGb)} GB RAM, ${real.cpuName} (${real.cpuCores}c/${real.cpuThreads}t), " +
        "GPUs=${real.gpus}, laptop=${real.laptop}, refresh=${real.refreshRate}, open games=${real.runningGameDirs} -> ${AutoTune.plan(real).tier}")
    println("AutoTune OK")
}

/** Settings survive a restart (uses a temporary file, not the player's real settings). */
private fun checkSettings() {
    val real = Settings.file
    val temp = java.io.File.createTempFile("dnz-settings", ".json")
    try {
        Settings.file = temp
        temp.delete()
        val before = LauncherState()
        before.language = Lang.EN
        before.ramGb = 7f
        before.launchMode = LaunchMode.Direct
        before.profiles.add(Profile("Test Profil", "26.2", "DNZ Client"))
        before.selectedProfile = before.profiles.size - 1
        Settings.save(Settings.snapshot(before))

        val after = LauncherState()
        check(after.language == Lang.EN && after.ramGb == 7f && after.launchMode == LaunchMode.Direct) { "settings not loaded: ${temp.readText()}" }
        check(after.profiles.last().name == "Test Profil" && after.selectedProfile == after.profiles.size - 1) { "profiles not loaded" }
        check(after.version == "26.2") { "version not taken from the selected profile" }
        println("Settings OK")
    } finally {
        Settings.file = real
        temp.delete()
        Strings.currentLang = Lang.TR
    }
}
