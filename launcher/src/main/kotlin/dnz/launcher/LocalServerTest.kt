package dnz.launcher

import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/** Starts a throwaway local server (26.2, IP protection), waits for "Done" and the e4mc address, stops it, deletes it. */
fun main() {
    runBlocking { run() }
}

private suspend fun run() {
    val config = LocalServerConfig("DNZ Test Server", "26.2", LocalServers.freeFolder("dnz-test"), ramGb = 2, port = 25599)
    val server = try {
        LocalServers.start(config, null) { println("progress: $it") }
    } catch (e: Exception) {
        e.printStackTrace()
        exitProcess(1)
    }

    val end = System.currentTimeMillis() + 240_000
    while (System.currentTimeMillis() < end && !server.stopped && !(server.ready && server.domain != null)) Thread.sleep(1000)
    println("ready=${server.ready} domain=${server.domain} stopped=${server.stopped} lan=${LocalServers.lanAddress()}")
    server.lines.takeLast(25).forEach { println("  | $it") }
    LocalServers.stop()
    Thread.sleep(15_000)
    println("stopped=${server.stopped}")
    LocalServers.delete(config)
    exitProcess(0)
}
