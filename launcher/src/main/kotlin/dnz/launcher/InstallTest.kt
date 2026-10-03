package dnz.launcher

import kotlinx.coroutines.runBlocking

/** Developer check: installs and starts a profile without the UI, printing progress. */
fun main(args: Array<String>) = runBlocking {
    val version = args.firstOrNull() ?: "26.3"
    var last = ""
    val process = GameLauncher.play(Profile("Test $version", version, "test"), Account.offline("DNZ_Test"), 3) { text, value ->
        val line = "${(value * 100).toInt()}% $text"
        if (line != last) println(line).also { last = line }
    }
    println("STARTED pid=${process.pid()}")
}
