package dnz.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/** A sign-in in progress: the browser page ([url]), or the backup short code ([code]). */
class MsLogin(val url: String, val code: MicrosoftAuth.DeviceCode? = null)

/** Starts "Sign in with Microsoft": Microsoft's sign-in page opens in the browser, nothing to type in DNZ. */
fun signInWithMicrosoft(state: LauncherState) {
    if (state.msLogin != null) return
    state.scope.launch {
        val browser = runCatching { withContext(Dispatchers.IO) { MicrosoftAuth.startBrowser() } }.getOrElse {
            showError(state, it)
            return@launch
        }
        val login = MsLogin(browser.url)
        state.msLogin = login
        open(browser.url)
        val result = withContext(Dispatchers.IO) { runCatching { browser.await { state.msLogin !== login } } }
        finish(state, login, result)
    }
}

/** Backup: a short code typed on microsoft.com/link (when the browser page does not work). */
private fun signInWithCode(state: LauncherState) {
    state.msLogin = null
    state.scope.launch {
        val code = runCatching { withContext(Dispatchers.IO) { MicrosoftAuth.startDeviceCode() } }.getOrElse {
            showError(state, it)
            return@launch
        }
        val login = MsLogin(code.verificationUri, code)
        state.msLogin = login
        copyAndOpen(code)
        val result = withContext(Dispatchers.IO) { runCatching { MicrosoftAuth.finishDeviceCode(code) { state.msLogin !== login } } }
        finish(state, login, result)
    }
}

private fun finish(state: LauncherState, login: MsLogin, result: Result<Account>) {
    if (state.msLogin !== login) return // closed by the player
    state.msLogin = null
    result.onSuccess { account ->
        state.account = account
        // Signed in with Microsoft: DNZ can start the game itself now.
        state.launchMode = LaunchMode.Direct
        state.toast = state.t("ms.success").replace("%s", account.name)
    }.onFailure { showError(state, it) }
}

private fun showError(state: LauncherState, e: Throwable) {
    val reason = (e as? MicrosoftAuth.AuthException)?.reason ?: MicrosoftAuth.Reason.OTHER
    if (reason == MicrosoftAuth.Reason.CANCELLED) return
    val key = when (reason) {
        MicrosoftAuth.Reason.NOT_APPROVED -> "ms.err.not_approved"
        MicrosoftAuth.Reason.NO_XBOX -> "ms.err.no_xbox"
        MicrosoftAuth.Reason.CHILD -> "ms.err.child"
        MicrosoftAuth.Reason.NO_MINECRAFT -> "ms.err.no_minecraft"
        MicrosoftAuth.Reason.EXPIRED -> "ms.err.expired"
        else -> "ms.err.other"
    }
    state.dialog = state.t("sign_in") to state.t(key).replace("%s", e.message ?: e.toString())
}

private fun open(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

private fun copyAndOpen(code: MicrosoftAuth.DeviceCode) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code.userCode), null) }
    open(code.verificationUri)
}

/** The window shown while the player signs in on Microsoft's page. */
@Composable
fun MicrosoftLoginDialog(state: LauncherState) {
    val login = state.msLogin ?: return
    val code = login.code
    AlertDialog(
        onDismissRequest = {},
        containerColor = Dnz.SurfaceHigh,
        title = { Text(state.t("sign_in"), color = Dnz.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (code != null) {
                    Text(state.t("ms.step1"), color = Dnz.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Dnz.Surface).padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(code.userCode, color = Dnz.Text, fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(state.t("ms.step2"), color = Dnz.Muted, fontSize = 13.sp)
                } else {
                    Text(state.t("ms.browser"), color = Dnz.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Dnz.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(state.t("ms.waiting"), color = Dnz.Muted, fontSize = 12.sp)
                }
                if (code == null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        state.t("ms.use_code"), color = Dnz.Muted, fontSize = 12.sp, textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).dnzClickable { signInWithCode(state) }.padding(4.dp),
                    )
                }
            }
        },
        confirmButton = {
            DnzButton(state.t(if (code != null) "ms.open" else "ms.reopen")) { if (code != null) copyAndOpen(code) else open(login.url) }
        },
        dismissButton = {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).dnzClickable { state.msLogin = null }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(state.t("cancel"), color = Dnz.Muted, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
