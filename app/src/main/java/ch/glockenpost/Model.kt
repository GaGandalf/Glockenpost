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
    /** Land der Kirche als ISO-Kürzel (CH, DE, AT, …); leer = unbekannt. */
    val country: String = "",
    val source: String = "",
    val url: String,
    val summary: String = "",
    val curated: Boolean = true,
) {
    val year: String get() = date.take(4)

    /** Land: Feld country, sonst letztes Länderkürzel im Ort. */
    val land: String
        get() {
            if (country.isNotBlank()) return country.uppercase()
            val codes = Regex("\\b(CH|DE|AT|LI|IT|FR|LU|NL|BE|PL|CZ)\\b").findAll(place).map { it.value }.toList()
            if (codes.isNotEmpty()) return codes.last()
            return when {
                "Österreich" in place -> "AT"
                "Deutschland" in place -> "DE"
                "Schweiz" in place -> "CH"
                else -> ""
            }
        }

    /** Erster Tag des Zeitraums, den das Datum bezeichnet (für Zeitraumfilter). */
    val startKey: String
        get() {
            val p = date.split("-")
            return p[0] + "-" + (p.getOrNull(1) ?: "01") + "-" + (p.getOrNull(2) ?: "01")
        }

    /** Sortierschlüssel: unvollständige Daten stehen am Ende ihres Monats bzw. Jahres. */
    val sortKey: String
        get() {
            val p = date.split("-")
            return p[0] + "-" + (p.getOrNull(1) ?: "12") + "-" + (p.getOrNull(2) ?: "99")
        }
}

object Lands {
    private val names = mapOf(
        "CH" to "Schweiz", "DE" to "Deutschland", "AT" to "Österreich", "LI" to "Liechtenstein",
        "IT" to "Italien", "FR" to "Frankreich", "LU" to "Luxemburg", "NL" to "Niederlande",
        "BE" to "Belgien", "PL" to "Polen", "CZ" to "Tschechien",
    )
    private val order = listOf("CH", "DE", "AT")

    fun name(code: String): String = names[code] ?: code.ifBlank { "Unbekannt" }

    fun sorted(codes: Collection<String>): List<String> = codes.distinct().sortedWith(
        compareBy<String>({ order.indexOf(it).let { i -> if (i < 0) 99 else i } }, { it })
    )
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
