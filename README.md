# PrivatBrowser

Ein datenschutzorientierter Android-Browser (Kotlin + WebView).

## Aktueller Stand
- Helle Browser-Oberfläche
- WebView mit JavaScript für Website-Kompatibilität
- Temporäre Cookies: werden beim Start und beim Beenden der App gelöscht
- Keine Passwortspeicherung durch die App
- Schaltfläche zum Löschen von Cookies, Cache und Website-Speicher
- Einfache lokale Werbe-/Tracker-Host-Sperrliste als Ausgangspunkt

## VPN
Ein echter VPN-Tunnel kann nicht allein durch eine Browser-WebView erzeugt werden. Dafür braucht es einen VPN-Dienst und einen Anbieter bzw. eine WireGuard-/OpenVPN-Konfiguration. Die VPN-Integration wird als eigener Entwicklungsschritt ergänzt; die App behauptet bis dahin nicht, dass ein VPN aktiv ist.

## Build
Das GitHub-Actions-Workflow `.github/workflows/android.yml` baut bei Push und Pull Request eine Debug-APK.
