# PrivatBrowser

Ein datenschutzorientierter Android-Browser (Kotlin + WebView).

## Aktueller Stand (Version 0.2.0)
- Helle Browser-Oberfläche, Adress-/Suchleiste (Startpage), Zurück/Vor/Start/Neu laden
- Mehrere Tabs, lokale Lesezeichen, Downloads in den Ordner „Download“
- Verständliche Fehlerseiten, unsichere Zertifikate werden immer abgelehnt
- Links zu Mail/Telefon/anderen Apps nur nach Rückfrage, unsichere Link-Typen blockiert
- Cookies, Cache und Website-Speicher werden beim Start und Beenden gelöscht
- „Sitzungsdaten löschen“ und „Private Sitzung beenden und alles löschen“
- Keine Passwort- oder Formularspeicherung, Drittanbieter-Cookies standardmäßig blockiert
- Werbe-/Tracker-Sperre auf Host-Ebene: eingebaute Liste + optionale Filterliste (StevenBlack/hosts), Ausnahmen pro Seite
- Einstellungen mit Versionsanzeige, Update-Prüfung über GitHub Releases (SHA-256 + Signaturprüfung)

Download der neuesten Version: https://github.com/Postmen1971/vpn_browser/releases/latest

## VPN
Ein echter VPN-Tunnel kann nicht allein durch eine Browser-WebView erzeugt werden. Dafür braucht es einen VPN-Dienst und einen Anbieter bzw. eine WireGuard-/OpenVPN-Konfiguration. Die VPN-Integration wird als eigener Entwicklungsschritt ergänzt; die App behauptet bis dahin nicht, dass ein VPN aktiv ist.

## Build und Veröffentlichung
- GitHub Actions baut bei jedem Push eine APK (Artefakt `PrivatBrowser-apk`).
- Eine neue Version wird veröffentlicht, indem `versionCode`/`versionName` in `app/build.gradle.kts` erhöht, ein Abschnitt in `CHANGELOG.md` ergänzt und ein Tag `vX.Y.Z` gepusht wird. Der Workflow erstellt dann eine GitHub Release mit `PrivatBrowser.apk` und `PrivatBrowser.apk.sha256`.
- Signatur: Die Secrets `SIGNING_KEYSTORE_BASE64` (Keystore, Base64) und `SIGNING_PASSWORD` müssen unter *Settings → Secrets and variables → Actions* gesetzt sein. Ohne sie wird mit einem wechselnden Debug-Schlüssel signiert und In-App-Updates funktionieren nicht. Der Schlüssel liegt nie im Repository.
