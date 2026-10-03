package dnz.launcher

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** "--background": started with Windows, stays hidden until the player opens it. */
fun main(args: Array<String>) {
    if (!SingleInstance.claim()) return // another DNZ Launcher runs and shows its window
    val background = "--background" in args
    application {
        val windowState = rememberWindowState(width = 1200.dp, height = 740.dp, position = WindowPosition(Alignment.Center))
        val state = remember { LauncherState() }
        var visible by remember { mutableStateOf(!background) }
        SingleInstance.onShow = {
            visible = true
            windowState.isMinimized = false
        }
        // macOS: clicking the Dock icon of the hidden launcher opens it again.
        if (Platform.isMac) {
            LaunchedEffect(Unit) {
                runCatching {
                    java.awt.Desktop.getDesktop().addAppEventListener(java.awt.desktop.AppReopenedListener {
                        javax.swing.SwingUtilities.invokeLater { SingleInstance.onShow() }
                    })
                }
            }
        }
        // With "start with Windows" on, closing only hides the window, so the next open is instant.
        val close = { if (state.startWithWindows) visible = false else exitApplication() }

        if (state.startWithWindows || !visible) {
            Tray(
                icon = BitmapPainter(DnzLogo),
                tooltip = "DNZ Launcher",
                onAction = { visible = true },
                menu = {
                    Item(state.t("tray.open"), onClick = { visible = true; windowState.isMinimized = false })
                    Item(state.t("tray.quit"), onClick = ::exitApplication)
                },
            )
        }
        Window(
            onCloseRequest = close,
            state = windowState,
            visible = visible,
            title = "DNZ Launcher",
            undecorated = true,
            icon = BitmapPainter(DnzLogo),
        ) {
            LaunchedEffect(visible) {
                if (visible) window.toFront()
            }
            LauncherApp(
                state = state,
                dragArea = { content -> WindowDraggableArea { content() } },
                onMinimize = { windowState.isMinimized = true },
                onClose = close,
            )
        }
    }
}
