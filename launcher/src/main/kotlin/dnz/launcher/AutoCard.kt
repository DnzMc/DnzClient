package dnz.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Settings page card with the AUTO button, a short explanation under it, and what it found and changed. */
@Composable
fun AutoCard(state: LauncherState) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.t("auto.title"), color = Dnz.Text, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(state.t("auto.caption"), color = Dnz.Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.width(20.dp))
            DnzButton(if (state.autoBusy) state.t("auto.scanning") else state.t("auto.button")) {
                if (!state.autoBusy) runAuto(state)
            }
        }
        state.autoResult?.let { AutoResult(state, it) }
    }
}

internal fun runAuto(state: LauncherState) {
    state.autoBusy = true
    state.scope.launch {
        try {
            val previousRam = state.ramGb
            val result = withContext(Dispatchers.IO) {
                AutoTune.apply(AutoTune.plan(PcScan.scan()), AutoTune.targets(state.profiles.toList()), previousRam)
            }
            state.ramGb = result.plan.ramGb.toFloat()
            state.autoResult = result
            state.toast = state.t("auto.done_toast").replace("%s", state.t(result.plan.tier.key))
        } catch (e: Exception) {
            state.dialog = state.t("auto.title") to state.t("auto.error").replace("%s", e.message ?: e.toString())
        } finally {
            state.autoBusy = false
        }
    }
}

private fun undoAuto(state: LauncherState, result: AutoTune.Result) {
    state.autoBusy = true
    state.scope.launch {
        try {
            withContext(Dispatchers.IO) { AutoTune.undo(AutoTune.targets(state.profiles.toList()), PcScan.scan().runningGameDirs) }
            state.ramGb = result.previousRamGb
            state.autoResult = null
            state.toast = state.t("auto.undone")
        } finally {
            state.autoBusy = false
        }
    }
}

@Composable
private fun AutoResult(state: LauncherState, result: AutoTune.Result) {
    val plan = result.plan
    val specs = plan.specs
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Dnz.Border))
    Spacer(Modifier.height(16.dp))
    Row {
        // What was found
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(state.t("auto.your_pc"))
            val ram = "${specs.ramGb.roundToInt()} GB" + if (specs.laptop) "  •  " + state.t("auto.laptop") else ""
            Spec(state.t("auto.spec.ram"), ram)
            val cores = state.t("auto.cores").replace("%c", specs.cpuCores.toString()).replace("%t", specs.cpuThreads.toString())
            Spec(state.t("auto.spec.cpu"), (specs.cpuName.ifBlank { state.t("auto.unknown") }) + "  ($cores)")
            if (specs.gpus.isEmpty()) {
                Spec(state.t("auto.spec.gpu"), state.t("auto.unknown"))
            }
            specs.gpus.forEach { gpu ->
                Spec(state.t("auto.spec.gpu"), gpu.name + (gpu.vramGb?.let { "  (${it.roundToInt()} GB)" } ?: ""))
            }
        }
        Spacer(Modifier.width(20.dp))
        // What was changed
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(state.t("auto.changes"))
                Spacer(Modifier.width(10.dp))
                TierChip(state, plan.tier)
            }
            Change(state.t("auto.set.ram").replace("%s", plan.ramGb.toString()))
            Change(state.t("auto.set.render").replace("%s", plan.renderDistance.toString()).replace("%d", plan.simulationDistance.toString()))
            Change(state.t("auto.set.fps"))
            Change(state.t("auto.set.particles").replace("%s", state.t("auto.particles.${plan.particles}")))
            Change(state.t("auto.set.details").replace("%s", state.t(plan.tier.key)))
        }
    }

    if (result.skipped.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Note(state.t("auto.skipped").replace("%s", result.skipped.joinToString(", ")), Color(0xFFFFB74D))
    }
    if (specs.lowPowerMode) {
        Spacer(Modifier.height(12.dp))
        Note(state.t("auto.mac_lowpower"), Color(0xFFFFB74D))
    }
    if (specs.onBattery && specs.laptop) {
        Spacer(Modifier.height(12.dp))
        Note(state.t("auto.battery"), Color(0xFFFFB74D))
    }
    if (specs.hybridGraphics && Platform.isWindows) {
        Spacer(Modifier.height(12.dp))
        Note(state.t("auto.hybrid").replace("%s", specs.gpus.filter { it.dedicated }.joinToString(", ") { it.name }), Dnz.Accent)
        Spacer(Modifier.height(8.dp))
        SmallButton(state.t("auto.open_graphics"), Icons.Filled.Settings) {
            runCatching { ProcessBuilder("explorer.exe", "ms-settings:display-advancedgraphics").start() }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(state.t("auto.applied_to").replace("%s", result.applied.joinToString(", ").ifEmpty { "-" }),
            color = Dnz.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        SmallButton(state.t("auto.undo"), Icons.Filled.Refresh) { if (!state.autoBusy) undoAuto(state, result) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
}

@Composable
private fun Spec(name: String, value: String) {
    Row {
        Text("$name:", color = Dnz.Muted, fontSize = 12.sp, modifier = Modifier.width(92.dp))
        Text(value, color = Dnz.Text, fontSize = 12.sp)
    }
}

@Composable
private fun Change(text: String) {
    Row {
        Text("✓", color = Dnz.Success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Dnz.Text, fontSize = 12.sp)
    }
}

@Composable
private fun TierChip(state: LauncherState, tier: AutoTune.Tier) {
    val color = when (tier) {
        AutoTune.Tier.Low -> Color(0xFFFF9F43)
        AutoTune.Tier.Medium -> Color(0xFFFFD24A)
        AutoTune.Tier.High -> Dnz.Success
        AutoTune.Tier.Ultra -> Dnz.Accent
    }
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(state.t("auto.tier") + ": " + state.t(tier.key), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.10f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Dnz.Text, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
