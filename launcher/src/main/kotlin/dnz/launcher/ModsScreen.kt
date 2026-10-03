package dnz.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI

enum class LibraryTab { Discover, Installed }

/** Mod library state; kept in LauncherState so results stay when the player switches pages. */
class LibraryState {
    var tab by mutableStateOf(LibraryTab.Discover)
    var query by mutableStateOf("")
    var sort by mutableStateOf(Modrinth.Sort.Relevance)
    var category by mutableStateOf<String?>(null)
    val hits = mutableStateListOf<Modrinth.Hit>()
    var total by mutableStateOf(0)
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf(false)
    /** Search that [hits] belong to, so coming back to the page doesn't search again. */
    var searchedFor by mutableStateOf<String?>(null)

    /** Mod page being shown (project id), or null for the lists. */
    var openProject by mutableStateOf<String?>(null)

    val installed = mutableStateListOf<Library.Installed>()
    var scanning by mutableStateOf(false)
    var scannedFor by mutableStateOf<String?>(null)

    /** Project id -> progress text while it installs. */
    val busy = mutableStateMapOf<String, String>()
}

// ------------------------------------------------------------------ screen

@Composable
fun ModsScreen(state: LauncherState) {
    val lib = state.library
    val profile = state.profiles[state.selectedProfile]
    val mc = profile.version

    // Search again when the text (after a short pause), sort, category or Minecraft version changes.
    val key = searchKey(lib, mc)
    LaunchedEffect(key) {
        if (lib.searchedFor != key) {
            delay(350)
            search(lib, mc, reset = true)
        }
    }
    LaunchedEffect(profile.name, mc) {
        if (lib.scannedFor != scanKey(profile)) rescan(lib, profile)
    }

    val open = lib.openProject
    if (open != null) {
        ModPage(state, open)
        return
    }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            ScreenTitle(state.t("mods"))
            Spacer(Modifier.weight(1f))
            ProfileMenu(state)
        }
        Segmented(
            listOf(
                state.t("lib.discover"),
                state.t("lib.installed_tab") + if (lib.installed.isNotEmpty()) " (${lib.installed.size})" else "",
            ),
            lib.tab.ordinal,
        ) { lib.tab = LibraryTab.entries[it] }
        Spacer(Modifier.height(14.dp))
        when (lib.tab) {
            LibraryTab.Discover -> DiscoverView(state, mc)
            LibraryTab.Installed -> InstalledView(state, profile)
        }
    }
}

private fun searchKey(lib: LibraryState, mc: String) = "${lib.query.trim()}|${lib.sort}|${lib.category}|$mc"
private fun scanKey(profile: Profile) = "${profile.name}|${profile.version}"

private suspend fun search(lib: LibraryState, mc: String, reset: Boolean) {
    val key = searchKey(lib, mc)
    lib.searching = true
    lib.searchError = false
    try {
        val offset = if (reset) 0 else lib.hits.size
        val page = withContext(Dispatchers.IO) { Modrinth.search(lib.query.trim(), mc, lib.sort, lib.category, offset) }
        if (reset) lib.hits.clear()
        val seen = lib.hits.map { it.id }.toHashSet()
        lib.hits.addAll(page.hits.filter { seen.add(it.id) })
        lib.total = page.total
        lib.searchedFor = key
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        lib.searchError = true
    } finally {
        lib.searching = false
    }
}

private suspend fun rescan(lib: LibraryState, profile: Profile) {
    lib.scanning = true
    try {
        val list = Library.scan(profile, checkUpdates = true)
        lib.installed.clear()
        lib.installed.addAll(list)
        lib.scannedFor = scanKey(profile)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
    } finally {
        lib.scanning = false
    }
}

/** Installs a mod (and what it needs) into the selected profile, in the background. */
private fun installProject(state: LauncherState, projectId: String, version: Modrinth.Version? = null) {
    val lib = state.library
    if (projectId in lib.busy) return
    val profile = state.profiles[state.selectedProfile]
    lib.busy[projectId] = state.t("lib.installing")
    state.scope.launch {
        try {
            val result = Library.install(profile, projectId, version) { text -> lib.busy[projectId] = text }
            var message = state.t("lib.installed_toast").replace("%s", result.main).replace("%p", profile.name)
            if (result.dependencies.isNotEmpty()) message += "\n" + state.t("lib.deps") + " " + result.dependencies.joinToString(", ")
            if (state.gameRunning) message += "\n" + state.t("lib.restart")
            state.toast = message
            rescan(lib, profile)
        } catch (e: Exception) {
            state.dialog = state.t("lib.install_failed") to (e.message ?: e.toString())
        } finally {
            lib.busy.remove(projectId)
        }
    }
}

private fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

private fun openFolder(dir: File) {
    runCatching {
        dir.mkdirs()
        Desktop.getDesktop().open(dir)
    }
}

// ------------------------------------------------------------------ discover

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DiscoverView(state: LauncherState, mc: String) {
    val lib = state.library
    SearchBar(state, mc)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SortMenu(state)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            val rowState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            LazyRow(
                // The mouse wheel scrolls the categories sideways.
                Modifier.fillMaxWidth().onPointerEvent(PointerEventType.Scroll) { event ->
                    val change = event.changes.firstOrNull() ?: return@onPointerEvent
                    if (!change.isConsumed) scope.launch { rowState.scrollBy((change.scrollDelta.y + change.scrollDelta.x) * 60f) }
                },
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Chip(state.t("cat.all"), lib.category == null) { lib.category = null } }
                items(Modrinth.categories) { c ->
                    Chip(state.t("cat.$c"), lib.category == c) { lib.category = if (lib.category == c) null else c }
                }
                item { Spacer(Modifier.width(40.dp)) }
            }
            // Fade on the right edge shows there are more categories.
            if (rowState.canScrollForward) {
                Box(
                    Modifier.align(Alignment.CenterEnd).width(48.dp).height(34.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, Dnz.Background))),
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    val installedIds = lib.installed.mapNotNull { it.projectId }.toSet()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lib.searchError) {
            item { Text(state.t("mods.error"), color = Dnz.Danger, fontSize = 13.sp) }
        } else if (!lib.searching && lib.hits.isEmpty() && lib.searchedFor != null) {
            item { Text(state.t("lib.no_results"), color = Dnz.Muted, fontSize = 13.sp) }
        }
        items(lib.hits, key = { it.id }) { hit -> ModRow(state, hit, hit.id in installedIds) }
        if (lib.searching) {
            item { Loading(state.t("mods.searching")) }
        } else if (lib.hits.size < lib.total && !lib.searchError) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    SmallButton(state.t("lib.load_more")) { state.scope.launch { search(lib, mc, reset = false) } }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(state: LauncherState, mc: String) {
    val lib = state.library
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Dnz.Muted)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (lib.query.isEmpty()) Text(state.t("mods.search"), color = Dnz.Muted)
            BasicTextField(
                lib.query, { lib.query = it }, singleLine = true,
                textStyle = TextStyle(color = Dnz.Text, fontSize = 15.sp), cursorBrush = SolidColor(Dnz.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (lib.query.isNotEmpty()) {
            Icon(Icons.Filled.Close, null, tint = Dnz.Muted, modifier = Modifier.size(18.dp).dnzClickable { lib.query = "" })
            Spacer(Modifier.width(10.dp))
        }
        val count = if (lib.searchedFor != null) "  •  " + state.t("lib.results").replace("%s", Modrinth.format(lib.total.toLong())) else ""
        Text("Minecraft $mc$count", color = Dnz.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SortMenu(state: LauncherState) {
    val lib = state.library
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(10.dp))
                .dnzClickable { open = true }.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(state.t("lib.sort") + ": ", color = Dnz.Muted, fontSize = 12.sp)
            Text(state.t(lib.sort.key), color = Dnz.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Filled.ArrowDropDown, null, tint = Dnz.Muted)
        }
        DnzDropdown(open, onDismiss = { open = false }) {
            Modrinth.Sort.entries.forEach { sort ->
                DropdownMenuItem(
                    modifier = Modifier.menuItem(),
                    text = { Text(state.t(sort.key), color = if (sort == lib.sort) Dnz.Accent else Dnz.Text, fontSize = 13.sp) },
                    onClick = { lib.sort = sort; open = false },
                )
            }
        }
    }
}

@Composable
private fun ModRow(state: LauncherState, hit: Modrinth.Hit, installed: Boolean) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (hovered) Dnz.SurfaceHigh else Dnz.Surface)
            .hoverable(hover).dnzClickable(highlight = false) { state.library.openProject = hit.id }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModIcon(hit.iconUrl, hit.title, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(hit.title, color = Dnz.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text(state.t("lib.by").replace("%s", hit.author), color = Dnz.Muted, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            Text(hit.description, color = Dnz.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Stat("↓ " + Modrinth.format(hit.downloads))
                Stat("♥ " + Modrinth.format(hit.follows))
                hit.categories.filter { it in Modrinth.categories }.take(3).forEach { Tag(state.t("cat.$it")) }
            }
        }
        Spacer(Modifier.width(12.dp))
        InstallButton(state, hit.id, installed)
    }
}

@Composable
private fun InstallButton(state: LauncherState, projectId: String, installed: Boolean, version: Modrinth.Version? = null) {
    val busy = state.library.busy[projectId]
    when {
        busy != null -> Row(Modifier.widthIn(max = 200.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Dnz.Accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(busy, color = Dnz.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        installed -> Row(
            Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Dnz.Success.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, null, tint = Dnz.Success, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(state.t("lib.installed_badge"), color = Dnz.Success, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        else -> DnzButton(state.t("mods.install")) { installProject(state, projectId, version) }
    }
}

// ------------------------------------------------------------------ mod page

private class PageData(val project: Modrinth.Project, val author: String?, val versions: List<Modrinth.Version>)

@Composable
private fun ModPage(state: LauncherState, projectId: String) {
    val lib = state.library
    val mc = state.profiles[state.selectedProfile].version
    var reload by remember { mutableStateOf(0) }
    val data by produceState<Result<PageData>?>(null, projectId, mc, reload) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val project = Modrinth.project(projectId)
                PageData(project, Modrinth.author(project.id), Modrinth.versions(project.id, mc))
            }
        }
    }
    var zoomed by remember { mutableStateOf<String?>(null) }
    var allVersions by remember(projectId) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).dnzClickable { lib.openProject = null }.padding(end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Dnz.Muted)
                    Spacer(Modifier.width(8.dp))
                    Text(state.t("lib.back"), color = Dnz.Muted, fontWeight = FontWeight.SemiBold)
                }
            }
            val result = data
            when {
                result == null -> item { Loading(state.t("lib.page_loading")) }
                result.isFailure -> item {
                    Column {
                        Text(state.t("lib.page_error"), color = Dnz.Danger)
                        Spacer(Modifier.height(10.dp))
                        SmallButton(state.t("lib.retry")) { reload++ }
                    }
                }
                else -> {
                    val page = result.getOrThrow()
                    val p = page.project
                    // Compare with the real id: the page may have been opened with the mod's short name.
                    val installedVersions = lib.installed.filter { it.projectId == p.id }.mapNotNull { it.versionId }.toSet()
                    val installed = lib.installed.any { it.projectId == p.id }
                    item { PageHeader(state, page, installed, mc) }
                    if (p.gallery.isNotEmpty()) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(p.gallery) { image ->
                                    RemoteImage(
                                        image.url,
                                        Modifier.width(284.dp).height(160.dp).clip(RoundedCornerShape(12.dp)).background(Dnz.SurfaceHigh)
                                            .dnzClickable { zoomed = image.url },
                                    )
                                }
                            }
                        }
                    }
                    item { SectionTitle(state.t("lib.versions").replace("%s", mc)) }
                    if (page.versions.isEmpty()) {
                        item { Text(state.t("lib.no_version").replace("%s", mc), color = Dnz.Muted, fontSize = 13.sp) }
                    }
                    val shown = if (allVersions) page.versions else page.versions.take(5)
                    items(shown, key = { it.id }) { v -> VersionRow(state, p.id, v, v.id in installedVersions) }
                    if (!allVersions && page.versions.size > 5) {
                        item { SmallButton(state.t("lib.more_versions").replace("%s", page.versions.size.toString())) { allVersions = true } }
                    }
                    item { SectionTitle(state.t("lib.description")) }
                    item { Description(state, p) }
                }
            }
        }
        zoomed?.let { url ->
            Box(
                Modifier.fillMaxSize().background(Color(0xE6000000)).dnzClickable(highlight = false) { zoomed = null }.padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                RemoteImage(url, Modifier.fillMaxSize(), ContentScale.Fit)
            }
        }
    }
}

@Composable
private fun PageHeader(state: LauncherState, page: PageData, installed: Boolean, mc: String) {
    val p = page.project
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModIcon(p.iconUrl, p.title, 84.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, color = Dnz.Text, fontSize = 26.sp, fontWeight = FontWeight.Black)
                page.author?.let { Text(state.t("lib.by").replace("%s", it), color = Dnz.Muted, fontSize = 13.sp) }
                Spacer(Modifier.height(6.dp))
                Text(p.description, color = Dnz.Text, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Stat("↓ " + Modrinth.format(p.downloads) + " " + state.t("mods.downloads"))
                    Stat("♥ " + Modrinth.format(p.followers) + " " + state.t("lib.followers"))
                    p.license?.let { Stat(state.t("lib.license") + ": " + it) }
                    p.categories.filter { it in Modrinth.categories }.distinct().take(4).forEach { Tag(state.t("cat.$it")) }
                }
            }
            Spacer(Modifier.width(16.dp))
            if (page.versions.isEmpty() && !installed) {
                Text(state.t("lib.unavailable").replace("%s", mc), color = Dnz.Danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            } else {
                InstallButton(state, p.id, installed)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkChip(state.t("lib.link.modrinth"), p.pageUrl)
            p.sourceUrl?.let { LinkChip(state.t("lib.link.source"), it) }
            p.issuesUrl?.let { LinkChip(state.t("lib.link.issues"), it) }
            p.wikiUrl?.let { LinkChip(state.t("lib.link.wiki"), it) }
            p.discordUrl?.let { LinkChip(state.t("lib.link.discord"), it) }
        }
    }
}

@Composable
private fun VersionRow(state: LauncherState, projectId: String, v: Modrinth.Version, installed: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Dnz.Surface).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (label, color) = when (v.type) {
            "beta" -> state.t("lib.beta") to Color(0xFFFF9F43)
            "alpha" -> state.t("lib.alpha") to Dnz.Danger
            else -> state.t("lib.release") to Dnz.Success
        }
        Box(Modifier.width(64.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.name.ifBlank { v.number }, color = Dnz.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${v.number}  •  ${formatDate(v.date)}  •  ↓ ${Modrinth.format(v.downloads)}", color = Dnz.Muted, fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        InstallButton(state, projectId, installed, v)
    }
}

private fun formatDate(iso: String): String =
    if (iso.length >= 10) "${iso.substring(8, 10)}.${iso.substring(5, 7)}.${iso.substring(0, 4)}" else iso

@Composable
private fun Description(state: LauncherState, p: Modrinth.Project) {
    val (lines, cut) = remember(p.id) { Markdown.lines(p.body.ifBlank { p.description }) }
    Card(Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            when (line.kind) {
                Markdown.Kind.Blank -> Spacer(Modifier.height(6.dp))
                Markdown.Kind.Heading -> Text(
                    line.text, color = Dnz.Text, fontWeight = FontWeight.Bold,
                    fontSize = when (line.level) { 1 -> 20.sp; 2 -> 17.sp; else -> 15.sp },
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                Markdown.Kind.Bullet -> Row(Modifier.padding(vertical = 1.dp)) {
                    Text("•", color = Dnz.Accent, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(line.text, color = Color(0xFFCDD3E0), fontSize = 13.sp, lineHeight = 19.sp)
                }
                Markdown.Kind.Quote -> Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 2.dp)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Dnz.Accent.copy(alpha = 0.6f)))
                    Spacer(Modifier.width(10.dp))
                    Text(line.text, color = Dnz.Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Markdown.Kind.Code -> Text(
                    line.text, color = Color(0xFFCDD3E0), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().background(Dnz.SurfaceHigh).padding(horizontal = 8.dp, vertical = 1.dp),
                )
                Markdown.Kind.Text -> Text(line.text, color = Color(0xFFCDD3E0), fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        if (cut) {
            Spacer(Modifier.height(10.dp))
            LinkChip(state.t("lib.read_more"), p.pageUrl)
        }
    }
}

// ------------------------------------------------------------------ installed

@Composable
private fun InstalledView(state: LauncherState, profile: Profile) {
    val lib = state.library
    val list = lib.installed
    val updates = list.filter { it.update != null && it.projectId != null }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.t("lib.count").replace("%s", list.size.toString()).replace("%a", list.count { it.enabled }.toString()),
            color = Dnz.Muted, fontSize = 13.sp,
        )
        if (lib.scanning) {
            Spacer(Modifier.width(10.dp))
            CircularProgressIndicator(Modifier.size(14.dp), color = Dnz.Accent, strokeWidth = 2.dp)
        }
        Spacer(Modifier.weight(1f))
        if (updates.isNotEmpty()) {
            DnzButton(state.t("lib.update_all").replace("%s", updates.size.toString())) {
                updates.forEach { installProject(state, it.projectId!!, it.update) }
            }
            Spacer(Modifier.width(8.dp))
        }
        SmallButton(state.t("lib.refresh"), Icons.Filled.Refresh) { state.scope.launch { rescan(lib, profile) } }
        Spacer(Modifier.width(8.dp))
        SmallButton(state.t("lib.open_folder"), Icons.Filled.Home) { openFolder(Library.modsDir(profile)) }
    }
    Spacer(Modifier.height(12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (list.isEmpty()) {
            item {
                if (lib.scanning) Loading(state.t("lib.scanning")) else Card(Modifier.fillMaxWidth()) {
                    Text(state.t("lib.empty"), color = Dnz.Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        items(list, key = { it.file.name }) { mod -> InstalledRow(state, profile, mod) }
    }
}

@Composable
private fun InstalledRow(state: LauncherState, profile: Profile, mod: Library.Installed) {
    val lib = state.library
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var rowModifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if (hovered && mod.projectId != null) Dnz.SurfaceHigh else Dnz.Surface).hoverable(hover)
    if (mod.projectId != null) rowModifier = rowModifier.dnzClickable(highlight = false) { lib.openProject = mod.projectId }
    Row(rowModifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.alpha(if (mod.enabled) 1f else 0.4f)) { ModIcon(mod.iconUrl, mod.title, 42.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(mod.title, color = if (mod.enabled) Dnz.Text else Dnz.Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (mod.bundled) Tag(state.t("lib.bundled"), Dnz.Accent)
                if (mod.projectId == null && !mod.bundled) Tag(state.t("lib.not_on_modrinth"))
            }
            Text(listOfNotNull(mod.versionNumber, mod.file.name).joinToString("  •  "), color = Dnz.Muted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            mod.update?.let { Text(state.t("lib.update_to").replace("%s", it.number), color = Dnz.Accent, fontSize = 11.sp) }
        }
        Spacer(Modifier.width(10.dp))
        val update = mod.update
        if (update != null && mod.projectId != null) {
            val busy = lib.busy[mod.projectId]
            if (busy != null) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Dnz.Accent, strokeWidth = 2.dp)
            } else {
                SmallButton(state.t("lib.update"), accent = true) { installProject(state, mod.projectId, update) }
            }
            Spacer(Modifier.width(10.dp))
        }
        if (mod.locked) {
            Text(state.t("lib.required"), color = Dnz.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
        } else {
            Switch(
                checked = mod.enabled,
                onCheckedChange = { on -> toggle(state, mod, on) },
                modifier = Modifier.handCursor(), colors = SwitchDefaults.colors(
                    checkedThumbColor = Dnz.OnAccent, checkedTrackColor = Dnz.Accent,
                    uncheckedThumbColor = Dnz.Muted, uncheckedTrackColor = Dnz.SurfaceHigh, uncheckedBorderColor = Dnz.Border,
                ),
            )
        }
        if (!mod.bundled) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).dnzClickable {
                    state.confirm = Confirm(
                        state.t("lib.remove_confirm.title"), state.t("lib.remove_confirm.body").replace("%s", mod.title), state.t("lib.remove"),
                    ) {
                        if (Library.remove(mod)) {
                            lib.installed.remove(mod)
                            state.toast = state.t("lib.removed").replace("%s", mod.title)
                        } else {
                            state.dialog = state.t("lib.remove") to state.t("lib.toggle_failed")
                        }
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Delete, state.t("lib.remove"), tint = Dnz.Muted, modifier = Modifier.size(20.dp)) }
        } else {
            Spacer(Modifier.width(40.dp))
        }
    }
}

private fun toggle(state: LauncherState, mod: Library.Installed, enabled: Boolean) {
    val lib = state.library
    val file = Library.setEnabled(mod, enabled)
    if (file == null) {
        state.dialog = state.t("lib.remove") to state.t("lib.toggle_failed")
        return
    }
    val index = lib.installed.indexOf(mod)
    if (index >= 0) lib.installed[index] = mod.copy(file = file, enabled = enabled)
    state.toast = state.t(if (enabled) "lib.enabled_toast" else "lib.disabled_toast").replace("%s", mod.title) +
        if (state.gameRunning) "\n" + state.t("lib.restart") else ""
}

// ------------------------------------------------------------------ small parts

@Composable
private fun ProfileMenu(state: LauncherState) {
    var open by remember { mutableStateOf(false) }
    val profile = state.profiles[state.selectedProfile]
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Dnz.Surface).border(1.dp, Dnz.Border, RoundedCornerShape(12.dp))
                .dnzClickable { open = true }.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(state.t("lib.profile"), color = Dnz.Muted, fontSize = 10.sp)
                Text("${profile.name}  •  ${profile.version}", color = Dnz.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ArrowDropDown, null, tint = Dnz.Muted)
        }
        DnzDropdown(open, onDismiss = { open = false }) {
            state.profiles.forEachIndexed { i, p ->
                DropdownMenuItem(
                    modifier = Modifier.menuItem(),
                    text = { Text("${p.name}  •  ${p.version}", color = if (i == state.selectedProfile) Dnz.Accent else Dnz.Text) },
                    onClick = { state.selectedProfile = i; state.version = p.version; open = false },
                )
            }
        }
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Dnz.Surface).padding(4.dp)) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(if (active) Dnz.SurfaceHigh else Color.Transparent)
                    .dnzClickable { onSelect(i) }.padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(label, color = if (active) Dnz.Text else Dnz.Muted, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) Dnz.Accent else Dnz.Surface)
            .border(1.dp, if (selected) Color.Transparent else Dnz.Border, RoundedCornerShape(50))
            .dnzClickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (selected) Dnz.OnAccent else Dnz.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Small outlined button (used on the Mods and Settings pages). */
@Composable
fun SmallButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, accent: Boolean = false, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (accent) Dnz.Accent.copy(alpha = if (hovered) 0.35f else 0.22f) else if (hovered) Dnz.SurfaceHigh else Dnz.Surface)
            .border(1.dp, if (accent) Dnz.Accent.copy(alpha = 0.5f) else Dnz.Border, RoundedCornerShape(10.dp))
            .hoverable(hover).dnzClickable(highlight = false, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (accent) Dnz.Accent else Dnz.Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (accent) Dnz.Accent else Dnz.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LinkChip(label: String, url: String) {
    SmallButton("$label ↗") { openUrl(url) }
}

@Composable
private fun ModIcon(url: String?, title: String, size: Dp) {
    RemoteImage(url, Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Dnz.SurfaceHigh)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(title.take(1).uppercase(), color = Dnz.Accent, fontWeight = FontWeight.Black, fontSize = (size.value * 0.4f).sp)
        }
    }
}

@Composable
private fun Stat(text: String) {
    Text(text, color = Dnz.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Tag(text: String, color: Color = Dnz.Muted) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Dnz.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Loading(text: String) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), color = Dnz.Accent, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = Dnz.Muted, fontSize = 13.sp)
    }
}
