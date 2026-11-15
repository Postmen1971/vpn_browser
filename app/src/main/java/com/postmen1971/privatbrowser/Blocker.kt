package com.postmen1971.privatbrowser

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * Werbe- und Tracker-Sperre auf Host-Ebene.
 *
 * - Eingebaute Grundliste (immer aktiv, wenn der Schutz eingeschaltet ist)
 * - Optional heruntergeladene, gepflegte Filterliste (StevenBlack/hosts, Hosts-Format)
 * - Ausnahmen pro Webseite, falls eine Seite sonst nicht funktioniert
 *
 * Ehrlicher Hinweis: Das ist eine Host-Sperrliste, kein vollständiger Werbeblocker.
 */
class Blocker(private val context: Context) {

    companion object {
        const val FILTER_LIST_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        const val FILTER_LIST_NAME = "StevenBlack/hosts (Werbung + Malware)"
        private const val FILE_NAME = "filterlist.txt"
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        val BUILT_IN = setOf(
            // Google Werbung / Analyse
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
            "googletagmanager.com", "googletagservices.com", "adservice.google.com", "adservice.google.de",
            "pagead2.googlesyndication.com", "2mdn.net", "app-measurement.com", "imasdk.googleapis.com",
            // Meta / Facebook Tracking
            "connect.facebook.net", "pixel.facebook.com", "an.facebook.com",
            // Microsoft / Bing Werbung
            "bat.bing.com", "clarity.ms", "ads.microsoft.com",
            // Amazon Werbung
            "amazon-adsystem.com", "aax.amazon-adsystem.com",
            // Werbenetzwerke
            "adnxs.com", "adsrvr.org", "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
            "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "smartadserver.com",
            "yieldlab.net", "adform.net", "adition.com", "media.net", "moatads.com", "teads.tv",
            "advertising.com", "bidswitch.net", "contextweb.com", "indexww.com", "sharethrough.com",
            "spotxchange.com", "33across.com", "lijit.com", "gumgum.com", "triplelift.com",
            "adsafeprotected.com", "doubleverify.com", "serving-sys.com", "zemanta.com", "revcontent.com",
            "mgid.com", "popads.net", "propellerads.com", "adcolony.com", "applovin.com", "unityads.unity3d.com",
            "inmobi.com", "smaato.net", "vungle.com", "chartboost.com", "adskeeper.com", "exoclick.com",
            "stroeerdigitalgroup.de", "ad4mat.de", "uimserv.net", "ioam.de", "xiti.com",
            // Tracking / Analyse
            "scorecardresearch.com", "quantserve.com", "quantcount.com", "hotjar.com", "hotjar.io",
            "mouseflow.com", "fullstory.com", "crazyegg.com", "mixpanel.com", "segment.io", "segment.com",
            "amplitude.com", "newrelic.com", "nr-data.net", "chartbeat.com", "chartbeat.net", "parsely.com",
            "kissmetrics.com", "heap.io", "heapanalytics.com", "optimizely.com", "branch.io",
            "appsflyer.com", "adjust.com", "kochava.com", "omtrdc.net", "demdex.net", "everesttech.net",
            "krxd.net", "bluekai.com", "exelator.com", "agkn.com", "rlcdn.com", "tapad.com",
            "addthis.com", "sharethis.com", "yandex.ru", "mc.yandex.ru", "matomo.cloud",
            "trackcmp.net", "statcounter.com", "histats.com", "etracker.com", "webtrekk.net",
            "wt-safetag.com", "emetriq.de", "nuggad.net", "adalliance.io", "permutive.com", "lotame.com",
            "crwdcntrl.net", "sentry-cdn.com", "bugsnag.com", "tiktok-analytics.com", "analytics.tiktok.com",
            "ads.tiktok.com", "ads.linkedin.com", "px.ads.linkedin.com", "snap.licdn.com", "ads-twitter.com",
            "static.ads-twitter.com", "analytics.twitter.com", "sc-static.net", "tr.snapchat.com",
            "pinimg-ads.com", "ct.pinterest.com"
        )
    }

    private val prefs = context.getSharedPreferences("blocker", Context.MODE_PRIVATE)

    @Volatile
    private var downloaded: Set<String> = emptySet()

    private val exceptionsSet: MutableSet<String> = Collections.synchronizedSet(
        prefs.getStringSet("exceptions", emptySet())!!.toMutableSet()
    )

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var autoUpdate: Boolean
        get() = prefs.getBoolean("autoUpdate", true)
        set(value) = prefs.edit().putBoolean("autoUpdate", value).apply()

    val lastUpdate: Long get() = prefs.getLong("lastUpdate", 0L)

    val downloadedCount: Int get() = downloaded.size

    val exceptions: List<String> get() = synchronized(exceptionsSet) { exceptionsSet.sorted() }

    fun isException(pageHost: String?): Boolean {
        if (pageHost.isNullOrBlank()) return false
        val host = normalize(pageHost)
        return exceptionsSet.contains(host)
    }

    fun setException(pageHost: String, exception: Boolean) {
        val host = normalize(pageHost)
        if (exception) exceptionsSet.add(host) else exceptionsSet.remove(host)
        prefs.edit().putStringSet("exceptions", synchronized(exceptionsSet) { exceptionsSet.toSet() }).apply()
    }

    /** Läuft auf einem WebView-Hintergrund-Thread. */
    fun shouldBlock(requestHost: String?, pageHost: String?): Boolean {
        if (!enabled || requestHost.isNullOrBlank()) return false
        if (isException(pageHost)) return false
        var host = requestHost.lowercase()
        val list = downloaded
        while (true) {
            if (BUILT_IN.contains(host) || list.contains(host)) return true
            val dot = host.indexOf('.')
            if (dot < 0 || dot == host.length - 1) return false
            host = host.substring(dot + 1)
            if (!host.contains('.')) return false
        }
    }

    /** Liest die gespeicherte Filterliste (im Hintergrund aufrufen). */
    fun loadFromDisk() {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) downloaded = parse(file.readLines())
    }

    fun needsAutoUpdate(): Boolean =
        enabled && autoUpdate && System.currentTimeMillis() - lastUpdate > WEEK_MS

    /** Lädt die Filterliste neu herunter (im Hintergrund aufrufen). Gibt die Anzahl Einträge zurück. */
    fun updateFromNetwork(): Int {
        val connection = URL(FILTER_LIST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "PrivatBrowser")
        try {
            if (connection.responseCode != 200) throw IllegalStateException("HTTP ${connection.responseCode}")
            val lines = connection.inputStream.bufferedReader().readLines()
            val parsed = parse(lines)
            if (parsed.size < 1000) throw IllegalStateException("Filterliste unvollständig (${parsed.size} Einträge)")
            val file = File(context.filesDir, FILE_NAME)
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(parsed.joinToString("\n"))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            downloaded = parsed
            prefs.edit().putLong("lastUpdate", System.currentTimeMillis()).apply()
            return parsed.size
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(lines: List<String>): Set<String> {
        val result = HashSet<String>(lines.size)
        for (raw in lines) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            val domain = (if (parts.size >= 2) parts[1] else parts[0]).lowercase()
            if (domain == "localhost" || domain == "0.0.0.0" || domain.endsWith(".local") ||
                domain == "broadcasthost" || !domain.contains('.') || domain.startsWith("ip6-")
            ) continue
            result.add(domain)
        }
        return result
    }

    private fun normalize(host: String): String = host.lowercase().removePrefix("www.")
}
