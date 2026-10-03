package dnz.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Letter icon colors a profile can pick. */
val ProfileColors = listOf(
    listOf(Color(0xFF4FA3FF), Color(0xFF7B5CFF)), listOf(Color(0xFF3ED6D0), Color(0xFF2A7FFF)),
    listOf(Color(0xFF7CE38B), Color(0xFF1FA35C)), listOf(Color(0xFFFFB347), Color(0xFFFF5E62)),
    listOf(Color(0xFFFF6B9D), Color(0xFFB344FF)), listOf(Color(0xFF8C95AB), Color(0xFF3A4152)),
)

/** Profile folders, pictures, copying and deleting. */
object Profiles {
    private val iconDir = File(GameLauncher.root, "icons").apply { mkdirs() }
    private val images = HashMap<String, ImageBitmap?>()

    fun image(profile: Profile): ImageBitmap? {
        val name = profile.icon ?: return null
        return synchronized(images) {
            images.getOrPut(name) { runCatching { ImageIO.read(File(iconDir, name)).toComposeImageBitmap() }.getOrNull() }
        }
    }

    /** Asks for a picture (Windows file window), crops it square, saves it 128 x 128. Returns its file name. */
    fun pickPicture(folder: String): String? {
        val dialog = FileDialog(null as Frame?, "Profil resmi", FileDialog.LOAD)
        dialog.setFilenameFilter { _, n -> n.lowercase().let { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") } }
        dialog.isVisible = true
        val chosen = dialog.file?.let { File(dialog.directory, it) } ?: return null
        val src = ImageIO.read(chosen) ?: return null
        val side = minOf(src.width, src.height)
        val out = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(src, 0, 0, 128, 128, (src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2, null)
            dispose()
        }
        val name = "$folder-${System.currentTimeMillis()}.png"
        ImageIO.write(out, "png", File(iconDir, name))
        return name
    }

    /** A folder name that no profile uses yet. */
    fun freeFolder(name: String, taken: Collection<String>): String {
        val base = name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "Profil" }
        var folder = base
        var n = 2
        while (folder in taken || File(GameLauncher.root, "instances/$folder").exists()) folder = "${base}_${n++}"
        return folder
    }

    /** Copies mods, settings, servers and packs (not worlds) to a new folder. */
    fun copyFiles(from: Profile, to: Profile) {
        val src = GameLauncher.instanceDir(from)
        val dst = GameLauncher.instanceDir(to).apply { mkdirs() }
        for (name in listOf("mods", "config", "resourcepacks", "shaderpacks", "options.txt", "servers.dat")) {
            val f = File(src, name)
            if (f.exists()) f.copyRecursively(File(dst, name), overwrite = true)
        }
    }

    /** Moves the profile's folder to the Recycle Bin (never deleted for good). */
    fun trashFiles(profile: Profile): Boolean {
        val dir = GameLauncher.instanceDir(profile)
        if (!dir.exists()) return true
        return runCatching { Desktop.getDesktop().moveToTrash(dir) }.getOrDefault(false)
    }

    fun openFolder(profile: Profile) {
        val dir = GameLauncher.instanceDir(profile).apply { mkdirs() }
        runCatching { Desktop.getDesktop().open(dir) }
    }

    fun modCount(profile: Profile): Int =
        File(GameLauncher.instanceDir(profile), "mods").listFiles { f -> f.name.endsWith(".jar") }?.size ?: 0
}

@Composable
fun ProfileIcon(profile: Profile, size: Dp) {
    val image = remember(profile.icon) { Profiles.image(profile) }
    if (image != null) {
        Image(image, null, Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else {
        val colors = ProfileColors[(profile.color ?: 0).coerceIn(0, ProfileColors.size - 1)]
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
            Text(profile.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
fun ProfilesScreen(state: LauncherState) {
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(state.t("profiles"))
            Spacer(Modifier.weight(1f))
            DnzButton("+ " + state.t("profiles.new")) { state.editingProfile = -1 }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in state.profiles.indices) ProfileCard(state, i)
        }
        Spacer(Modifier.height(18.dp))
        ImportCard(state)
    }
    state.editingProfile?.let { ProfileEditor(state, it) }
    state.deletingProfile?.let { DeleteProfileDialog(state, it) }
}

@Composable
private fun ProfileCard(state: LauncherState, i: Int) {
    val p = state.profiles[i]
    val selected = i == state.selectedProfile
    val mods = remember(p.folder, state.screen) { Profiles.modCount(p) }
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dnz.Surface)
            .border(1.dp, if (selected) Dnz.Accent else Dnz.Border, RoundedCornerShape(14.dp))
            .dnzClickable { state.selectedProfile = i; state.version = p.version }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileIcon(p, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = Dnz.Text, fontWeight = FontWeight.Bold)
            Text(p.description, color = Dnz.Muted, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("Minecraft ${p.version}")
                Tag(state.t("profiles.mods").replace("%s", mods.toString()))
                Tag("RAM " + (p.ramGb?.let { "$it GB" } ?: state.t("profiles.ram_default")))
            }
        }
        if (selected) Text(state.t("profiles.selected"), color = Dnz.Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        IconAction(Icons.Filled.Edit) { state.editingProfile = i }
        Box {
            IconAction(Icons.Filled.MoreVert) { menu = true }
            DnzDropdown(menu, onDismiss = { menu = false }) {
                DropdownMenuItem(modifier = Modifier.menuItem(), text = { Text(state.t("profiles.duplicate"), color = Dnz.Text) }, onClick = {
                    menu = false
                    duplicate(state, i)
                })
                DropdownMenuItem(modifier = Modifier.menuItem(), text = { Text(state.t("lib.open_folder"), color = Dnz.Text) }, onClick = {
                    menu = false
                    Profiles.openFolder(p)
                })
                DropdownMenuItem(modifier = Modifier.menuItem(), text = { Text(state.t("profiles.delete"), color = Dnz.Danger) }, onClick = {
                    menu = false
                    if (state.profiles.size <= 1) state.toast = state.t("profiles.last") else state.deletingProfile = i
                })
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Dnz.SurfaceHigh).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = Dnz.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).dnzClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Dnz.Muted, modifier = Modifier.size(18.dp))
    }
}

private fun duplicate(state: LauncherState, i: Int) {
    val p = state.profiles[i]
    val name = p.name + " " + state.t("profiles.copy_suffix")
    val copy = p.copy(name = name, dir = Profiles.freeFolder(name, state.profiles.map { it.folder }))
    state.scope.launch {
        withContext(Dispatchers.IO) { runCatching { Profiles.copyFiles(p, copy) } }
        state.profiles.add(copy)
        state.toast = state.t("profiles.duplicated").replace("%s", name)
    }
}

/** Window for a new profile (index -1) or for editing one: name, description, version, picture/color, RAM. */
@Composable
private fun ProfileEditor(state: LauncherState, index: Int) {
    val old = state.profiles.getOrNull(index)
    var name by remember { mutableStateOf(old?.name ?: "Profil ${state.profiles.size + 1}") }
    var description by remember { mutableStateOf(old?.description ?: "DNZ Client") }
    var version by remember { mutableStateOf(old?.version ?: state.version) }
    var color by remember { mutableStateOf(old?.color ?: (state.profiles.size % ProfileColors.size)) }
    var icon by remember { mutableStateOf(old?.icon) }
    var ram by remember { mutableStateOf(old?.ramGb) }
    val folder = remember { old?.folder ?: Profiles.freeFolder(name, state.profiles.map { it.folder }) }
    val preview = Profile(name.ifBlank { "?" }, version, description, folder, color, icon, ram)

    AlertDialog(
        onDismissRequest = { state.editingProfile = null },
        containerColor = Dnz.SurfaceHigh,
        title = { Text(state.t(if (old == null) "profiles.new" else "profiles.edit"), color = Dnz.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(460.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileIcon(preview, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProfileColors.forEachIndexed { c, colors ->
                                Box(
                                    Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(colors))
                                        .border(2.dp, if (icon == null && c == color) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                                        .dnzClickable { color = c; icon = null },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton(state.t("profiles.picture")) {
                                state.scope.launch {
                                    withContext(Dispatchers.IO) { runCatching { Profiles.pickPicture(folder) }.getOrNull() }?.let { icon = it }
                                }
                            }
                            if (icon != null) SmallButton(state.t("profiles.picture_remove")) { icon = null }
                        }
                    }
                }
                EditField(state.t("profiles.name"), name) { name = it }
                EditField(state.t("profiles.description"), description) { description = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.t("version"), color = Dnz.Muted, fontSize = 12.sp, modifier = Modifier.width(70.dp))
                    state.versions.forEach { v ->
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(if (v == version) Dnz.Accent else Dnz.Surface)
                                .dnzClickable { version = v }.padding(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text(v, color = if (v == version) Dnz.OnAccent else Dnz.Text, fontSize = 12.sp) }
                    }
                }
                if (old != null && version != old.version) Text(state.t("profiles.version_warn"), color = Color(0xFFFFB74D), fontSize = 11.sp)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.t("settings.ram"), color = Dnz.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(ram?.let { "$it GB" } ?: state.t("profiles.ram_default") + " (${state.ramGb.toInt()} GB)", color = Dnz.Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Slider(
                        (ram ?: 1).toFloat(), { v -> ram = v.toInt().takeIf { it >= 2 } }, valueRange = 1f..16f, steps = 14,
                        modifier = Modifier.handCursor(),
                        colors = SliderDefaults.colors(thumbColor = Dnz.Accent, activeTrackColor = Dnz.Accent, inactiveTrackColor = Dnz.Surface),
                    )
                    Text(state.t("profiles.ram_hint"), color = Dnz.Muted, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            DnzButton(state.t("servers.save")) {
                val profile = Profile(name.trim().ifBlank { "Profil" }, version, description.trim(), folder, color, icon, ram)
                if (old == null) {
                    state.profiles.add(profile)
                    state.selectedProfile = state.profiles.size - 1
                } else {
                    state.profiles[index] = profile
                }
                if (index == state.selectedProfile || old == null) state.version = profile.version
                state.editingProfile = null
            }
        },
        dismissButton = {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).dnzClickable { state.editingProfile = null }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(state.t("cancel"), color = Dnz.Muted, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun EditField(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Dnz.Muted, fontSize = 12.sp, modifier = Modifier.width(70.dp))
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = Dnz.Text, fontSize = 13.sp),
                cursorBrush = SolidColor(Dnz.Accent), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Delete: removes the profile from the list; its files go to the Recycle Bin only if asked. */
@Composable
private fun DeleteProfileDialog(state: LauncherState, index: Int) {
    val p = state.profiles.getOrNull(index) ?: run { state.deletingProfile = null; return }
    var files by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { state.deletingProfile = null },
        containerColor = Dnz.SurfaceHigh,
        title = { Text(state.t("profiles.delete_title").replace("%s", p.name), color = Dnz.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(state.t("profiles.delete_body"), color = Dnz.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.dnzClickable(highlight = false) { files = !files }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(files, { files = it }, modifier = Modifier.handCursor(),
                        colors = CheckboxDefaults.colors(checkedColor = Dnz.Danger, uncheckedColor = Dnz.Muted, checkmarkColor = Color.White))
                    Text(state.t("profiles.delete_files"), color = Dnz.Text, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Dnz.Danger).dnzClickable {
                state.deletingProfile = null
                if (state.gameRunning && index == state.selectedProfile) {
                    state.toast = state.t("profiles.delete_running")
                    return@dnzClickable
                }
                state.profiles.removeAt(index)
                state.selectedProfile = state.selectedProfile.let { if (it >= index && it > 0) it - 1 else it }.coerceIn(0, state.profiles.size - 1)
                state.version = state.profiles[state.selectedProfile].version
                state.scope.launch {
                    val ok = !files || withContext(Dispatchers.IO) { Profiles.trashFiles(p) }
                    state.toast = state.t(if (ok) "profiles.deleted" else "profiles.trash_failed").replace("%s", p.name)
                }
            }.padding(horizontal = 18.dp, vertical = 10.dp)) { Text(state.t("profiles.delete"), color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).dnzClickable { state.deletingProfile = null }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(state.t("cancel"), color = Dnz.Muted, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
