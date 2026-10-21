package dnz.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.util.Base64
import javax.imageio.ImageIO

/** Another player's skin, found by name: shown on the 3D character and can be put on the signed-in account. */
class FoundSkin(val name: String, val url: String, val skin: LoadedSkin, val png: ByteArray? = null) {
    /** From a file: the same picture with slim (Alex) or wide (Steve) arms. */
    fun withSlim(slim: Boolean) = FoundSkin(name, url, LoadedSkin(skin.image, slim, skin.legacy), png)
}

object SkinSearch {
    /** A skin PNG from this computer (64x64, or old 64x32); null when it is not a Minecraft skin. */
    fun fromFile(file: java.io.File): FoundSkin? {
        val bytes = file.readBytes()
        if (bytes.size > 1 shl 20) return null
        val image = runCatching { ImageIO.read(bytes.inputStream()) }.getOrNull() ?: return null
        if (image.width != 64 || (image.height != 64 && image.height != 32)) return null
        return FoundSkin(file.nameWithoutExtension, "", LoadedSkin(Image.makeFromBitmap(image.toComposeImageBitmap().asSkiaBitmap()), false, image.height == 32), bytes)
    }

    /** Mojang name → the player's current skin; null when there is no such player. */
    suspend fun find(name: String): FoundSkin? = withContext(Dispatchers.IO) {
        val clean = name.trim()
        require(clean.matches(Regex("[A-Za-z0-9_]{1,16}")))
        val profile = runCatching { Net.json("https://api.mojang.com/users/profiles/minecraft/$clean").asJsonObject }.getOrNull()
            ?: return@withContext null
        val uuid = profile["id"].asString
        val session = Net.json("https://sessionserver.mojang.com/session/minecraft/profile/$uuid").asJsonObject
        val textures = session.getAsJsonArray("properties").map { it.asJsonObject }.first { it["name"].asString == "textures" }
        val decoded = JsonParser.parseString(String(Base64.getDecoder().decode(textures["value"].asString))).asJsonObject
        val skin = decoded.getAsJsonObject("textures").getAsJsonObject("SKIN") ?: return@withContext null
        val url = skin["url"].asString.replace("http://", "https://")
        check(url.startsWith("https://textures.minecraft.net/"))
        val image = ImageIO.read(Net.bytes(url).inputStream())
        val slim = skin.getAsJsonObject("metadata")?.get("model")?.asString == "slim"
        FoundSkin(
            profile["name"].asString, url,
            LoadedSkin(Image.makeFromBitmap(image.toComposeImageBitmap().asSkiaBitmap()), slim, image.height == 32),
        )
    }

    /** Puts [found]'s skin on the Microsoft account (Minecraft's own skin change, same as on minecraft.net). */
    suspend fun apply(account: Account, found: FoundSkin) = withContext(Dispatchers.IO) {
        // the skin goes up as a PNG file: Mojang refuses skin changes by URL (HTTP 403)
        val png = found.png ?: Net.bytes(found.url)
        val boundary = "----DNZ" + System.nanoTime()
        val body = java.io.ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n${if (found.skin.slim) "slim" else "classic"}\r\n".toByteArray())
            write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n".toByteArray())
            write(png)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        val request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.minecraftservices.com/minecraft/profile/skins"))
            .header("Authorization", "Bearer ${account.accessToken}")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .timeout(java.time.Duration.ofSeconds(30))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body)).build()
        val response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
        Accounts.forgetSkin(account.uuid)
    }
}

/** Under the 3D character: a player name box; the found skin is previewed on the character and can be used. */
@Composable
fun SkinFinder(state: LauncherState, found: FoundSkin?, onFound: (FoundSkin?) -> Unit, onApplied: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun t(tr: String, en: String, de: String) = when (state.language) {
        Lang.TR -> tr
        Lang.DE -> de
        Lang.EN -> en
    }
    fun search() {
        if (busy || name.isBlank()) return
        if (!name.trim().matches(Regex("[A-Za-z0-9_]{1,16}"))) {
            message = t("Geçersiz oyuncu adı", "Not a valid player name", "Kein gültiger Spielername")
            return
        }
        busy = true
        message = null
        scope.launch {
            val result = runCatching { SkinSearch.find(name) }
            busy = false
            result.onSuccess {
                if (it == null) message = t("Bu isimde oyuncu yok", "No player with that name", "Kein Spieler mit diesem Namen")
                onFound(it)
            }.onFailure { message = t("Bulunamadı, internetini kontrol et", "Couldn't search, check your internet", "Suche fehlgeschlagen, Internet prüfen") }
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Dnz.Surface)
                .border(1.dp, Dnz.Border, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = Dnz.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (name.isEmpty()) Text(t("Oyuncu adıyla skin ara", "Search a skin by player name", "Skin nach Spielername suchen"), color = Dnz.Muted, fontSize = 13.sp)
                BasicTextField(
                    name, { name = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16) },
                    singleLine = true, textStyle = TextStyle(color = Dnz.Text, fontSize = 13.sp), cursorBrush = SolidColor(Dnz.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) { search(); true } else false },
                )
            }
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(9.dp)).background(Dnz.Accent).dnzClickable { search() }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(color = Dnz.OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                else Text(t("Ara", "Search", "Suchen"), color = Dnz.OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        // A skin PNG from this computer
        Row(
            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp)).background(Dnz.Surface)
                .border(1.dp, Dnz.Border, RoundedCornerShape(10.dp)).dnzClickable {
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, t("Skin dosyası seç", "Choose a skin file", "Skin-Datei wählen"), java.awt.FileDialog.LOAD)
                    dialog.setFilenameFilter { _, n -> n.lowercase().endsWith(".png") }
                    dialog.file = "*.png"
                    dialog.isVisible = true
                    val picked = dialog.files.firstOrNull() ?: return@dnzClickable
                    val skin = runCatching { SkinSearch.fromFile(picked) }.getOrNull()
                    if (skin == null) {
                        message = t("Bu bir Minecraft skini değil (64x64 PNG olmalı)", "Not a Minecraft skin (must be a 64x64 PNG)", "Kein Minecraft-Skin (64x64 PNG nötig)")
                    } else {
                        message = null
                        onFound(skin)
                    }
                },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, null, tint = Dnz.Text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(t("Dosyadan skin yükle", "Upload a skin file", "Skin-Datei hochladen"), color = Dnz.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Dnz.Muted, fontSize = 12.sp)
        }
        if (found != null) {
            Spacer(Modifier.height(10.dp))
            Text(t("Önizleme: ${found.name}", "Preview: ${found.name}", "Vorschau: ${found.name}"), color = Dnz.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            // A file doesn't say its arm width: the player picks it
            if (found.png != null) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.clip(RoundedCornerShape(9.dp)).background(Dnz.Surface).padding(3.dp)) {
                    listOf(false to t("Geniş kol (Steve)", "Wide arms (Steve)", "Breite Arme (Steve)"), true to t("İnce kol (Alex)", "Slim arms (Alex)", "Schmale Arme (Alex)")).forEach { (slim, label) ->
                        val on = found.skin.slim == slim
                        Text(
                            label, color = if (on) Dnz.OnAccent else Dnz.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(if (on) Dnz.Accent else androidx.compose.ui.graphics.Color.Transparent)
                                .dnzClickable { onFound(found.withSlim(slim)) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(9.dp)).background(Dnz.Accent).dnzClickable {
                        val account = state.account
                        if (busy) return@dnzClickable
                        if (account == null || account.offline || account.accessToken.isEmpty()) {
                            message = t("Önce Microsoft hesabınla giriş yap", "Sign in with Microsoft first", "Melde dich zuerst mit Microsoft an")
                            return@dnzClickable
                        }
                        busy = true
                        scope.launch {
                            // the Minecraft sign-in only lasts about a day: renew it first (as PLAY does)
                            val fresh = withContext(Dispatchers.IO) { runCatching { MicrosoftAuth.restore() }.getOrNull() }
                                ?.takeIf { it.accessToken.isNotEmpty() }?.also { state.account = it } ?: account
                            val result = runCatching { SkinSearch.apply(fresh, found) }
                            result.exceptionOrNull()?.let { System.err.println("Skin change failed: $it") }
                            busy = false
                            result.onSuccess {
                                message = t("Skinin değişti! Oyunda görünmesi bir dakika sürebilir.", "Skin changed! It may take a minute to show in game.", "Skin geändert! Im Spiel kann es eine Minute dauern.")
                                onFound(null)
                                onApplied()
                            }.onFailure { message = t("Skin değiştirilemedi, sonra tekrar dene", "Couldn't change the skin, try again later", "Skin konnte nicht geändert werden") }
                        }
                    }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t("Skinim yap", "Use as my skin", "Als meinen Skin nutzen"), color = Dnz.OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(9.dp)).background(Dnz.Surface).dnzClickable { onFound(null); message = null }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t("Kendi skinim", "My skin", "Mein Skin"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
    }
}
