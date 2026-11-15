# PrivatBrowser – Projektplan und Entwicklungs-Roadmap

Stand: 15.11.2026  
Repository: https://github.com/Postmen1971/vpn_browser

## 1. Ziel des Projekts

Wir entwickeln einen eigenen, möglichst datenschutzfreundlichen Android-Browser. Er soll Webseiten zuverlässig öffnen und Anmeldungen ermöglichen, dabei aber so wenig Daten wie praktikabel auf dem Gerät zurücklassen.

Der Browser soll einfach zu bedienen sein und eine helle, übersichtliche Oberfläche haben.

**Wichtig:** Datenschutzfunktionen werden ehrlich beschrieben. Eine kleine Sperrliste ist noch kein vollständiger Werbe- oder Trackingschutz. Das Löschen von Browserdaten ist keine Garantie für vollständige Anonymität. Ohne aktive VPN-Verbindung wird die normale Internetverbindung des Geräts verwendet.

## 2. Grundregeln

- Keine Speicherung von Passwörtern durch unsere App.
- Keine unnötige Erfassung oder Übertragung von Nutzungsdaten.
- Cookies und Website-Daten sollen nur so lange wie nötig bestehen.
- Sitzungsdaten sollen auf Knopfdruck gelöscht werden können.
- Nur sichere Webseitenverbindungen über HTTPS bevorzugen; unverschlüsselte HTTP-Seiten werden derzeit technisch noch zugelassen, weil manche Webseiten sie benötigen.
- Keine Zugangsdaten, VPN-Dateien, privaten Schlüssel oder Passwörter in GitHub einchecken.
- Vor jeder größeren Erweiterung muss die APK erfolgreich gebaut und getestet werden.

## 3. Geplante Funktionen

### A. Browser-Grundfunktionen – zuerst

- Helle, einfache und gut lesbare Oberfläche.
- Adress- und Suchleiste.
- Startseite mit Startpage-Suche.
- Zurück, Startseite und Neu laden.
- Anzeige der aktuell geöffneten Internetadresse.
- Ladefortschritt beim Öffnen einer Seite.
- Verständliche Fehlermeldungen, wenn eine Seite nicht erreichbar ist.
- Sichere Behandlung von Links und nicht unterstützten Protokollen.

### B. Datenschutz und private Sitzung

- Cookies beim Beenden der privaten Sitzung löschen.
- Cache und Browserverlauf löschen.
- Website-Speicher (Local Storage, Session Storage und WebStorage) löschen.
- Formulardaten nicht dauerhaft speichern.
- Keine Passwortspeicherung durch die App.
- Schaltfläche „Sitzungsdaten löschen“.
- Später eine klar erkennbare Funktion „Private Sitzung beenden und alles löschen“.
- Prüfen, dass beim Löschen keine noch laufende Seite ungewollt weiter Daten schreibt.

**Technischer Hinweis:** Android WebView kann Website-Daten innerhalb der App gemeinsam verwalten. Wirklich voneinander getrennte Profile oder unabhängige Sitzungen sind deshalb ein eigener Entwicklungspunkt und müssen technisch geprüft werden. Der aktuelle Stand bietet noch keine garantierte Profil-Isolation.

### C. Werbung und Tracking reduzieren

- Die aktuelle kleine Host-Sperrliste erweitern.
- Eine gepflegte Filterliste verwenden und regelmäßig aktualisieren.
- Tracker- und Werbedomains blockieren.
- Ausnahmen ermöglichen, falls eine Webseite sonst nicht richtig funktioniert.
- Prüfen, ob blockierte Inhalte die Bedienbarkeit wichtiger Webseiten beeinträchtigen.
- Den Schutz realistisch testen und nicht als vollständigen Werbeblocker bezeichnen, solange er das nicht nachweislich ist.

### D. Bedienung und Komfort – nach den Grundfunktionen

- Download-Verwaltung mit sicherem Speicherort.
- Optional mehrere Tabs.
- Lesezeichen und Favoriten – möglichst ohne unnötige Cloud-Synchronisierung.
- Einstellungen für Datenschutz und Sperrliste.
- Anzeige, ob VPN aktiv ist, sobald die VPN-Funktion später eingebaut wurde.
- Eine gut sichtbare Möglichkeit, alle privaten Daten zu löschen.

### E. Versionsnummern und Update-Funktion

Diese Funktion gehört fest zum PrivatBrowser. Updates sollen nachvollziehbar und sicher über GitHub Releases bereitgestellt werden.

Geplantes Verhalten:
- Die App zeigt ihre installierte Versionsnummer in den Einstellungen an (z. B. Version 1.0.0).
- Jede Veröffentlichung erhält eine Versionsnummer und eine fortlaufende Android-`versionCode`.
- GitHub Actions erstellt bei einer Veröffentlichung ein APK-Artefakt und stellt es als Asset einer GitHub Release bereit.
- Die App prüft auf Wunsch und/oder in einem sinnvollen Abstand, ob eine neuere Version verfügbar ist.
- Bei einem Update zeigt die App die neue Versionsnummer und die Änderungsübersicht (Changelog) an.
- Der Nutzer kann das Update herunterladen oder später installieren.
- Android öffnet anschließend den offiziellen Paket-Installer; die Installation muss vom Nutzer bestätigt werden.
- Die App prüft, dass das heruntergeladene Paket zur erwarteten App gehört. Prüfsumme und Signaturprüfung werden vor dem produktiven Einsatz umgesetzt.
- Updates müssen mit derselben App-ID und demselben Signaturschlüssel wie die installierte Version signiert sein, sonst kann Android sie nicht als normales Update installieren.
- Der private Signaturschlüssel und Passwörter dürfen niemals im Repository liegen. Für Release-Builds werden geschützte GitHub Secrets verwendet.
- Wenn kein Update verfügbar ist oder die Prüfung fehlschlägt, zeigt die App eine verständliche Meldung. Es wird keine erfolgreiche Aktualisierung vorgetäuscht.

### F. CyberGhost-VPN – ausdrücklich später

Dieser Punkt wird zunächst **nicht umgesetzt**. Wir kümmern uns zuerst um den Browser selbst.

Wenn die übrigen Funktionen stabil sind, prüfen wir die direkte Integration von CyberGhost über eine manuelle OpenVPN-Konfiguration.

Geplante spätere Schritte:
1. Manuelle OpenVPN-Konfiguration im CyberGhost-Konto erstellen.
2. OpenVPN-Integration für Android technisch und lizenzrechtlich prüfen.
3. Import der Konfigurationsdatei direkt in der App ermöglichen.
4. Zugangsdaten und Schlüssel ausschließlich lokal und geschützt auf dem Gerät speichern.
5. Keine VPN-Geheimnisse in GitHub, Logs oder Fehlerberichte schreiben.
6. Verbindung, Trennung und Verbindungsabbrüche sicher behandeln.
7. Prüfen, ob Browser-Verkehr bei gewünschtem VPN-Betrieb zuverlässig über den Tunnel läuft.
8. VPN-Status erst dann als „verbunden“ anzeigen, wenn die Verbindung tatsächlich bestätigt wurde.

Bis zur fertigen Integration darf die App nicht behaupten, dass ein VPN aktiv ist. Die CyberGhost-App kann bis dahin unabhängig vom PrivatBrowser verwendet werden.

## 4. Entwicklungsreihenfolge

| Phase | Inhalt | Priorität |
|---|---|---|
| 1 | Grundbrowser und erfolgreicher APK-Build | Jetzt |
| 2 | Navigation, Adressleiste und Fehlerbehandlung | Hoch |
| 3 | Private Sitzung und zuverlässiges Löschen | Hoch |
| 4 | Erweiterter Werbe- und Trackingschutz | Hoch |
| 5 | Bedienkomfort, Einstellungen und Downloads | Mittel |
| 6 | Sitzungs-/Profil-Isolation prüfen und verbessern | Hoch |
| 7 | CyberGhost/OpenVPN integrieren und testen | Später |
| 8 | Gesamttest, Stabilität und Veröffentlichung | Zum Schluss |

## 5. Aktueller technischer Stand

Bereits im Repository vorhanden:
- Android-Projekt mit Kotlin und WebView.
- Helles Grundlayout.
- Startpage als Startseite.
- Adress-/Suchfeld.
- Zurück-, Start-, Neu-laden- und Löschen-Funktion.
- Kleine lokale Sperrliste für einige bekannte Werbe-/Tracking-Hosts.
- Löschen von Cookies, Cache, Verlauf und Website-Speicher.
- Android-Manifest mit Internet-Berechtigung und deaktiviertem App-Backup.
- GitHub-Actions-Workflow zum Erstellen einer Debug-APK.\n- Versionsnummern und In-App-Update sind in der Roadmap aufgenommen, aber noch nicht implementiert.

Noch offen:
- Der erste GitHub-Actions-Build ist fehlgeschlagen. Ursache war ein Kotlin-Fehler bei der Einstellung des einzeiligen Adressfelds.
- Dieser Fehler wurde korrigiert.
- Zusätzlich wurde die Gradle-Version im Workflow angepasst, um sie mit dem Android-Gradle-Plugin kompatibel zu halten.
- Die neuen GitHub-Actions-Läufe müssen erfolgreich abgeschlossen werden. Erst danach gilt der APK-Build als bestätigt.
- Es wurde noch kein vollständiger Funktionstest auf einem Android-Gerät durchgeführt.
- VPN ist noch nicht integriert.

## 6. Build und Test

Bei Änderungen soll GitHub Actions automatisch eine Debug-APK erstellen.

Geplanter Ablauf:
1. Änderung im Quellcode.
2. GitHub Actions startet automatisch.
3. Kotlin-/Gradle-Build muss erfolgreich sein.
4. APK-Artefakt `PrivatBrowser-debug-apk` wird bereitgestellt.
5. APK auf dem Android-Gerät installieren.
6. Webseiten, Navigation, Datenschutzfunktionen und Fehlerfälle testen.
7. Erst nach erfolgreichem Test die nächste größere Funktion ergänzen.

Workflow-Datei: `.github/workflows/android.yml`

## 7. Sicherheits- und Qualitätscheckliste

- [ ] APK baut ohne Fehler.\n- [ ] Versionsnummer und Android-`versionCode` stimmen.\n- [ ] Release-APK wird über GitHub Releases bereitgestellt.\n- [ ] Update-Prüfung erkennt eine neuere Version korrekt.\n- [ ] APK-Prüfsumme und Signatur werden geprüft.\n- [ ] Android-Installer installiert ein Update über die vorhandene Version.\n- [ ] Update funktioniert nur mit gleichbleibender App-ID und Signatur.
- [ ] App startet zuverlässig.
- [ ] HTTPS-Webseiten lassen sich öffnen.
- [ ] Suche und direkte Adresseingabe funktionieren.
- [ ] Zurück, Start und Neu laden funktionieren.
- [ ] Cookies werden wie vorgesehen gelöscht.
- [ ] Cache und Website-Speicher werden wie vorgesehen gelöscht.
- [ ] Keine Passwörter werden von der App gespeichert.
- [ ] Tracker-Sperrliste blockiert nur die vorgesehenen Hosts.
- [ ] Webseiten bleiben trotz Blockliste benutzbar.
- [ ] Keine Zugangsdaten oder privaten Schlüssel im Repository.
- [ ] Verhalten bei Verbindungsabbruch ist nachvollziehbar.
- [ ] VPN wird erst nach erfolgreicher Integration als aktiv angezeigt.

## 8. Was ausdrücklich nicht versprochen wird

- Der Browser macht den Nutzer nicht automatisch anonym.
- Ohne VPN wird die öffentliche IP-Adresse des normalen Internetanschlusses verwendet.
- Eine WebView und eine kleine Sperrliste bieten noch keinen Schutz wie ein vollständig ausgereifter Browser mit umfassendem Tracking-Schutz.
- Das Löschen von Cookies und Cache entfernt nicht automatisch alle Daten, die Webseiten oder andere Apps außerhalb des Browser-Speichers besitzen.
- Die VPN-Funktion ist erst später geplant und aktuell nicht vorhanden.

---

**Arbeitsprinzip:** Wir entwickeln Schritt für Schritt. Zuerst muss der vorhandene Browser zuverlässig bauen und funktionieren. Danach verbessern wir Datenschutz und Bedienung. CyberGhost/OpenVPN kommt erst zum Schluss.
