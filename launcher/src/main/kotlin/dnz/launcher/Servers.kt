package dnz.launcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.Hashtable
import javax.naming.directory.InitialDirContext

/** A server in a profile's server list (the game's servers.dat). */
data class SavedServer(val name: String, val address: String, val iconBase64: String?)

/** What a server says about itself when pinged (the same info the game's server list shows). */
data class ServerStatus(
    val motd: AnnotatedString,
    val online: Int,
    val max: Int,
    val version: String,
    val pingMs: Long,
    val icon: ImageBitmap?,
)

/**
 * The server list of a profile: reads and edits its servers.dat, pings servers (Minecraft's status protocol,
 * nothing else is sent), and leaves a "join this server" note that DNZ Client uses when the game starts.
 */
object Servers {
    private fun file(profile: Profile) = File(GameLauncher.instanceDir(profile), "servers.dat")

    fun list(profile: Profile): List<SavedServer> {
        val f = file(profile)
        if (!f.isFile) return emptyList()
        return runCatching {
            val list = Nbt.read(f)["servers"] as? Nbt.TagList ?: return emptyList()
            list.items.filterIsInstance<Map<*, *>>().mapNotNull { s ->
                val ip = s["ip"] as? String ?: return@mapNotNull null
                SavedServer((s["name"] as? String)?.ifBlank { ip } ?: ip, ip, s["icon"] as? String)
            }
        }.getOrDefault(emptyList())
    }

    fun add(profile: Profile, name: String, address: String) {
        val f = file(profile).apply { parentFile.mkdirs() }
        val root = if (f.isFile) Nbt.read(f) else linkedMapOf()
        val list = root["servers"] as? Nbt.TagList ?: Nbt.TagList(Nbt.COMPOUND, mutableListOf()).also { root["servers"] = it }
        list.items.add(linkedMapOf<String, Any>("ip" to address.trim(), "name" to name.trim().ifBlank { address.trim() }, "hidden" to 0.toByte()))
        Nbt.write(f, root)
    }

    fun remove(profile: Profile, address: String) {
        val f = file(profile)
        if (!f.isFile) return
        val root = Nbt.read(f)
        val list = root["servers"] as? Nbt.TagList ?: return
        list.items.removeIf { (it as? Map<*, *>)?.get("ip") == address }
        Nbt.write(f, root)
    }

    /** Tells DNZ Client to join [address] as soon as the game has started (used once, ignored after 10 minutes). */
    fun requestJoin(profile: Profile, server: SavedServer) {
        val note = JsonObject().apply {
            addProperty("name", server.name)
            addProperty("address", server.address)
            addProperty("time", System.currentTimeMillis())
        }
        val f = File(GameLauncher.instanceDir(profile), "config/dnzclient-join.json").apply { parentFile.mkdirs() }
        f.writeText(note.toString())
    }

    // ------------------------------------------------------------------ status ping

    fun ping(address: String, timeoutMs: Int = 4000): ServerStatus {
        val (host, port) = resolve(address)
        Socket().use { socket ->
            socket.soTimeout = timeoutMs
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            val connectMs = (System.nanoTime() - start) / 1_000_000
            val out = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            // Handshake (next state 1 = status), then the status request.
            packet(out, 0x00) {
                varInt(it, -1)
                string(it, host)
                it.writeShort(port)
                varInt(it, 1)
            }
            packet(out, 0x00) {}
            readVarInt(input) // length
            readVarInt(input) // packet id
            val json = JsonParser.parseString(readString(input)).asJsonObject

            // Ping / pong for the latency; some servers close right away, then the connect time is used.
            val ping = runCatching {
                val t = System.nanoTime()
                packet(out, 0x01) { it.writeLong(System.currentTimeMillis()) }
                readVarInt(input)
                readVarInt(input)
                input.readLong()
                (System.nanoTime() - t) / 1_000_000
            }.getOrDefault(connectMs)

            val players = json.getAsJsonObject("players")
            val icon = json["favicon"]?.asString?.substringAfter("base64,", "")?.takeIf { it.isNotBlank() }?.let(::decodeIcon)
            return ServerStatus(
                motd = motd(json["description"]),
                online = players?.get("online")?.asInt ?: 0,
                max = players?.get("max")?.asInt ?: 0,
                version = json.getAsJsonObject("version")?.get("name")?.asString ?: "",
                pingMs = ping,
                icon = icon,
            )
        }
    }

    fun decodeIcon(base64: String): ImageBitmap? = runCatching {
        org.jetbrains.skia.Image.makeFromEncoded(Base64.getMimeDecoder().decode(base64)).toComposeImageBitmap()
    }.getOrNull()

    /** "host", "host:port", or a domain with a Minecraft SRV record (like the game does). */
    private fun resolve(address: String): Pair<String, Int> {
        val trimmed = address.trim()
        val colon = trimmed.lastIndexOf(':')
        if (colon > 0 && !trimmed.startsWith("[")) {
            trimmed.substring(colon + 1).toIntOrNull()?.let { return trimmed.substring(0, colon) to it }
        }
        runCatching {
            val env = Hashtable<String, String>().apply {
                put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory")
                put("com.sun.jndi.dns.timeout.initial", "2000")
            }
            val records = InitialDirContext(env).getAttributes("_minecraft._tcp.$trimmed", arrayOf("SRV"))["srv"]
            if (records != null && records.size() > 0) {
                val parts = records.get(0).toString().split(" ")
                return parts[3].trimEnd('.') to parts[2].toInt()
            }
        }
        return trimmed to 25565
    }

    private inline fun packet(out: DataOutputStream, id: Int, body: (DataOutputStream) -> Unit) {
        val bytes = ByteArrayOutputStream()
        val data = DataOutputStream(bytes)
        varInt(data, id)
        body(data)
        val raw = bytes.toByteArray()
        val framed = ByteArrayOutputStream()
        varInt(DataOutputStream(framed), raw.size)
        framed.write(raw)
        out.write(framed.toByteArray())
        out.flush()
    }

    private fun varInt(out: DataOutputStream, value: Int) {
        var v = value
        while (true) {
            if (v and 0x7F.inv() == 0) {
                out.writeByte(v)
                return
            }
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    private fun string(out: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        varInt(out, bytes.size)
        out.write(bytes)
    }

    private fun readVarInt(input: DataInputStream): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = input.readUnsignedByte()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            check(shift < 35) { "bad varint" }
        }
    }

    private fun readString(input: DataInputStream): String {
        val length = readVarInt(input)
        check(length in 0..1_000_000) { "bad length" }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    // ------------------------------------------------------------------ MOTD colors

    private val NAMED = mapOf(
        "black" to '0', "dark_blue" to '1', "dark_green" to '2', "dark_aqua" to '3', "dark_red" to '4', "dark_purple" to '5',
        "gold" to '6', "gray" to '7', "dark_gray" to '8', "blue" to '9', "green" to 'a', "aqua" to 'b', "red" to 'c',
        "light_purple" to 'd', "yellow" to 'e', "white" to 'f',
    )
    private val CODES = mapOf(
        '0' to 0x000000, '1' to 0x0000AA, '2' to 0x00AA00, '3' to 0x00AAAA, '4' to 0xAA0000, '5' to 0xAA00AA, '6' to 0xFFAA00,
        '7' to 0xAAAAAA, '8' to 0x555555, '9' to 0x5555FF, 'a' to 0x55FF55, 'b' to 0x55FFFF, 'c' to 0xFF5555, 'd' to 0xFF55FF,
        'e' to 0xFFFF55, 'f' to 0xFFFFFF,
    )

    /** The server description with its colors: plain text with § codes, or Minecraft's JSON text format. */
    fun motd(description: JsonElement?): AnnotatedString {
        val legacy = StringBuilder()
        fun walk(e: JsonElement?, color: String?, bold: Boolean) {
            when {
                e == null || e.isJsonNull -> Unit
                e.isJsonPrimitive -> legacy.append(prefix(color, bold)).append(e.asString)
                e.isJsonArray -> e.asJsonArray.forEach { walk(it, color, bold) }
                e.isJsonObject -> {
                    val o = e.asJsonObject
                    val c = o["color"]?.asString ?: color
                    val b = o["bold"]?.asBoolean ?: bold
                    o["text"]?.let { legacy.append(prefix(c, b)).append(it.asString) }
                    o["extra"]?.let { walk(it, c, b) }
                }
            }
        }
        walk(description, null, false)
        return parseLegacy(legacy.toString())
    }

    private fun prefix(color: String?, bold: Boolean): String {
        val sb = StringBuilder("§r")
        when {
            color == null -> Unit
            color.startsWith("#") -> sb.append("§#").append(color.drop(1).take(6))
            else -> NAMED[color]?.let { sb.append('§').append(it) }
        }
        if (bold) sb.append("§l")
        return sb.toString()
    }

    private fun parseLegacy(text: String): AnnotatedString = buildAnnotatedString {
        var color: Int? = null
        var bold = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '§' && i + 1 < text.length) {
                val code = text[i + 1].lowercaseChar()
                when {
                    code == '#' && i + 8 <= text.length -> {
                        color = text.substring(i + 2, i + 8).toIntOrNull(16)
                        i += 8
                        continue
                    }
                    code in CODES -> {
                        color = CODES[code]
                        bold = false
                    }
                    code == 'l' -> bold = true
                    code == 'r' -> {
                        color = null
                        bold = false
                    }
                }
                i += 2
                continue
            }
            val style = SpanStyle(
                color = color?.let { Color(0xFF000000 or it.toLong()) } ?: Color.Unspecified,
                fontWeight = if (bold) FontWeight.Bold else null,
            )
            withStyle(style) { append(ch) }
            i++
        }
    }
}
