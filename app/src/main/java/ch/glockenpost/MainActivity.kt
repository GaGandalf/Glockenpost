package ch.glockenpost

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RefreshWorker.createChannel(this)
        RefreshWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { GlockenpostTheme { NewsScreen() } }
    }
}

/* ---------------- Zustand ---------------- */

class NewsViewModel(app: Application) : AndroidViewModel(app) {
    val repo = NewsRepository(app)
    val items = MutableStateFlow<List<NewsItem>>(emptyList())
    val updated = MutableStateFlow("")
    val refreshing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            reload()
            refresh(silent = true)
        }
    }

    private suspend fun reload() {
        val f = withContext(Dispatchers.IO) { repo.load() }
        items.value = f.items.sortedByDescending { it.sortKey }
        updated.value = f.updated
    }

    fun refresh(silent: Boolean = false) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                val r = repo.refresh()
                reload()
                val msg = when {
                    r.newCount == 1 -> "1 neue Meldung"
                    r.newCount > 1 -> "${r.newCount} neue Meldungen"
                    else -> "Keine neuen Meldungen"
                }
                if (!silent || r.newCount > 0) {
                    message.value = if (!r.curatedOk && repo.curatedUrl.isNotBlank() && !silent)
                        "$msg. Die geprüfte Liste war nicht erreichbar." else msg
                }
            } catch (e: Exception) {
                if (!silent) message.value = "Aktualisierung fehlgeschlagen. Prüfe die Internetverbindung."
            } finally {
                refreshing.value = false
            }
        }
    }
}

/* ---------------- Gestaltung ---------------- */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2C6B5F), onPrimary = Color.White,
    secondary = Color(0xFF8A5A22), onSecondary = Color.White,
    background = Color(0xFFF2F3F1), onBackground = Color(0xFF1D2423),
    surface = Color(0xFFF2F3F1), onSurface = Color(0xFF1D2423),
    surfaceVariant = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFF5D6866),
    outline = Color(0xFFD9DDDB), secondaryContainer = Color(0xFF1D2423), onSecondaryContainer = Color(0xFFF2F3F1),
    tertiary = Color(0xFFF3DFB8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6CC0AD), onPrimary = Color(0xFF0E1A17),
    secondary = Color(0xFFD39C5C), onSecondary = Color(0xFF1D1206),
    background = Color(0xFF121716), onBackground = Color(0xFFE6EBE9),
    surface = Color(0xFF121716), onSurface = Color(0xFFE6EBE9),
    surfaceVariant = Color(0xFF1A2120), onSurfaceVariant = Color(0xFF9AA6A3),
    outline = Color(0xFF2C3533), secondaryContainer = Color(0xFFE6EBE9), onSecondaryContainer = Color(0xFF121716),
    tertiary = Color(0xFF5A4422),
)

@Composable
fun GlockenpostTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@Composable
private fun categoryColor(c: Category): Color {
    val dark = isSystemInDarkTheme()
    return when (c) {
        Category.GUSS -> if (dark) Color(0xFFD39C5C) else Color(0xFF8A5A22)
        Category.WEIHE -> if (dark) Color(0xFF6CC0AD) else Color(0xFF2C6B5F)
        Category.SAN -> if (dark) Color(0xFFA3A8DC) else Color(0xFF5B5F8F)
        Category.KLOEP -> if (dark) Color(0xFFE08A83) else Color(0xFF9B3F3A)
        Category.SONST -> if (dark) Color(0xFF9AA6A3) else Color(0xFF5D6866)
    }
}

private val MONTHS = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.")
private val MONTHS_LONG = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")

fun formatDate(d: String): String {
    val p = d.split("-")
    return runCatching {
        when (p.size) {
            3 -> "${p[2].toInt()}. ${MONTHS[p[1].toInt() - 1]} ${p[0]}"
            2 -> "${MONTHS_LONG[p[1].toInt() - 1]} ${p[0]}"
            else -> p[0]
        }
    }.getOrDefault(d)
}

private fun isoToSwiss(d: String): String = d.split("-").reversed().joinToString(".")

/* ---------------- Bildschirm ---------------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NewsScreen(vm: NewsViewModel = viewModel()) {
    val all by vm.items.collectAsState()
    val updated by vm.updated.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val message by vm.message.collectAsState()

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<Category?>(null) }
    var onlyCurated by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val uri = LocalUriHandler.current

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.message.value = null }
    }

    val terms = NewsRepository.normalize(query).split(" ").filter { it.isNotBlank() }
    val filtered = remember(all, query, category, onlyCurated) {
        all.filter { n ->
            (category == null || Category.of(n.category) == category) &&
                (!onlyCurated || n.curated) &&
                terms.all { t ->
                    NewsRepository.normalize(
                        listOf(n.title, n.summary, n.place, n.source, Category.of(n.category).label).joinToString(" ")
                    ).contains(t)
                }
        }
    }
    val byYear = filtered.groupBy { it.year }.toSortedMap(compareByDescending { it })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text(
                            buildAnnotatedString {
                                append("Glocken")
                                pushStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary)); append("post"); pop()
                            },
                            fontFamily = FontFamily.Serif, fontSize = 26.sp,
                        )
                        Text(
                            "${all.size} Meldungen" + if (updated.isNotBlank()) " · Liste vom ${isoToSwiss(updated)}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (refreshing) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Jetzt aktualisieren") }
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Einstellungen") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text("Ort, Giesserei, Kirche, Stichwort …") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Suche leeren") }
                },
                shape = RoundedCornerShape(12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    FilterChip(
                        selected = onlyCurated, onClick = { onlyCurated = !onlyCurated },
                        label = { Text("Nur geprüfte") },
                    )
                }
                items(Category.entries.toList()) { c ->
                    val count = all.count { Category.of(it.category) == c }
                    FilterChip(
                        selected = category == c,
                        onClick = { category = if (category == c) null else c },
                        leadingIcon = { Box(Modifier.size(8.dp).background(categoryColor(c), CircleShape)) },
                        label = { Text("${c.label} $count") },
                    )
                }
            }
            Text(
                if (filtered.size == all.size) "Alle ${all.size} Meldungen" else "${filtered.size} von ${all.size} Meldungen",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (filtered.isEmpty()) {
                Text(
                    "Keine Meldung passt zu deiner Suche. Versuch einen Ortsnamen oder eine Giesserei, oder setz die Filter zurück.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                byYear.forEach { (year, list) ->
                    stickyHeader(key = "y$year") { YearHeader(year, list.size) }
                    items(list, key = { it.url + "|" + it.date }) { n -> NewsRow(n, terms) { uri.openUri(n.url) } }
                }
            }
        }
    }

    if (showSettings) SettingsDialog(vm.repo) { showSettings = false }
}

@Composable
private fun YearHeader(year: String, count: Int) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(year, fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 34.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (count == 1) "1 Meldung" else "$count Meldungen",
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun NewsRow(n: NewsItem, terms: List<String>, onOpen: () -> Unit) {
    val cat = Category.of(n.category)
    val mark = MaterialTheme.colorScheme.tertiary
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                cat.label.uppercase(), color = categoryColor(cat),
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
            )
            Text(
                "  ·  " + formatDate(n.date), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(highlight(n.title, terms, mark), fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp)
        if (n.summary.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                highlight(n.summary, terms, mark), style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                maxLines = 5, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        val meta = listOf(n.source, n.place).filter { it.isNotBlank() }.joinToString(" · ") +
            if (n.curated) "" else "  ·  automatisch gefunden"
        Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
}

/** Markiert Suchbegriffe (Gross-/Kleinschreibung egal). */
private fun highlight(text: String, terms: List<String>, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val lower = text.lowercase()
    for (t in terms) {
        if (t.length < 2) continue
        var i = lower.indexOf(t)
        while (i >= 0) {
            addStyle(SpanStyle(background = color), i, i + t.length)
            i = lower.indexOf(t, i + t.length)
        }
    }
}

@Composable
private fun SettingsDialog(repo: NewsRepository, onClose: () -> Unit) {
    var url by remember { mutableStateOf(repo.curatedUrl) }
    var feeds by remember { mutableStateOf(repo.feedsEnabled) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Einstellungen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Adresse der wöchentlich geprüften Liste (news.json)", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text("https://raw.githubusercontent.com/…/data/news.json") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatische Suche in News-Feeds", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Zeitungen und Lokalzeitungen aus CH, DE und AT, ohne Social Media",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = feeds, onCheckedChange = { feeds = it })
                }
                val last = repo.lastRefresh
                Text(
                    "Die App sucht einmal täglich im Hintergrund nach neuen Meldungen." +
                        if (last.isNotBlank()) " Zuletzt: $last" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { repo.curatedUrl = url; repo.feedsEnabled = feeds; onClose() }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } },
    )
}
