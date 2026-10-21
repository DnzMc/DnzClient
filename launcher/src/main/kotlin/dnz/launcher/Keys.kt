package dnz.launcher

import java.util.Properties

/** Private IDs and keys (Microsoft sign-in app, ...). */
object Keys {
    private val values = Properties().apply {
        runCatching { Keys::class.java.getResourceAsStream("/dnz-keys.properties")?.use { load(it) } }
    }

    fun get(name: String): String = values.getProperty(name)?.trim().orEmpty()
}
