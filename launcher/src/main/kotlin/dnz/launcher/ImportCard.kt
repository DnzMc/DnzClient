package dnz.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the import card shows; kept in LauncherState so it survives switching pages. */
class ImportState {
    var scanning by mutableStateOf(false)
    /** Null = not scanned yet. */
    var sources by mutableStateOf<List<Importer.Source>?>(null)
    var selected by mutableStateOf<Importer.Source?>(null)
    var targetProfile by mutableStateOf(0)
    var parts by mutableStateOf(Importer.Parts())
    var busy by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var result by mutableStateOf<Importer.Result?>(null)
}

/** Profiles page card: finds other Minecraft installs and brings their settings, mods and servers into a DNZ profile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportCard(state: LauncherState) {
    val im = state.imports
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.t("import.title"), color = Dnz.Text, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(state.t("import.caption"), color = Dnz.Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.width(20.dp))
            if (im.sources == null) {
                DnzButton(if (im.scanning) state.t("import.scanning") else state.t("import.scan")) { scan(state) }
            } else {
                SmallButton(if (im.scanning) state.t("import.scanning") else state.t("import.rescan"), Icons.Filled.Refresh) { scan(state) }
            }
        }
        val sources = im.sources ?: return@Card
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Dnz.Border))
        Spacer(Modifier.height(14.dp))
        if (sources.isEmpty()) {
            Text(state.t("import.none"), color = Dnz.Muted, fontSize = 12.sp)
            return@Card
        }
        Text(state.t("import.found").replace("%s", sources.size.toString()), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sources.forEach { SourceRow(state, it) }
        }

        val source = im.selected ?: return@Card
        Spacer(Modifier.height(16.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text(state.t("import.target"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.profiles.forEachIndexed { i, p ->
                        val on = i == im.targetProfile
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) Dnz.Accent else Dnz.SurfaceHigh)
                                .dnzClickable { im.targetProfile = i }.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("${p.name}  •  ${p.version}", color = if (on) Dnz.OnAccent else Dnz.Text, fontSize = 12.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(state.t("import.what"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                val p = im.parts
                val game = source.gameDir != null
                if (game) PartBox(state.t("part.options"), p.options) { im.parts = p.copy(options = it) }
                if (source.modCount > 0) PartBox(state.t("part.mods"), p.mods) { im.parts = p.copy(mods = it) }
                if (game && source.serverCount > 0) PartBox(state.t("part.servers"), p.servers) { im.parts = p.copy(servers = it) }
                if (game && source.packCount > 0) PartBox(state.t("part.packs"), p.packs) { im.parts = p.copy(packs = it) }
                if (game) PartBox(state.t("part.configs"), p.configs) { im.parts = p.copy(configs = it) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(im.status.orEmpty(), color = Dnz.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            DnzButton(if (im.busy) state.t("import.working") else state.t("import.button")) { runImport(state, source) }
        }
        im.result?.let { ImportResult(state, it) }
    }
}

@Composable
private fun SourceRow(state: LauncherState, source: Importer.Source) {
    val im = state.imports
    val on = im.selected == source
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Dnz.SurfaceHigh)
            .border(1.dp, if (on) Dnz.Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .dnzClickable { select(im, source) }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(on, onClick = { select(im, source) }, modifier = Modifier.handCursor(), colors = RadioButtonDefaults.colors(selectedColor = Dnz.Accent))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(source.dir.name, color = Dnz.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(source.dir.absolutePath, color = Dnz.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            source.mcVersion?.let { Chip(state.t("import.version").replace("%s", it)) }
            if (source.gameDir == null) Chip(state.t("import.mods_only"))
            if (source.hasOptions) Chip(state.t("import.settings"))
            if (source.modCount > 0) Chip(state.t("import.mods").replace("%s", source.modCount.toString()))
            if (source.serverCount > 0) Chip(state.t("import.servers").replace("%s", source.serverCount.toString()))
            if (source.packCount > 0) Chip(state.t("import.packs").replace("%s", source.packCount.toString()))
        }
    }
}

private fun select(im: ImportState, source: Importer.Source) {
    if (im.busy) return
    im.selected = source
    im.result = null
    im.status = null
}

@Composable
private fun Chip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Dnz.Accent.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = Dnz.Text, fontSize = 11.sp)
    }
}

@Composable
private fun PartBox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().dnzClickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = onChange, modifier = Modifier.handCursor(), colors = CheckboxDefaults.colors(checkedColor = Dnz.Accent, uncheckedColor = Dnz.Muted, checkmarkColor = Dnz.OnAccent))
        Text(label, color = Dnz.Text, fontSize = 12.sp)
    }
}

@Composable
private fun ImportResult(state: LauncherState, r: Importer.Result) {
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Dnz.Border))
    Spacer(Modifier.height(12.dp))
    Text(state.t("import.r.title"), color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    val good = buildList {
        if (r.options) add(state.t("import.r.options"))
        if (r.modsInstalled.isNotEmpty()) add(state.t("import.r.mods").replace("%s", r.modsInstalled.size.toString()) + ": " + r.modsInstalled.joinToString(", "))
        if (r.modsAlready > 0) add(state.t("import.r.already").replace("%s", r.modsAlready.toString()))
        if (r.serversAdded > 0) add(state.t("import.r.servers").replace("%s", r.serversAdded.toString()))
        if (r.packsCopied > 0) add(state.t("import.r.packs").replace("%s", r.packsCopied.toString()))
        if (r.configsCopied > 0) add(state.t("import.r.configs").replace("%s", r.configsCopied.toString()))
    }
    val bad = buildList {
        if (r.modsNoVersion.isNotEmpty()) {
            val mc = state.profiles.getOrNull(state.imports.targetProfile)?.version.orEmpty()
            add(state.t("import.r.no_version").replace("%v", mc).replace("%s", r.modsNoVersion.joinToString(", ")))
        }
        if (r.modsUnknown.isNotEmpty()) add(state.t("import.r.unknown").replace("%s", r.modsUnknown.joinToString(", ")))
        if (r.errors.isNotEmpty()) add(state.t("import.r.errors").replace("%s", r.errors.joinToString("; ")))
    }
    if (good.isEmpty() && bad.isEmpty()) Text(state.t("import.r.nothing"), color = Dnz.Muted, fontSize = 12.sp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        good.forEach { ResultLine("✓", Dnz.Success, it) }
        bad.forEach { ResultLine("!", Color(0xFFFFB74D), it) }
    }
}

@Composable
private fun ResultLine(mark: String, color: Color, text: String) {
    Row {
        Text(mark, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
        Text(text, color = Dnz.Text, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

private fun scan(state: LauncherState) {
    val im = state.imports
    if (im.scanning || im.busy) return
    im.scanning = true
    state.scope.launch {
        try {
            val found = withContext(Dispatchers.IO) { Importer.find() }
            im.sources = found
            im.selected = found.firstOrNull { it.dir.absolutePath == im.selected?.dir?.absolutePath } ?: found.firstOrNull()
            im.result = null
            im.status = null
            im.targetProfile = state.selectedProfile
        } finally {
            im.scanning = false
        }
    }
}

private fun runImport(state: LauncherState, source: Importer.Source) {
    val im = state.imports
    if (im.busy) return
    val profile = state.profiles.getOrNull(im.targetProfile) ?: return
    im.busy = true
    im.result = null
    state.scope.launch {
        try {
            if (withContext(Dispatchers.IO) { Importer.isRunning(profile) }) {
                state.dialog = state.t("import.title") to state.t("import.running").replace("%s", profile.name)
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                Importer.import(source, profile, im.parts) { text -> state.scope.launch { im.status = text } }
            }
            im.result = result
            state.toast = state.t("import.done_toast").replace("%s", profile.name)
        } catch (e: Exception) {
            state.dialog = state.t("import.title") to (e.message ?: e.toString())
        } finally {
            im.busy = false
            im.status = null
        }
    }
}
