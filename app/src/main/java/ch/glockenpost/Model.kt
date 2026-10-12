package ch.glockenpost

import kotlinx.serialization.Serializable

@Serializable
data class NewsFile(
    val updated: String = "",
    val items: List<NewsItem> = emptyList(),
)

/**
 * Eine Meldung.
 * date: "YYYY-MM-DD", "YYYY-MM" oder "YYYY"
 * category: guss | weihe | san | kloep | sonst
 * curated: true = von der wöchentlich geprüften Liste, false = automatisch aus News-Feeds
 */
@Serializable
data class NewsItem(
    val date: String,
    val category: String = "sonst",
    val title: String,
    val place: String = "",
    val source: String = "",
    val url: String,
    val summary: String = "",
    val curated: Boolean = true,
) {
    val year: String get() = date.take(4)

    /** Sortierschlüssel: unvollständige Daten stehen am Ende ihres Monats bzw. Jahres. */
    val sortKey: String
        get() {
            val p = date.split("-")
            return p[0] + "-" + (p.getOrNull(1) ?: "12") + "-" + (p.getOrNull(2) ?: "99")
        }
}

enum class Category(val key: String, val label: String) {
    GUSS("guss", "Guss & neue Glocken"),
    WEIHE("weihe", "Weihe & Einbau"),
    SAN("san", "Sanierung"),
    KLOEP("kloep", "Klöppel"),
    SONST("sonst", "Sonstiges");

    companion object {
        fun of(key: String): Category = entries.firstOrNull { it.key == key } ?: SONST
    }
}
