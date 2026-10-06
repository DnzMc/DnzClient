package dnz.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Launcher color themes, chosen on the Settings page. */
enum class LauncherTheme(val key: String) {
    /** Near-black base with an orange accent. */
    Colorful("theme.color"),
    /** Black and white only (errors stay red). */
    Mono("theme.mono"),
}

/**
 * A DNZ Workshop theme: the launcher's 8 main colors, made with the Theme Maker of DNZ Workshop Uploader.
 * File: {"dnzLauncherTheme": 1, "name": "...", "colors": {"background": "#0A0D12", ...}} (only colors, no code).
 */
data class ThemeColors(
    val name: String,
    val background: Long,
    val sidebar: Long,
    val surface: Long,
    val surfaceHigh: Long,
    val accent: Long,
    val accent2: Long,
    val text: Long,
    val muted: Long,
) {
    fun values() = listOf(background, sidebar, surface, surfaceHigh, accent, accent2, text, muted)

    fun toJson(): String {
        val root = JsonObject()
        root.addProperty("dnzLauncherTheme", 1)
        root.addProperty("name", name)
        root.add("colors", JsonObject().apply { KEYS.zip(values()).forEach { (k, v) -> addProperty(k, hex(v)) } })
        return GsonBuilder().setPrettyPrinting().create().toJson(root)
    }

    fun with(key: String, value: Long) = when (key) {
        "background" -> copy(background = value)
        "sidebar" -> copy(sidebar = value)
        "surface" -> copy(surface = value)
        "surfaceHigh" -> copy(surfaceHigh = value)
        "accent" -> copy(accent = value)
        "accent2" -> copy(accent2 = value)
        "text" -> copy(text = value)
        else -> copy(muted = value)
    }

    companion object {
        val KEYS = listOf("background", "sidebar", "surface", "surfaceHigh", "accent", "accent2", "text", "muted")

        /** The launcher's own colors, the starting point of the Theme Maker. */
        val DEFAULT = ThemeColors("DNZ", 0xFF0A0D12, 0xFF0A0D12, 0xFF151B25, 0xFF1C2330, 0xFFFF8A3D, 0xFFFF6A1A, 0xFFF2F4FA, 0xFF8C95AB)

        fun hex(c: Long) = "#%06X".format(c and 0xFFFFFF)

        /** "#RRGGBB" to an opaque color, or null. */
        fun parseHex(text: String): Long? =
            Regex("^#?([0-9a-fA-F]{6})$").find(text.trim())?.groupValues?.get(1)?.toLong(16)?.let { it or 0xFF000000 }

        /** Reads a theme file; null unless it has a name and all 8 colors. */
        fun fromJson(text: String): ThemeColors? = runCatching {
            val o = JsonParser.parseString(text).asJsonObject
            val colors = o["colors"].asJsonObject
            val v = KEYS.map { parseHex(colors[it].asString)!! }
            ThemeColors(o["name"].asString.take(40), v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7])
        }.getOrNull()
    }
}

/** DNZ colors of the current theme. Reading them in a composable redraws it when the theme changes. */
object Dnz {
    var theme by mutableStateOf(LauncherTheme.Colorful)
    /** A theme from DNZ Workshop; while set it wins over [theme]. */
    var custom by mutableStateOf<ThemeColors?>(null)
    private val mono get() = custom == null && theme == LauncherTheme.Mono

    private fun pick(color: Long, mono: Long) = Color(if (this.mono) mono else color)
    private inline fun pick(color: Long, mono: Long, fromCustom: (ThemeColors) -> Long) = custom?.let { Color(fromCustom(it)) } ?: pick(color, mono)

    val Background get() = pick(0xFF0A0D12, 0xFF050505) { it.background }
    val Sidebar get() = pick(0xFF0A0D12, 0xFF0B0B0B) { it.sidebar }
    val Surface get() = pick(0xFF151B25, 0xFF141414) { it.surface }
    val SurfaceHigh get() = pick(0xFF1C2330, 0xFF1F1F1F) { it.surfaceHigh }
    val Border get() = pick(0x22FFFFFF, 0x26FFFFFF) { 0x22000000L or (it.text and 0xFFFFFF) }
    val Accent get() = pick(0xFFFF8A3D, 0xFFF2F2F2) { it.accent }
    val Accent2 get() = pick(0xFFFF6A1A, 0xFFB8B8B8) { it.accent2 }
    val Accent3 get() = pick(0xFF3ED6D0, 0xFF7A7A7A)
    /** Text and icons drawn on top of the accent color (dark on a light accent, white on a dark one). */
    val OnAccent get() = pick(0xFF0A0D12, 0xFF000000) { if (luminance(it.accent) > 0.55f) 0xFF0A0D12 else 0xFFFFFFFF }
    val Text get() = pick(0xFFF2F4FA, 0xFFF5F5F5) { it.text }
    val Muted get() = pick(0xFF8C95AB, 0xFF9A9A9A) { it.muted }
    val Success get() = pick(0xFF7CE38B, 0xFFFFFFFF)
    val Danger get() = pick(0xFFFF6B6B, 0xFFFF6B6B)

    /** Makes the DNZ logo gray in the black & white theme. */
    val LogoFilter: ColorFilter? get() = if (mono) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null

    val AccentGradient get() = Brush.horizontalGradient(listOf(Accent, Accent2))
    val HeroGradient
        get() = Brush.linearGradient(
            custom?.let { listOf(mix(it.background, it.accent, 0.45f), mix(it.background, it.accent2, 0.3f), Color(it.background)) }
                ?: if (mono) listOf(Color(0xFF2E2E2E), Color(0xFF161616), Color(0xFF050505))
                else listOf(Color(0xFF1B3F8F), Color(0xFF3A2A8C), Color(0xFF5B1F7A)),
        )

    private fun luminance(c: Long) = ((c shr 16 and 0xFF) * 0.299f + (c shr 8 and 0xFF) * 0.587f + (c and 0xFF) * 0.114f) / 255f

    private fun mix(a: Long, b: Long, t: Float): Color {
        fun ch(shift: Int) = ((a shr shift and 0xFF) * (1 - t) + (b shr shift and 0xFF) * t) / 255f
        return Color(ch(16), ch(8), ch(0))
    }
}
