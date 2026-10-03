package dnz.launcher

import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files

/**
 * Account switching on a COPY of the Minecraft Launcher's account file (the real one is only read):
 * a second fake account is added to the copy, then switched to; everything else must stay the same.
 * Also loads the skin face of the real account. gradlew accountsTest
 */
fun main() {
    val official = Accounts.readOfficial()
    println("ACCOUNTS ${official.accounts.map { it.name }} active=${official.activeLocalId?.take(6)}...")
    val real = official.accounts.firstOrNull() ?: error("no Minecraft Launcher account on this computer")

    val dir = Files.createTempDirectory("dnz-accounts").toFile()
    try {
        val copy = File(dir, real.file!!.name)
        val root = JsonParser.parseString(real.file.readText()).asJsonObject
        val accounts = root.getAsJsonObject("accounts")
        val fake = accounts.get(real.localId).deepCopy().asJsonObject
        fake.getAsJsonObject("minecraftProfile").addProperty("name", "SecondTest")
        fake.getAsJsonObject("minecraftProfile").addProperty("id", "00000000000000000000000000000002")
        accounts.add("fakelocalid0000000000000000000002", fake)
        copy.writeText(root.toString())
        val before = JsonParser.parseString(copy.readText()).asJsonObject

        val ok = Accounts.switchOfficial(McAccount("SecondTest", "00000000000000000000000000000002", "fakelocalid0000000000000000000002", copy))
        val after = JsonParser.parseString(copy.readText()).asJsonObject
        val active = after["activeAccountLocalId"].asString
        after.remove("activeAccountLocalId")
        before.remove("activeAccountLocalId")
        val checks = listOf(
            "switch reported ok" to ok,
            "active account changed" to (active == "fakelocalid0000000000000000000002"),
            "nothing else changed" to (before == after),
            "backup made" to File(copy.path + ".dnz-backup").exists(),
        )
        checks.forEach { (name, pass) -> println((if (pass) "OK   " else "FAIL ") + name) }

        val head = Accounts.head(real.uuid)
        println("head of ${real.name}: " + (head?.let { "${it.width}x${it.height}" } ?: "none"))
        println(if (checks.all { it.second } && head != null) "ACCOUNTS OK" else "ACCOUNTS FAILED")
    } finally {
        dir.deleteRecursively()
    }
}
