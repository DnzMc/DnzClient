package dnz.launcher

import java.util.Properties

/**
 * Private IDs and keys (Microsoft sign-in app, ...). They are never in the source code: they come from
 * src/main/resources/dnz-keys.properties, which is not in git (see dnz-keys.properties.example).
 * A build without the file still works, only the features that need a key stay off.
 */
object Keys {
    private val values = Properties().apply {
        runCatching { Keys::class.java.getResourceAsStream("/dnz-keys.properties")?.use { load(it) } }
    }

    fun get(name: String): String = values.getProperty(name)?.trim().orEmpty()
}
