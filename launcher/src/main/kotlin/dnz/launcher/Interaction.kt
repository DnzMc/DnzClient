package dnz.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp

/**
 * Everything clickable in the launcher: hand cursor over it, and it lights up a little while the mouse is on it.
 * [highlight] = false for elements that already change color on hover themselves.
 */
fun Modifier.dnzClickable(enabled: Boolean = true, highlight: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    this.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .hoverable(hover, enabled)
        .then(
            if (highlight && enabled && hovered) Modifier.drawWithContent {
                drawContent()
                drawRect(Color.White.copy(alpha = 0.08f))
            } else Modifier,
        )
        .clickable(enabled = enabled, onClick = onClick)
}

/** Hand cursor for Material controls (switches, sliders, checkboxes, menu items). */
fun Modifier.handCursor(): Modifier = this.pointerHoverIcon(PointerIcon.Hand)

/** A row in a [DnzDropdown]: hand cursor and a rounded highlight inset from the menu's edges. */
fun Modifier.menuItem(): Modifier = this.handCursor().padding(horizontal = 6.dp).clip(RoundedCornerShape(8.dp))

/** Drop-down menu in the launcher style: rounded corners, thin border, soft shadow. */
@Composable
fun DnzDropdown(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        containerColor = Dnz.SurfaceHigh,
        border = BorderStroke(1.dp, Dnz.Border),
        shadowElevation = 16.dp,
        content = content,
    )
}
