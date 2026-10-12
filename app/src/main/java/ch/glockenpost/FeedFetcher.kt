package ch.glockenpost

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Holt Meldungen aus Google-News-Feeds (deutschsprachige Ausgaben CH, DE, AT).
 * Google News bündelt Websites von Zeitungen, Lokalzeitungen und Sendern.
 * Beiträge von Social-Media-Plattformen werden zusätzlich herausgefiltert.
 */
object FeedFetcher {

    private val queries = listOf(
        "Glockenguss OR \"Glocke gegossen\" OR \"Glocken gegossen\" OR \"neue Glocke\" OR \"neue Glocken\"",
        "Glockenweihe OR Glockensegnung OR Glockenaufzug OR Glockeneinholung OR \"Glocke geweiht\"",
        "Glockenstuhl OR \"Geläut saniert\" OR \"Geläute saniert\" OR \"Glocken schweigen\" OR Glockensanierung",
        "Klöppel Glocke OR Glockenklöppel OR Ankerklöppel OR \"neue Klöppel\"",
        "Glockengiesserei OR Glockengießerei OR Rüetschi OR Grassmayr OR Perner OR Rincker",
    )

    private val editions = listOf("CH" to "CH:de", "DE" to "DE:de", "AT" to "AT:de")

    private val bellWords = listOf("glock", "geläut", "kloeppel", "klöppel", "giesserei", "gießerei")

    private val socialHosts = listOf(
        "facebook.", "instagram.", "twitter.", "x.com", "tiktok.", "youtube.", "youtu.be",
        "linkedin.", "pinterest.", "threads.net", "reddit.", "telegram.", "whatsapp.", "snapchat.",
    )

    fun fetchAll(): List<NewsItem> {
        val out = mutableListOf<NewsItem>()
        for (q in queries) for ((gl, ceid) in editions) {
            val url = "https://news.google.com/rss/search?q=" +
                URLEncoder.encode("$q when:30d", "UTF-8") + "&hl=de&gl=$gl&ceid=$ceid"
            runCatching { out += parse(Http.get(url)) }
        }
        return out
    }

    private fun parse(stream: InputStream): List<NewsItem> = stream.use { input ->
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val items = mutableListOf<NewsItem>()
        var inItem = false
        var title = ""; var link = ""; var pub = ""; var source = ""; var sourceUrl = ""; var desc = ""
        var tag = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (tag == "item") {
                        inItem = true
                        title = ""; link = ""; pub = ""; source = ""; sourceUrl = ""; desc = ""
                    } else if (inItem && tag == "source") {
                        sourceUrl = parser.getAttributeValue(null, "url") ?: ""
                    }
                }
                XmlPullParser.TEXT -> if (inItem) {
                    val t = parser.text ?: ""
                    when (tag) {
                        "title" -> title += t
                        "link" -> link += t
                        "pubDate" -> pub += t
                        "source" -> source += t
                        "description" -> desc += t
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        inItem = false
                        toItem(title.trim(), link.trim(), pub.trim(), source.trim(), sourceUrl, desc)?.let { items += it }
                    }
                    tag = ""
                }
            }
            parser.next()
        }
        items
    }

    private fun toItem(rawTitle: String, link: String, pub: String, source: String, sourceUrl: String, desc: String): NewsItem? {
        if (rawTitle.isBlank() || link.isBlank()) return null
        val host = sourceUrl.lowercase()
        if (socialHosts.any { host.contains(it) }) return null
        // Google hängt " - Quelle" an den Titel an
        val title = if (source.isNotBlank() && rawTitle.endsWith(" - $source")) {
            rawTitle.removeSuffix(" - $source")
        } else rawTitle
        val lower = title.lowercase()
        if (bellWords.none { lower.contains(it) }) return null
        val date = parseDate(pub) ?: return null
        val summary = stripHtml(desc).let { s ->
            if (s.isBlank() || s.lowercase().startsWith(lower.take(30))) "" else s.take(300)
        }
        return NewsItem(
            date = date,
            category = classify(lower + " " + summary.lowercase()),
            title = title,
            place = "",
            source = source.ifBlank { sourceUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.") },
            url = link,
            summary = summary,
            curated = false,
        )
    }

    fun classify(text: String): String = when {
        "klöppel" in text || "kloeppel" in text -> "kloep"
        listOf("weihe", "geweiht", "segnung", "gesegnet", "aufzug", "einholung", "eingeweiht", "läuten wieder", "läutet wieder")
            .any { it in text } -> "weihe"
        listOf("sanier", "glockenstuhl", "restaur", "renovier", "schweig", "verstumm").any { it in text } -> "san"
        listOf("guss", "gegossen", "giesserei", "gießerei", "neue glocke").any { it in text } -> "guss"
        else -> "sonst"
    }

    private fun parseDate(s: String): String? = runCatching {
        val d = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(s)!!
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
    }.getOrNull()

    private fun stripHtml(s: String): String =
        s.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("\\s+"), " ").trim()
}

object Http {
    fun get(url: String): InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Glockenpost/1.0")
        c.setRequestProperty("Accept-Language", "de-CH,de;q=0.9")
        if (c.responseCode !in 200..299) {
            c.disconnect()
            throw IllegalStateException("HTTP ${c.responseCode}")
        }
        return c.inputStream
    }

    fun getText(url: String): String = get(url).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
