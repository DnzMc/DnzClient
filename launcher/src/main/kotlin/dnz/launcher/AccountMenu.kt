package dnz.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Top-right account button: skin face and name of the active account. Clicking it lists the other
 * accounts to switch to, plus adding an account and the account page.
 */
@Composable
fun AccountMenu(state: LauncherState) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { state.refreshAccounts() }
    val current = state.currentAccount
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()

    Box {
        Row(
            Modifier.height(28.dp).clip(RoundedCornerShape(8.dp))
                .background(if (hovered || open) Dnz.SurfaceHigh else Color.Transparent)
                .hoverable(hover).dnzClickable(highlight = false) {
                    open = !open
                    if (open) state.scope.launch { state.refreshAccounts() }
                }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkinHead(current, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(current?.name ?: state.t("not_signed"), color = Dnz.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Filled.ArrowDropDown, null, tint = Dnz.Muted, modifier = Modifier.size(18.dp))
        }
        DnzDropdown(open, onDismiss = { open = false }) {
            Text(
                state.t(if (state.launchMode == LaunchMode.Official) "acc.official_title" else "acc.dnz_title"),
                color = Dnz.Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            for (account in state.switchableAccounts) {
                val active = account.uuid == current?.uuid
                DropdownMenuItem(
                    modifier = Modifier.menuItem(),
                    leadingIcon = { SkinHead(account, 24.dp) },
                    text = { Text(account.name, color = Dnz.Text, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) },
                    trailingIcon = { if (active) Icon(Icons.Filled.Check, null, tint = Dnz.Accent, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        open = false
                        if (!active) state.scope.launch { state.switchAccount(account) }
                    },
                )
            }
            if (state.switchableAccounts.isEmpty()) {
                Text(state.t("acc.none"), color = Dnz.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
            HorizontalDivider(color = Dnz.Border, modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                    modifier = Modifier.menuItem(),
                leadingIcon = { Icon(Icons.Filled.Add, null, tint = Dnz.Accent) },
                text = { Text(state.t("acc.add"), color = Dnz.Text) },
                onClick = {
                    open = false
                    if (state.launchMode == LaunchMode.Official) {
                        if (Accounts.openOfficialLauncher()) state.toast = state.t("acc.add_hint")
                    } else {
                        state.screen = Screen.Account
                    }
                },
            )
            DropdownMenuItem(
                    modifier = Modifier.menuItem(),
                leadingIcon = { Icon(Icons.Filled.Person, null, tint = Dnz.Muted) },
                text = { Text(state.t("acc.page"), color = Dnz.Text) },
                onClick = {
                    open = false
                    state.screen = Screen.Account
                },
            )
        }
    }
}

/** The account's skin face in crisp pixels; the first letter while it loads (or for test accounts). */
@Composable
fun SkinHead(account: McAccount?, size: Dp) {
    val uuid = account?.uuid
    val head by produceState(uuid?.let { Accounts.cachedHead(it) }, uuid) {
        if (value == null && uuid != null) value = withContext(Dispatchers.IO) { Accounts.head(uuid) }
    }
    val image = head
    if (image != null) {
        Image(image, null, Modifier.size(size).clip(RoundedCornerShape(3.dp)), filterQuality = FilterQuality.None)
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(3.dp)).background(Dnz.AccentGradient), contentAlignment = Alignment.Center) {
            Text((account?.name ?: "?").take(1).uppercase(), color = Dnz.OnAccent, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Black)
        }
    }
}
