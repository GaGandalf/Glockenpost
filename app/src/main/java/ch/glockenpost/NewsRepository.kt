package ch.glockenpost

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RefreshResult(val newCount: Int, val curatedOk: Boolean, val feedCount: Int)

class NewsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val cacheFile = File(context.filesDir, "news_cache.json")
    private val prefs = context.getSharedPreferences("glockenpost", Context.MODE_PRIVATE)

    var curatedUrl: String
        get() = prefs.getString("curated_url", null) ?: BuildConfig.CURATED_URL
        set(value) = prefs.edit().putString("curated_url", value.trim()).apply()

    var feedsEnabled: Boolean
        get() = prefs.getBoolean("feeds_enabled", true)
        set(value) = prefs.edit().putBoolean("feeds_enabled", value).apply()

    var lastRefresh: String
        get() = prefs.getString("last_refresh", "") ?: ""
        private set(value) = prefs.edit().putString("last_refresh", value).apply()

    /** Gespeicherte Meldungen, beim ersten Start die mitgelieferte Liste. */
    fun load(): NewsFile {
        val text = runCatching { if (cacheFile.exists()) cacheFile.readText() else null }.getOrNull()
            ?: context.assets.open("news.json").bufferedReader().use { it.readText() }
        return runCatching { json.decodeFromString(NewsFile.serializer(), text) }
            .getOrElse {
                val seed = context.assets.open("news.json").bufferedReader().use { r -> r.readText() }
                json.decodeFromString(NewsFile.serializer(), seed)
            }
    }

    suspend fun refresh(): RefreshResult = withContext(Dispatchers.IO) {
        val old = load()
        val oldKeys = old.items.map { key(it) }.toSet()

        // 1. Geprüfte Wochenliste
        val url = curatedUrl
        val curated: NewsFile? = if (url.isBlank()) null else runCatching {
            json.decodeFromString(NewsFile.serializer(), Http.getText(url))
        }.getOrNull()
        val curatedItems = (curated?.items ?: old.items.filter { it.curated }).map { it.copy(curated = true) }

        // 2. Automatische Feed-Suche
        val feedItems = if (feedsEnabled) FeedFetcher.fetchAll() else emptyList()

        // 3. Zusammenführen ohne Duplikate; geprüfte Meldungen haben Vorrang
        val merged = LinkedHashMap<String, NewsItem>()
        val urls = HashSet<String>()
        fun add(n: NewsItem) {
            val k = key(n)
            if (k.isBlank() || merged.containsKey(k) || !urls.add(n.url)) return
            merged[k] = n
        }
        curatedItems.forEach(::add)
        feedItems.sortedByDescending { it.sortKey }.forEach(::add)
        old.items.filter { !it.curated }.forEach(::add)

        val items = merged.values.sortedByDescending { it.sortKey }.take(3000)
        val file = NewsFile(updated = curated?.updated ?: old.updated, items = items)
        cacheFile.writeText(json.encodeToString(NewsFile.serializer(), file))
        lastRefresh = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date())

        RefreshResult(
            newCount = items.count { key(it) !in oldKeys },
            curatedOk = curated != null,
            feedCount = feedItems.size,
        )
    }

    companion object {
        /** Normalisierter Titel als Schlüssel gegen Doppelmeldungen aus mehreren Quellen. */
        fun key(n: NewsItem): String = normalize(n.title).take(70)

        fun normalize(s: String): String = s.lowercase(Locale.GERMAN)
            .replace("ß", "ss").replace("ä", "a").replace("ö", "o").replace("ü", "u")
            .replace("é", "e").replace("è", "e").replace("à", "a")
            .replace(Regex("[^a-z0-9]+"), " ").trim()
    }
}
