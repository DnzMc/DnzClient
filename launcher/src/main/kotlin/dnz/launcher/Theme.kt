package dnz.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/** Launcher color themes, chosen on the Settings page. */
enum class LauncherTheme(val key: String) {
    /** Dark navy base with a blue -> purple accent. */
    Colorful("theme.color"),
    /** Black and white only (errors stay red). */
    Mono("theme.mono"),
}

/** DNZ colors of the current theme. Reading them in a composable redraws it when the theme changes. */
object Dnz {
    var theme by mutableStateOf(LauncherTheme.Colorful)
    private val mono get() = theme == LauncherTheme.Mono

    private fun pick(color: Long, mono: Long) = Color(if (this.mono) mono else color)

    val Background get() = pick(0xFF0A0D16, 0xFF050505)
    val Sidebar get() = pick(0xFF0D111C, 0xFF0B0B0B)
    val Surface get() = pick(0xFF131826, 0xFF141414)
    val SurfaceHigh get() = pick(0xFF1A2133, 0xFF1F1F1F)
    val Border get() = pick(0x22FFFFFF, 0x26FFFFFF)
    val Accent get() = pick(0xFF4FA3FF, 0xFFF2F2F2)
    val Accent2 get() = pick(0xFF7B5CFF, 0xFFB8B8B8)
    val Accent3 get() = pick(0xFF3ED6D0, 0xFF7A7A7A)
    /** Text and icons drawn on top of the accent color. */
    val OnAccent get() = pick(0xFFFFFFFF, 0xFF000000)
    val Text get() = pick(0xFFF2F4FA, 0xFFF5F5F5)
    val Muted get() = pick(0xFF8C95AB, 0xFF9A9A9A)
    val Success get() = pick(0xFF7CE38B, 0xFFFFFFFF)
    val Danger get() = pick(0xFFFF6B6B, 0xFFFF6B6B)

    /** Makes the DNZ logo gray in the black & white theme. */
    val LogoFilter: ColorFilter? get() = if (mono) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null

    val AccentGradient get() = Brush.horizontalGradient(listOf(Accent, Accent2))
    val HeroGradient
        get() = Brush.linearGradient(
            if (mono) listOf(Color(0xFF2E2E2E), Color(0xFF161616), Color(0xFF050505))
            else listOf(Color(0xFF1B3F8F), Color(0xFF3A2A8C), Color(0xFF5B1F7A)),
        )
}
