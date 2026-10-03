package dnz.launcher

import kotlinx.coroutines.runBlocking

/** Developer check for the "official Minecraft Launcher" mode without the UI. */
fun main(args: Array<String>) = runBlocking {
    val version = args.firstOrNull() ?: "26.3"
    OfficialLauncher.play(Profile("DNZ PvP", version, "test"), 4) { text, value -> println("${(value * 100).toInt()}% $text") }
    println("DONE")
}
