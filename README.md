# Glockenpost für Android

Die App sammelt Meldungen zu Glockenguss, Glockenweihen, Geläut- und Glockenstuhlsanierungen und Klöppeltausch. Sie ordnet sie nach Jahr, die neuesten zuerst, und bietet Suche sowie Kategorie-Filter.

## Woher die Meldungen kommen

1. **Geprüfte Wochenliste** (`data/news.json` in diesem Repository). Claude ergänzt sie jede Woche mit Meldungen von Websites, Lokalzeitungen und Pfarreiseiten. Die App lädt sie bei jeder Aktualisierung.
2. **Automatische Suche**: Die App durchsucht täglich die deutschsprachigen Google-News-Feeds für die Schweiz, Deutschland und Österreich. Social-Media-Beiträge werden herausgefiltert. Diese Treffer sind mit «automatisch gefunden» markiert.
3. **Mitgelieferte Liste**: Beim ersten Start zeigt die App die Liste vom 12.10.2026, auch ohne Internet.

Die App sucht einmal täglich im Hintergrund nach neuen Meldungen und meldet Neues per Benachrichtigung. Über die Taste ⟳ lässt sich die Suche jederzeit von Hand starten.

## APK bauen, ohne Android Studio

1. Lege auf GitHub ein **öffentliches** Repository an, zum Beispiel `glockenpost`. Öffentlich muss es sein, damit die App `data/news.json` ohne Anmeldung laden kann.
2. Lade den gesamten Inhalt dieses Ordners hoch, auch den versteckten Ordner `.github`. Beim Hochladen über den Browser alles per Drag & Drop in «Add file → Upload files» ziehen.
3. Unter **Actions** startet der Ablauf «App bauen» automatisch. Er dauert rund 5 Minuten.
4. Unter **Releases** liegt danach `Glockenpost.apk`. Öffne diese Seite auf dem Handy, lade die APK herunter und installiere sie. Android fragt einmalig, ob der Browser Apps installieren darf.

Der Build trägt die Adresse der Wochenliste automatisch ein:
`https://raw.githubusercontent.com/<dein-name>/<repo>/main/data/news.json`.
In der App lässt sie sich unter Einstellungen ändern.

Jede Änderung am App-Code erzeugt eine neue Version. Änderungen an `data/` lösen keinen neuen Build aus, die App holt sie selbst. Alle Versionen sind mit demselben Schlüssel (`app/glockenpost.keystore`) signiert, deshalb lassen sie sich direkt über die alte Version installieren.

## Mit Android Studio

Öffne den Ordner in Android Studio (Koala oder neuer). Falls Android Studio fragt, lass es den Gradle-Wrapper erzeugen, und starte die App dann auf einem Gerät oder im Emulator.

## Format von `data/news.json`

```json
{
  "updated": "2026-10-12",
  "items": [
    {
      "date": "2026-09-21",
      "category": "weihe",
      "title": "Feusisberg: Abt Urban weiht neue Meinradsglocke",
      "place": "Feusisberg SZ (CH)",
      "source": "March-Anzeiger / Höfner Volksblatt",
      "url": "https://marchhoefe24.ch/articles/407172-abt-weihte-neue-glocke-feierlich",
      "summary": "…"
    }
  ]
}
```

`date` ist `YYYY-MM-DD`, `YYYY-MM` oder `YYYY`. `category` ist `guss`, `weihe`, `san`, `kloep` oder `sonst`.
