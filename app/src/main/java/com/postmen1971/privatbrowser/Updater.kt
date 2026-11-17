package com.postmen1971.privatbrowser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Update-Funktion über GitHub Releases.
 *
 * Ablauf: neueste Release abfragen → Version vergleichen → Changelog anzeigen →
 * APK herunterladen → SHA-256-Prüfsumme, Paketname, Versionscode und Signatur prüfen →
 * offiziellen Android-Paket-Installer öffnen (Nutzer muss bestätigen).
 */
class Updater(private val context: Context) {

    companion object {
        const val REPO = "Postmen1971/vpn_browser"
        const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
        const val RELEASES_PAGE = "https://github.com/$REPO/releases"
        private const val CHECK_INTERVAL_MS = 30L * 60 * 1000 // bei jedem Start, höchstens alle 30 Minuten
    }

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val shaUrl: String?,
        val pageUrl: String
    )

    class UpdateException(message: String) : Exception(message)

    private val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)

    var autoCheck: Boolean
        get() = prefs.getBoolean("autoCheck", true)
        set(value) = prefs.edit().putBoolean("autoCheck", value).apply()

    fun needsAutoCheck(): Boolean =
        autoCheck && System.currentTimeMillis() - prefs.getLong("lastCheck", 0L) > CHECK_INTERVAL_MS

    private fun packageInfo(): PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

    val installedVersion: String get() = packageInfo().versionName ?: "?"

    @Suppress("DEPRECATION")
    val installedVersionCode: Long
        get() {
            val info = packageInfo()
            return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        }

    /** Fragt die neueste Release ab (Hintergrund-Thread). Null = keine Release vorhanden. */
    fun fetchLatest(): Release? {
        val connection = URL(API_LATEST).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "PrivatBrowser")
        try {
            val code = connection.responseCode
            if (code == 404) return null
            if (code != 200) throw UpdateException("Update-Server antwortet mit Fehler $code.")
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            var sha: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".apk")) apk = url
                    if (name.endsWith(".sha256")) sha = url
                }
            }
            if (apk == null) throw UpdateException("Die neueste Release enthält keine APK-Datei.")
            return Release(
                version = json.optString("tag_name").removePrefix("v").removePrefix("V"),
                notes = json.optString("body").ifBlank { "Keine Änderungsübersicht angegeben." },
                apkUrl = apk,
                shaUrl = sha,
                pageUrl = json.optString("html_url", RELEASES_PAGE)
            )
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(remote: String, local: String = installedVersion): Boolean {
        val r = remote.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun updateDir(): File {
        val base = context.getExternalFilesDir("updates") ?: File(context.cacheDir, "updates")
        base.mkdirs()
        return base
    }

    /** Lädt die APK herunter und prüft sie (Hintergrund-Thread). */
    fun downloadAndVerify(release: Release, progress: (Int) -> Unit): File {
        val dir = updateDir()
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "PrivatBrowser-${release.version}.apk")

        val expectedSha = release.shaUrl?.let { url ->
            val text = readText(url)
            text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()
        } ?: throw UpdateException("Für diese Version fehlt die Prüfsumme (.sha256). Update aus Sicherheitsgründen abgebrochen.")

        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "PrivatBrowser")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            if (connection.responseCode != 200) throw UpdateException("Download fehlgeschlagen (Fehler ${connection.responseCode}).")
            val total = connection.contentLength
            var done = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total > 0) progress((done * 100 / total).toInt())
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        if (actualSha != expectedSha) {
            target.delete()
            throw UpdateException("Prüfsumme stimmt nicht überein. Die Datei wurde gelöscht und nicht installiert.")
        }
        verifyPackage(target)
        return target
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw UpdateException("Die heruntergeladene Datei ist keine gültige App.")
        if (archive.packageName != context.packageName) {
            apk.delete()
            throw UpdateException("Die Datei gehört zu einer anderen App (${archive.packageName}). Abgebrochen.")
        }
        val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        if (archiveCode <= installedVersionCode) {
            throw UpdateException("Die heruntergeladene Version ist nicht neuer als die installierte.")
        }
        val installed = pm.getPackageInfo(context.packageName, flags)
        val a = certHashes(archive)
        val b = certHashes(installed)
        if (a.isEmpty()) throw UpdateException("Die Signatur der heruntergeladenen Datei konnte nicht gelesen werden.")
        if (a != b) {
            throw UpdateException(
                "Das Update ist mit einem anderen Schlüssel signiert als die installierte App. " +
                    "Android kann es nicht als Update installieren. Bitte die App einmalig deinstallieren " +
                    "und die neue Version von der Release-Seite installieren."
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun certHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        } ?: return emptySet()
        val md = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig -> md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    /** True = Installer geöffnet; false = Nutzer muss zuerst die Installationserlaubnis geben. */
    fun install(activity: Activity, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return false
        }
        val uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(activity, "${activity.packageName}.files", apk)
        } else {
            Uri.fromFile(apk)
        }
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)
        }
        activity.startActivity(intent)
        return true
    }

    private fun readText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "PrivatBrowser")
        try {
            if (connection.responseCode != 200) throw UpdateException("Prüfsumme konnte nicht geladen werden (Fehler ${connection.responseCode}).")
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
