package dnz.launcher

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * "Sign in with Microsoft" for starting the game directly from DNZ.
 *
 * The player signs in on Microsoft's own page (microsoft.com/link, device code), DNZ never sees the password.
 * Chain: Microsoft token -> Xbox Live -> XSTS -> Minecraft token -> Minecraft profile.
 * Only the Microsoft refresh token is kept, encrypted with Windows (DPAPI, only this Windows user can read it).
 *
 * Needs an Azure app registration (CLIENT_ID) that Mojang has approved for Minecraft; until then the last step
 * answers 403 and the player is told the approval is still pending.
 */
object MicrosoftAuth {
    /** Mojang approved DNZ Launcher for Minecraft sign-in. Set to true once the approval arrives. */
    const val approved = true // Mojang approved the App ID on 06.10.2026

    /** Application (client) ID from portal.azure.com -> App registrations ("microsoft.clientId" in dnz-keys.properties). */
    val CLIENT_ID = Keys.get("microsoft.clientId")

    val ready: Boolean get() = CLIENT_ID.isNotBlank()

    private val saved = File(GameLauncher.root, "microsoft.dat")
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()

    data class DeviceCode(val userCode: String, val verificationUri: String, val deviceCode: String, val intervalSec: Int, val expiresAt: Long)

    class AuthException(val reason: Reason, message: String) : Exception(message)

    enum class Reason { NOT_APPROVED, NO_XBOX, CHILD, NO_MINECRAFT, EXPIRED, CANCELLED, OTHER }

    // ------------------------------------------------------------------ sign in (browser)

    /**
     * Normal sign-in: Microsoft's own page opens in the browser, and after signing in it sends the player back to a
     * tiny page served by DNZ on this computer (localhost), so no code has to be typed.
     * The app registration needs the redirect URI "http://localhost" (Mobile and desktop applications).
     */
    class BrowserLogin internal constructor(
        val url: String,
        private val server: com.sun.net.httpserver.HttpServer,
        private val result: java.util.concurrent.CompletableFuture<String>,
        private val redirect: String,
        private val verifier: String,
    ) {
        /** Waits for Microsoft to send the player back, then logs in to Minecraft. [cancelled] stops waiting. */
        fun await(cancelled: () -> Boolean): Account {
            try {
                var code: String? = null
                val end = System.currentTimeMillis() + 15 * 60_000
                while (code == null) {
                    if (cancelled()) throw AuthException(Reason.CANCELLED, "cancelled")
                    if (System.currentTimeMillis() > end) throw AuthException(Reason.EXPIRED, "timed out")
                    code = runCatching { result.get(300, java.util.concurrent.TimeUnit.MILLISECONDS) }
                        .getOrElse { e -> if (e is java.util.concurrent.TimeoutException) null else throw e.cause ?: e }
                }
                val o = postForm(
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                    mapOf("client_id" to CLIENT_ID, "grant_type" to "authorization_code", "code" to code, "redirect_uri" to redirect,
                        "code_verifier" to verifier, "scope" to "XboxLive.signin offline_access"),
                    allowError = true,
                )
                if (o.has("error")) throw AuthException(Reason.OTHER, o["error_description"]?.asString ?: o["error"].asString)
                return loginWithMicrosoft(o["access_token"].asString, o["refresh_token"].asString)
            } finally {
                close()
            }
        }

        fun close() = server.stop(0)
    }

    fun startBrowser(): BrowserLogin {
        val random = java.security.SecureRandom()
        fun token(bytes: Int) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))
        val verifier = token(48)
        val challenge = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val stateToken = token(16)

        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0)
        val redirect = "http://localhost:${server.address.port}"
        val result = java.util.concurrent.CompletableFuture<String>()
        server.createContext("/") { exchange ->
            val query = exchange.requestURI.rawQuery.orEmpty().split('&').filter { '=' in it }
                .associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
            val ok = query["state"] == stateToken && query["code"] != null
            val page = if (ok) donePage("Giriş tamam / Signed in", "DNZ Launcher'a dönebilirsin. You can go back to DNZ Launcher.")
            else donePage("Giriş yapılamadı / Sign-in failed", query["error_description"] ?: query["error"] ?: "")
            val bytes = page.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            when {
                ok -> result.complete(query["code"])
                query["state"] == stateToken && query["error"] == "access_denied" -> result.completeExceptionally(AuthException(Reason.CANCELLED, "declined"))
                query["state"] == stateToken -> result.completeExceptionally(AuthException(Reason.OTHER, query["error_description"] ?: query["error"] ?: "?"))
            }
        }
        server.start()

        fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
        val url = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=$CLIENT_ID&response_type=code" +
            "&redirect_uri=${enc(redirect)}&scope=${enc("XboxLive.signin offline_access")}&code_challenge=$challenge" +
            "&code_challenge_method=S256&state=$stateToken&prompt=select_account"
        return BrowserLogin(url, server, result, redirect, verifier)
    }

    private fun donePage(title: String, text: String) = """<!doctype html><html><head><meta charset="utf-8"><title>DNZ Launcher</title>
        <style>body{margin:0;height:100vh;display:flex;align-items:center;justify-content:center;background:#0d0d12;color:#eee;font-family:Segoe UI,sans-serif}
        div{text-align:center}h1{font-size:28px;margin:0 0 10px}p{color:#9a9aa8}</style></head>
        <body><div><h1>${title.escapeHtml()}</h1><p>${text.escapeHtml()}</p></div></body></html>"""

    private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ------------------------------------------------------------------ sign in (code, backup)

    fun startDeviceCode(): DeviceCode {
        val o = postForm(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
            mapOf("client_id" to CLIENT_ID, "scope" to "XboxLive.signin offline_access"),
        )
        return DeviceCode(
            o["user_code"].asString, o["verification_uri"].asString, o["device_code"].asString,
            o["interval"]?.asInt ?: 5, System.currentTimeMillis() + (o["expires_in"]?.asLong ?: 900) * 1000,
        )
    }

    /** Waits until the player has signed in on Microsoft's page, then logs in to Minecraft. [cancelled] stops waiting. */
    fun finishDeviceCode(code: DeviceCode, cancelled: () -> Boolean): Account {
        var interval = code.intervalSec
        while (true) {
            if (cancelled()) throw AuthException(Reason.CANCELLED, "cancelled")
            if (System.currentTimeMillis() > code.expiresAt) throw AuthException(Reason.EXPIRED, "code expired")
            Thread.sleep(interval * 1000L)
            val o = postForm(
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                mapOf("client_id" to CLIENT_ID, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code", "device_code" to code.deviceCode),
                allowError = true,
            )
            when (o["error"]?.asString) {
                null -> return loginWithMicrosoft(o["access_token"].asString, o["refresh_token"].asString)
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> throw AuthException(Reason.EXPIRED, "code expired")
                "authorization_declined" -> throw AuthException(Reason.CANCELLED, "declined")
                else -> throw AuthException(Reason.OTHER, o["error_description"]?.asString ?: o["error"].asString)
            }
        }
    }

    /** Signs in again with the saved refresh token (no window). Null if there is none or it no longer works. */
    fun restore(): Account? {
        if (!ready || !saved.isFile) return null
        val refresh = runCatching { Secret.decrypt(saved.readText()) }.getOrNull() ?: return null
        val o = postForm(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
            mapOf("client_id" to CLIENT_ID, "grant_type" to "refresh_token", "refresh_token" to refresh, "scope" to "XboxLive.signin offline_access"),
            allowError = true,
        )
        if (o.has("error")) return null
        return loginWithMicrosoft(o["access_token"].asString, o["refresh_token"]?.asString ?: refresh)
    }

    fun signOut() {
        saved.delete()
        Secret.forget()
    }

    private fun loginWithMicrosoft(msToken: String, refreshToken: String): Account {
        // Xbox Live
        val xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", JsonObject().apply {
            add("Properties", JsonObject().apply {
                addProperty("AuthMethod", "RPS")
                addProperty("SiteName", "user.auth.xboxlive.com")
                addProperty("RpsTicket", "d=$msToken")
            })
            addProperty("RelyingParty", "http://auth.xboxlive.com")
            addProperty("TokenType", "JWT")
        })
        val xblToken = xbl.second["Token"].asString
        val uhs = xbl.second.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")[0].asJsonObject["uhs"].asString

        // XSTS for Minecraft
        val xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", JsonObject().apply {
            add("Properties", JsonObject().apply {
                addProperty("SandboxId", "RETAIL")
                add("UserTokens", com.google.gson.JsonArray().apply { add(xblToken) })
            })
            addProperty("RelyingParty", "rp://api.minecraftservices.com/")
            addProperty("TokenType", "JWT")
        })
        if (xsts.first == 401) {
            when (xsts.second["XErr"]?.asLong) {
                2148916233L -> throw AuthException(Reason.NO_XBOX, "no Xbox profile")
                2148916235L, 2148916236L, 2148916237L, 2148916238L -> throw AuthException(Reason.CHILD, "account needs adult approval")
                else -> throw AuthException(Reason.OTHER, "Xbox: ${xsts.second}")
            }
        }
        val xstsToken = xsts.second["Token"].asString

        // Minecraft (403 here = this app is not approved by Mojang yet)
        val mc = postJson("https://api.minecraftservices.com/authentication/login_with_xbox",
            JsonObject().apply { addProperty("identityToken", "XBL3.0 x=$uhs;$xstsToken") })
        if (mc.first == 403) throw AuthException(Reason.NOT_APPROVED, "not approved by Mojang yet")
        check(mc.first == 200) { "Minecraft login: HTTP ${mc.first}" }
        val mcToken = mc.second["access_token"].asString

        val profileRequest = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
            .header("Authorization", "Bearer $mcToken").timeout(Duration.ofSeconds(20)).build()
        val profile = http.send(profileRequest, HttpResponse.BodyHandlers.ofString())
        if (profile.statusCode() == 404) throw AuthException(Reason.NO_MINECRAFT, "this account does not own Minecraft")
        check(profile.statusCode() == 200) { "Minecraft profile: HTTP ${profile.statusCode()}" }
        val p = JsonParser.parseString(profile.body()).asJsonObject

        saved.writeText(Secret.encrypt(refreshToken))
        if (!Platform.isWindows) {
            saved.setReadable(false, false)
            saved.setReadable(true, true)
        }
        return Account(p["name"].asString, p["id"].asString, mcToken, offline = false)
    }

    // ------------------------------------------------------------------ http

    private fun postForm(url: String, form: Map<String, String>, allowError: Boolean = false): JsonObject {
        val body = form.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, Charsets.UTF_8) }
        val request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        val json = runCatching { JsonParser.parseString(response.body()).asJsonObject }.getOrElse { JsonObject() }
        if (!allowError) check(response.statusCode() == 200) { json["error_description"]?.asString ?: "HTTP ${response.statusCode()}" }
        return json
    }

    private fun postJson(url: String, body: JsonObject): Pair<Int, JsonObject> {
        val request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json").header("Accept", "application/json")
            .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        val json = runCatching { JsonParser.parseString(response.body()).asJsonObject }.getOrElse { JsonObject() }
        return response.statusCode() to json
    }

    /** Windows DPAPI through PowerShell: only this Windows user on this computer can decrypt. Secrets go through stdin. */
    private object Secret {
        private const val KEYCHAIN = "keychain"
        private const val SERVICE = "DNZ Launcher Microsoft"

        /** Mac: the macOS Keychain keeps it (the file only says so). Linux: the file itself, readable by this user only. */
        fun encrypt(text: String): String = when {
            Platform.isMac -> {
                val process = ProcessBuilder("security", "add-generic-password", "-U", "-a", "DNZ", "-s", SERVICE, "-w", text).start()
                check(process.waitFor() == 0) { "keychain failed" }
                KEYCHAIN
            }
            Platform.isLinux -> "plain:" + java.util.Base64.getEncoder().encodeToString(text.toByteArray())
            else -> windowsEncrypt(text)
        }

        fun decrypt(saved: String): String = when {
            saved.trim() == KEYCHAIN -> Platform.run("security", "find-generic-password", "-a", "DNZ", "-s", SERVICE, "-w")
                ?.takeIf { it.isNotEmpty() } ?: error("not in keychain")
            saved.startsWith("plain:") -> String(java.util.Base64.getDecoder().decode(saved.removePrefix("plain:").trim()))
            else -> windowsDecrypt(saved)
        }

        fun forget() {
            if (Platform.isMac) Platform.run("security", "delete-generic-password", "-a", "DNZ", "-s", SERVICE)
        }

        private fun windowsEncrypt(text: String): String = run(
            "Add-Type -AssemblyName System.Security; \$in = [Console]::In.ReadToEnd(); " +
                "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes(\$in), \$null, 'CurrentUser'))",
            text,
        )

        private fun windowsDecrypt(base64: String): String = run(
            "Add-Type -AssemblyName System.Security; \$in = [Console]::In.ReadToEnd().Trim(); " +
                "[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect([Convert]::FromBase64String(\$in), \$null, 'CurrentUser'))",
            base64,
        )

        private fun run(script: String, input: String): String {
            val encoded = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
            val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded).start()
            process.outputStream.use { it.write(input.toByteArray(Charsets.UTF_8)) }
            val out = process.inputStream.bufferedReader().readText().trim()
            check(process.waitFor() == 0 && out.isNotEmpty()) { "encryption failed" }
            return out
        }
    }
}
