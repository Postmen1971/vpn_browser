package com.postmen1971.privatbrowser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {

    companion object {
        const val START_PAGE = "https://www.startpage.com/"
        const val SEARCH_URL = "https://www.startpage.com/sp/search?query="
        private const val REQ_STORAGE = 11
        private const val REQ_FILE = 12

        private val BG = Color.rgb(245, 247, 250)
        private val TEXT = Color.rgb(25, 45, 65)
        private val MUTED = Color.rgb(90, 105, 120)
        private val ACCENT = Color.rgb(30, 100, 200)
        private val DANGER = Color.rgb(190, 40, 40)
    }

    /** Ein Tab = eine eigene WebView. Hinweis: Cookies teilen sich alle Tabs (WebView-Einschränkung). */
    inner class Tab(val webView: WebView) {
        @Volatile var pageHost: String? = null
        @Volatile var blockedCount = 0
        var title: String = "Neuer Tab"
        var errorUrl: String? = null
        var currentUrl: String? = null
    }

    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var container: FrameLayout
    private lateinit var tabButton: Button
    private lateinit var backButton: Button
    private lateinit var forwardButton: Button

    private lateinit var blocker: Blocker
    private lateinit var bookmarks: Bookmarks
    private lateinit var updater: Updater

    private val tabs = mutableListOf<Tab>()
    private var currentIndex = 0
    private val current: Tab? get() = tabs.getOrNull(currentIndex)

    private var pendingDownload: (() -> Unit)? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var pendingUpdateFile: java.io.File? = null

    private val settingsPrefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private var blockThirdPartyCookies: Boolean
        get() = settingsPrefs.getBoolean("block3pc", true)
        set(value) = settingsPrefs.edit().putBoolean("block3pc", value).apply()

    // ------------------------------------------------------------------ Start

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashRecorder()
        val previousCrash = getSharedPreferences("diag", MODE_PRIVATE).getString("lastCrash", null)
        if (previousCrash != null) {
            getSharedPreferences("diag", MODE_PRIVATE).edit().remove("lastCrash").commit()
            showCrashReport(previousCrash)
            return
        }
        try {
            initializeBrowser()
        } catch (error: Throwable) {
            showStartupError(error)
        }
    }

    private fun initializeBrowser() {
        blocker = Blocker(this)
        bookmarks = Bookmarks(this)
        updater = Updater(this)
        wipeStorage()
        buildUi()

        val launchUrl = intent?.dataString?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        createTab(launchUrl ?: START_PAGE)

        // Filterliste laden und bei Bedarf wöchentlich aktualisieren
        Thread {
            try {
                blocker.loadFromDisk()
                if (blocker.needsAutoUpdate()) blocker.updateFromNetwork()
            } catch (_: Throwable) {
            }
        }.start()

        // Update-Prüfung (höchstens einmal täglich, abschaltbar)
        if (updater.needsAutoCheck()) checkForUpdates(silent = true)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val url = intent?.dataString ?: return
        if ((url.startsWith("http://") || url.startsWith("https://")) && ::container.isInitialized) createTab(url)
    }

    // ------------------------------------------------------------------ Oberfläche

    private fun buildUi() {
        window.statusBarColor = BG
        window.navigationBarColor = Color.WHITE
        var uiFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = uiFlags

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(dp(8), dp(6) + insets.systemWindowInsetTop, dp(8), insets.systemWindowInsetBottom)
            insets
        }
        root.setPadding(dp(8), dp(6), dp(8), 0)

        // Adresszeile
        address = EditText(this).apply {
            hint = "Webadresse oder Suchbegriff"
            setSingleLine(true)
            textSize = 16f
            setTextColor(TEXT)
            setHintTextColor(Color.GRAY)
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(Color.WHITE)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSelectAllOnFocus(true)
        }
        address.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_NEXT || enter
            ) {
                if (event == null || event.action == KeyEvent.ACTION_DOWN) openAddress()
                true
            } else false
        }
        val addressRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addressRow.addView(address, LinearLayout.LayoutParams(0, dp(46), 1f))
        addressRow.addView(smallButton("Los") { openAddress() }, LinearLayout.LayoutParams(dp(56), dp(46)))
        root.addView(addressRow)

        // Bedienleiste
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, 0)
        }
        fun add(button: Button, weight: Float = 1f) =
            controls.addView(button, LinearLayout.LayoutParams(0, dp(44), weight))
        backButton = smallButton("◀") { current?.webView?.let { if (it.canGoBack()) it.goBack() } }
        forwardButton = smallButton("▶") { current?.webView?.let { if (it.canGoForward()) it.goForward() } }
        add(backButton)
        add(forwardButton)
        add(smallButton("Start") { loadInCurrent(START_PAGE) }, 1.3f)
        add(smallButton("Neu") { reloadCurrent() }, 1.2f)
        add(smallButton("☆") { toggleBookmark() })
        tabButton = smallButton("1") { showTabs() }
        add(tabButton)
        val menuButton = smallButton("☰") {}
        menuButton.setOnClickListener { showMenu(it) }
        add(menuButton)
        add(smallButton("Löschen", DANGER) { confirmClearSession() }, 1.7f)
        root.addView(controls)

        status = TextView(this).apply {
            textSize = 12f
            setTextColor(MUTED)
            setPadding(dp(4), dp(2), dp(4), dp(3))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        root.addView(status)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.INVISIBLE
            max = 100
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        container = FrameLayout(this)
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun smallButton(label: String, color: Int = TEXT, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(color)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { action() }
        }

    private fun updateChrome() {
        val tab = current ?: return
        if (!address.hasFocus()) {
            address.setText(tab.errorUrl ?: tab.currentUrl ?: "")
        }
        backButton.isEnabled = tab.webView.canGoBack()
        forwardButton.isEnabled = tab.webView.canGoForward()
        tabButton.text = tabs.size.toString()

        val url = tab.errorUrl ?: tab.currentUrl ?: ""
        val security = when {
            url.startsWith("https://") -> "🔒 Verschlüsselt"
            url.startsWith("http://") -> "⚠ Unverschlüsselt"
            else -> ""
        }
        val shield = when {
            !blocker.enabled -> "Werbeschutz aus"
            blocker.isException(tab.pageHost) -> "Werbeschutz für diese Seite aus"
            else -> "🛡 ${tab.blockedCount} blockiert"
        }
        status.text = listOf(security, shield, "VPN: nicht aktiv").filter { it.isNotEmpty() }.joinToString("  ·  ")
    }

    // ------------------------------------------------------------------ Tabs

    @SuppressLint("SetJavaScriptEnabled")
    private fun createTab(url: String?): Tab {
        val webView = WebView(this)
        val tab = Tab(webView)
        webView.setBackgroundColor(Color.WHITE)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            saveFormData = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, !blockThirdPartyCookies)

        webView.webChromeClient = ChromeClient(tab)
        webView.webViewClient = BrowserClient(tab)
        webView.setDownloadListener { dlUrl, userAgent, contentDisposition, mimeType, _ ->
            confirmDownload(dlUrl, userAgent, contentDisposition, mimeType)
        }

        tabs.add(tab)
        container.addView(webView, FrameLayout.LayoutParams(-1, -1))
        switchTo(tabs.size - 1)
        if (url != null) webView.loadUrl(url)
        return tab
    }

    private fun switchTo(index: Int) {
        if (index !in tabs.indices) return
        currentIndex = index
        tabs.forEachIndexed { i, t -> t.webView.visibility = if (i == index) View.VISIBLE else View.GONE }
        progress.visibility = View.INVISIBLE
        address.clearFocus()
        updateChrome()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val tab = tabs.removeAt(index)
        destroyWebView(tab.webView)
        if (tabs.isEmpty()) {
            createTab(START_PAGE)
            return
        }
        val next = when {
            index < currentIndex -> currentIndex - 1
            index == currentIndex -> minOf(index, tabs.size - 1)
            else -> currentIndex
        }
        switchTo(next)
    }

    private fun destroyWebView(webView: WebView) {
        try {
            webView.stopLoading()
            container.removeView(webView)
            webView.loadUrl("about:blank")
            webView.destroy()
        } catch (_: Throwable) {
        }
    }

    private fun showTabs() {
        val labels = tabs.mapIndexed { i, t ->
            (if (i == currentIndex) "● " else "    ") + t.title.ifBlank { t.currentUrl ?: "Neuer Tab" }
        }
        val list = ListView(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Tabs (${tabs.size})")
            .setView(list)
            .setPositiveButton("Neuer Tab") { _, _ -> createTab(START_PAGE) }
            .setNegativeButton("Aktuellen schließen") { _, _ -> closeTab(currentIndex) }
            .setNeutralButton("Abbrechen", null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            switchTo(position)
            dialog.dismiss()
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            closeTab(position)
            dialog.dismiss()
            true
        }
        dialog.show()
        Toast.makeText(this, "Tipp: Lange auf einen Tab drücken schließt ihn.", Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ Navigation

    private fun openAddress() {
        val entered = address.text.toString().trim()
        if (entered.isBlank()) return
        val target = when {
            entered.startsWith("http://") || entered.startsWith("https://") -> entered
            entered.contains(" ") || !entered.contains(".") -> SEARCH_URL + URLEncoder.encode(entered, "UTF-8")
            else -> "https://$entered"
        }
        hideKeyboard()
        address.clearFocus()
        loadInCurrent(target)
    }

    private fun loadInCurrent(url: String) {
        val tab = current ?: createTab(null)
        tab.errorUrl = null
        tab.webView.loadUrl(url)
    }

    private fun reloadCurrent() {
        val tab = current ?: return
        val failed = tab.errorUrl
        if (failed != null) {
            tab.errorUrl = null
            tab.webView.loadUrl(failed)
        } else {
            tab.webView.reload()
        }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)
    }

    /** Entscheidet, was mit einem angeklickten Link passiert. true = WebView lädt ihn NICHT selbst. */
    private fun handleLink(tab: Tab, url: String): Boolean {
        val uri = Uri.parse(url)
        when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                tab.errorUrl = null
                return false
            }
            "intent" -> {
                try {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        component = null
                        selector = null
                    }
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    if (intent.resolveActivity(packageManager) != null) {
                        askOpenExternal(intent, "eine andere App")
                    } else if (fallback != null && (fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                        tab.webView.loadUrl(fallback)
                    } else {
                        toast("Für diesen Link ist keine passende App installiert.")
                    }
                } catch (_: Exception) {
                    toast("Dieser Link kann nicht geöffnet werden.")
                }
                return true
            }
            "mailto", "tel", "sms", "smsto", "geo", "market", "whatsapp", "tg", "maps" -> {
                val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
                val what = when (uri.scheme?.lowercase()) {
                    "mailto" -> "die E-Mail-App"
                    "tel" -> "die Telefon-App"
                    "sms", "smsto" -> "die SMS-App"
                    "geo", "maps" -> "die Karten-App"
                    "market" -> "den App-Store"
                    else -> "eine andere App"
                }
                askOpenExternal(intent, what)
                return true
            }
            else -> {
                // file:, content:, javascript:, data: usw. werden aus Sicherheitsgründen nicht geöffnet
                toast("Nicht unterstützter Link-Typ – aus Sicherheitsgründen blockiert.")
                return true
            }
        }
    }

    private fun askOpenExternal(intent: Intent, what: String) {
        AlertDialog.Builder(this)
            .setTitle("Link außerhalb des Browsers öffnen?")
            .setMessage("Dieser Link möchte $what öffnen. Dabei verlässt du den PrivatBrowser.")
            .setPositiveButton("Öffnen") { _, _ ->
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    toast("Keine passende App gefunden.")
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    // ------------------------------------------------------------------ WebView-Clients

    private inner class BrowserClient(private val tab: Tab) : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            return handleLink(tab, url)
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url == null) return false
            return handleLink(tab, url)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                val host = Uri.parse(url).host
                if (host != tab.pageHost) tab.blockedCount = 0
                tab.pageHost = host
                tab.currentUrl = url
                tab.errorUrl = null
            }
            if (tab == current) updateChrome()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) tab.currentUrl = url
            if (tab == current) updateChrome()
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) tab.currentUrl = url
            if (tab == current) updateChrome()
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            if (request == null || request.isForMainFrame) return null
            val host = request.url?.host
            if (blocker.shouldBlock(host, tab.pageHost)) {
                tab.blockedCount++
                if (tab == current) runOnUiThread { updateChrome() }
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (Build.VERSION.SDK_INT >= 23 && request != null && request.isForMainFrame) {
                showErrorPage(tab, request.url.toString(), error?.errorCode ?: WebViewClient.ERROR_UNKNOWN)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
            if (Build.VERSION.SDK_INT < 23 && failingUrl != null) showErrorPage(tab, failingUrl, errorCode)
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            // Unsichere Zertifikate werden nie akzeptiert.
            handler?.cancel()
            val url = error?.url ?: return
            if (url == tab.currentUrl || url == view?.url || tab.currentUrl == null) {
                showErrorPage(tab, url, WebViewClient.ERROR_FAILED_SSL_HANDSHAKE)
            }
        }

        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
            // Ohne diese Methode beendet Android die ganze App, wenn der WebView-Prozess abstürzt.
            runOnUiThread {
                val index = tabs.indexOf(tab)
                if (index >= 0) {
                    val url = tab.currentUrl
                    tabs.removeAt(index)
                    try { container.removeView(tab.webView); tab.webView.destroy() } catch (_: Throwable) {}
                    createTab(url ?: START_PAGE)
                    toast("Die Seite ist abgestürzt und wurde neu geladen.")
                }
            }
            return true
        }
    }

    private inner class ChromeClient(private val tab: Tab) : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (tab != current) return
            progress.progress = newProgress
            progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.INVISIBLE
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (!title.isNullOrBlank()) tab.title = title
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            callback?.invoke(origin, false, false) // Standort wird nicht freigegeben
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.deny() // Kamera/Mikrofon werden nicht freigegeben
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            return try {
                startActivityForResult(fileChooserParams!!.createIntent(), REQ_FILE)
                true
            } catch (_: Exception) {
                fileCallback = null
                toast("Keine App zur Dateiauswahl gefunden.")
                false
            }
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (customView != null || view == null) {
                callback?.onCustomViewHidden()
                return
            }
            customView = view
            customViewCallback = callback
            root.visibility = View.GONE
            (window.decorView as ViewGroup).addView(view, FrameLayout.LayoutParams(-1, -1))
        }

        override fun onHideCustomView() = exitFullscreen()
    }

    private fun exitFullscreen() {
        val view = customView ?: return
        (window.decorView as ViewGroup).removeView(view)
        customView = null
        root.visibility = View.VISIBLE
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    private fun showErrorPage(tab: Tab, failingUrl: String, code: Int) {
        val (title, text) = when (code) {
            WebViewClient.ERROR_HOST_LOOKUP -> "Seite nicht gefunden" to
                "Die Adresse konnte nicht gefunden werden. Prüfe die Schreibweise und ob das Gerät mit dem Internet verbunden ist."
            WebViewClient.ERROR_CONNECT -> "Keine Verbindung" to
                "Der Server konnte nicht erreicht werden. Vielleicht besteht keine Internetverbindung oder die Seite ist gerade nicht erreichbar."
            WebViewClient.ERROR_TIMEOUT -> "Zeitüberschreitung" to
                "Die Seite hat zu lange nicht geantwortet. Versuche es gleich noch einmal."
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "Unsichere Verbindung blockiert" to
                "Das Sicherheitszertifikat dieser Seite ist ungültig oder nicht vertrauenswürdig. Zu deinem Schutz wurde die Seite nicht geöffnet."
            WebViewClient.ERROR_UNSUPPORTED_SCHEME -> "Nicht unterstützt" to
                "Diese Art von Adresse kann der Browser nicht öffnen."
            WebViewClient.ERROR_TOO_MANY_REQUESTS -> "Zu viele Anfragen" to
                "Die Seite hat zu viele Anfragen erhalten. Bitte später erneut versuchen."
            else -> "Seite nicht erreichbar" to
                "Beim Laden der Seite ist ein Fehler aufgetreten (Code $code)."
        }
        val safeUrl = TextUtils.htmlEncode(failingUrl)
        val html = """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>body{font-family:sans-serif;background:#f5f7fa;color:#192d41;padding:24px;line-height:1.5}
            h2{margin-top:32px}.url{color:#5a6978;word-break:break-all;font-size:14px}
            a.btn{display:inline-block;margin-top:20px;padding:12px 20px;background:#1e64c8;color:#fff;border-radius:8px;text-decoration:none}</style>
            </head><body><h2>$title</h2><p>$text</p><p class="url">$safeUrl</p>
            <a class="btn" href="$safeUrl">Erneut versuchen</a></body></html>
        """.trimIndent()
        tab.webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        tab.errorUrl = failingUrl
        tab.title = title
        if (tab == current) updateChrome()
    }

    // ------------------------------------------------------------------ Menü

    private fun showMenu(anchor: View) {
        val tab = current
        val popup = PopupMenu(this, anchor)
        val m = popup.menu
        m.add(0, 1, 0, "Neuer Tab")
        m.add(0, 2, 1, "Lesezeichen")
        val url = tab?.currentUrl
        if (url != null && tab?.errorUrl == null) {
            m.add(0, 3, 2, if (bookmarks.contains(url)) "Lesezeichen entfernen" else "Lesezeichen hinzufügen")
        }
        val host = tab?.pageHost
        if (host != null && blocker.enabled) {
            m.add(0, 4, 3, if (blocker.isException(host)) "Werbeschutz für diese Seite einschalten" else "Werbeschutz für diese Seite ausschalten")
        }
        m.add(0, 5, 4, "Downloads")
        m.add(0, 6, 5, "Sitzungsdaten löschen")
        m.add(0, 7, 6, "Private Sitzung beenden und alles löschen")
        m.add(0, 8, 7, "Einstellungen")
        m.add(0, 9, 8, "Nach Updates suchen")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> createTab(START_PAGE)
                2 -> showBookmarks()
                3 -> toggleBookmark()
                4 -> host?.let {
                    blocker.setException(it, !blocker.isException(it))
                    reloadCurrent()
                    toast(if (blocker.isException(it)) "Ausnahme für $it gespeichert" else "Werbeschutz für $it wieder aktiv")
                }
                5 -> openDownloads()
                6 -> confirmClearSession()
                7 -> confirmEndSession()
                8 -> showSettings()
                9 -> checkForUpdates(silent = false)
            }
            true
        }
        popup.show()
    }

    // ------------------------------------------------------------------ Lesezeichen

    private fun toggleBookmark() {
        val tab = current ?: return
        val url = tab.currentUrl
        if (url == null || tab.errorUrl != null) {
            toast("Keine Seite geöffnet.")
            return
        }
        if (bookmarks.contains(url)) {
            bookmarks.remove(url)
            toast("Lesezeichen entfernt")
        } else {
            bookmarks.add(tab.title, url)
            toast("Lesezeichen gespeichert (nur auf diesem Gerät)")
        }
    }

    private fun showBookmarks() {
        val entries = bookmarks.all()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Lesezeichen")
                .setMessage("Noch keine Lesezeichen. Tippe auf ☆, um die aktuelle Seite zu speichern.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val list = ListView(this)
        list.adapter = object : ArrayAdapter<Bookmarks.Entry>(this, android.R.layout.simple_list_item_2, android.R.id.text1, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                v.findViewById<TextView>(android.R.id.text1).text = entries[position].title
                v.findViewById<TextView>(android.R.id.text2).text = entries[position].url
                return v
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Lesezeichen")
            .setView(list)
            .setNegativeButton("Schließen", null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            loadInCurrent(entries[position].url)
            dialog.dismiss()
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val entry = entries[position]
            AlertDialog.Builder(this)
                .setTitle("Lesezeichen löschen?")
                .setMessage(entry.title)
                .setPositiveButton("Löschen") { _, _ ->
                    bookmarks.remove(entry.url)
                    dialog.dismiss()
                    showBookmarks()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
            true
        }
        dialog.show()
    }

    // ------------------------------------------------------------------ Downloads

    private fun confirmDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("Dieser Download-Typ wird nicht unterstützt.")
            return
        }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        AlertDialog.Builder(this)
            .setTitle("Datei herunterladen?")
            .setMessage("$fileName\n\nWird im Ordner „Download“ des Geräts gespeichert.")
            .setPositiveButton("Herunterladen") { _, _ ->
                val start = { startDownload(url, userAgent, mimeType, fileName) }
                if (Build.VERSION.SDK_INT in 23..28 &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingDownload = start
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
                } else {
                    start()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun startDownload(url: String, userAgent: String?, mimeType: String?, fileName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("PrivatBrowser-Download")
                if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast("Download gestartet: $fileName")
        } catch (e: Exception) {
            toast("Download nicht möglich: ${e.message}")
        }
    }

    private fun openDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            toast("Die Download-Übersicht ist auf diesem Gerät nicht verfügbar. Dateien liegen im Ordner „Download“.")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) pendingDownload?.invoke()
            else toast("Ohne Speicher-Berechtigung kann nicht heruntergeladen werden.")
            pendingDownload = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    // ------------------------------------------------------------------ Private Sitzung

    private fun confirmClearSession() {
        AlertDialog.Builder(this)
            .setTitle("Sitzungsdaten löschen?")
            .setMessage("Alle Tabs werden geschlossen. Cookies, Cache, Verlauf, Formulardaten und Website-Speicher werden gelöscht. Du wirst auf allen Webseiten abgemeldet.")
            .setPositiveButton("Alles löschen") { _, _ ->
                clearSession()
                createTab(START_PAGE)
                toast("Sitzungsdaten gelöscht")
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun confirmEndSession() {
        AlertDialog.Builder(this)
            .setTitle("Private Sitzung beenden?")
            .setMessage("Alle Tabs werden geschlossen, alle Browserdaten gelöscht und der PrivatBrowser beendet.\n\nLesezeichen und Einstellungen bleiben erhalten.")
            .setPositiveButton("Beenden und löschen") { _, _ ->
                clearSession()
                finishAndRemoveTask()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /**
     * Stoppt zuerst alle Seiten (damit keine laufende Seite weiter Daten schreibt),
     * schließt alle Tabs und löscht danach alle Browserdaten.
     */
    private fun clearSession() {
        exitFullscreen()
        tabs.forEach { it.webView.stopLoading() }
        tabs.forEach { tab ->
            try {
                tab.webView.evaluateJavascript("try{localStorage.clear();sessionStorage.clear();}catch(e){}", null)
                tab.webView.clearCache(true)
                tab.webView.clearHistory()
                tab.webView.clearFormData()
                tab.webView.clearSslPreferences()
                tab.webView.clearMatches()
            } catch (_: Throwable) {
            }
            destroyWebView(tab.webView)
        }
        tabs.clear()
        currentIndex = 0
        wipeStorage()
    }

    private fun wipeStorage() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().removeSessionCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            @Suppress("DEPRECATION")
            android.webkit.WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword()
            @Suppress("DEPRECATION")
            android.webkit.WebViewDatabase.getInstance(this).clearFormData()
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------ Einstellungen

    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        fun header(text: String) = layout.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ACCENT)
            setPadding(0, dp(14), 0, dp(4))
        })
        fun info(text: String) = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(MUTED)
            setPadding(0, dp(2), 0, dp(4))
        }.also { layout.addView(it) }
        fun switch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = layout.addView(Switch(this).apply {
            text = label
            textSize = 14f
            setTextColor(TEXT)
            isChecked = checked
            setPadding(0, dp(6), 0, dp(6))
            setOnCheckedChangeListener { _, value -> onChange(value) }
        })
        fun button(label: String, action: () -> Unit) = layout.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-2, -2))

        header("Version")
        info("PrivatBrowser ${updater.installedVersion} (Build ${updater.installedVersionCode})")
        switch("Täglich automatisch nach Updates suchen", updater.autoCheck) { updater.autoCheck = it }
        button("Jetzt nach Updates suchen") { checkForUpdates(silent = false) }

        header("Werbe- und Trackingschutz")
        switch("Werbe-/Tracker-Hosts blockieren", blocker.enabled) {
            blocker.enabled = it
            updateChrome()
        }
        val listInfo = info(filterListText())
        switch("Filterliste wöchentlich automatisch aktualisieren", blocker.autoUpdate) { blocker.autoUpdate = it }
        button("Filterliste jetzt aktualisieren") {
            listInfo.text = "Filterliste wird geladen …"
            Thread {
                val result = try {
                    "Aktualisiert: ${blocker.updateFromNetwork()} Einträge"
                } catch (e: Exception) {
                    "Aktualisierung fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}"
                }
                runOnUiThread {
                    listInfo.text = filterListText()
                    toast(result)
                }
            }.start()
        }
        button("Ausnahmen verwalten (${blocker.exceptions.size})") { showExceptions() }
        info("Hinweis: Das ist eine Sperrliste auf Ebene der Server-Namen. Sie reduziert Werbung und Tracking, ist aber kein vollständiger Werbeblocker.")

        header("Datenschutz")
        switch("Cookies von Drittanbietern blockieren", blockThirdPartyCookies) { value ->
            blockThirdPartyCookies = value
            tabs.forEach { CookieManager.getInstance().setAcceptThirdPartyCookies(it.webView, !value) }
        }
        info("Falls eine Anmeldung nicht funktioniert, kann das Ausschalten helfen.")
        info(
            "• Cookies, Cache und Website-Speicher werden bei jedem Start und beim Beenden gelöscht.\n" +
                "• Die App speichert keine Passwörter und keine Formulardaten.\n" +
                "• Standort, Kamera und Mikrofon werden für Webseiten nicht freigegeben.\n" +
                "• Unsichere Zertifikate werden immer abgelehnt.\n" +
                "• Alle Tabs teilen sich dieselben Cookies – es gibt noch keine getrennten Profile.\n" +
                "• Der Browser macht dich nicht anonym: Ohne VPN ist deine normale IP-Adresse sichtbar."
        )

        header("VPN")
        info("Nicht aktiv. Die VPN-Funktion (CyberGhost/OpenVPN) ist für später geplant und noch nicht eingebaut. Bis dahin kannst du die CyberGhost-App separat verwenden.")

        val scroll = ScrollView(this).apply { addView(layout) }
        AlertDialog.Builder(this)
            .setTitle("Einstellungen")
            .setView(scroll)
            .setPositiveButton("Schließen") { _, _ -> updateChrome() }
            .show()
    }

    private fun filterListText(): String {
        val builtIn = "Eingebaute Liste: ${Blocker.BUILT_IN.size} Einträge"
        return if (blocker.downloadedCount > 0) {
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(blocker.lastUpdate))
            "$builtIn\n${Blocker.FILTER_LIST_NAME}: ${blocker.downloadedCount} Einträge, Stand $date"
        } else {
            "$builtIn\nErweiterte Filterliste: noch nicht geladen"
        }
    }

    private fun showExceptions() {
        val items = blocker.exceptions
        if (items.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Ausnahmen")
                .setMessage("Keine Ausnahmen. Über das Menü ☰ kannst du den Werbeschutz für eine einzelne Seite ausschalten, falls sie sonst nicht funktioniert.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Ausnahmen – antippen zum Entfernen")
            .setItems(items.toTypedArray()) { _, which ->
                blocker.setException(items[which], false)
                toast("${items[which]} entfernt")
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    // ------------------------------------------------------------------ Updates

    private fun checkForUpdates(silent: Boolean) {
        if (!silent) toast("Suche nach Updates …")
        Thread {
            try {
                val release = updater.fetchLatest()
                runOnUiThread {
                    when {
                        release == null -> if (!silent) message("Keine Updates", "Es wurde noch keine Version veröffentlicht.")
                        updater.isNewer(release.version) -> showUpdateDialog(release)
                        else -> if (!silent) message("Kein Update verfügbar", "Du verwendest die neueste Version (${updater.installedVersion}).")
                    }
                }
            } catch (e: Exception) {
                if (!silent) runOnUiThread {
                    message("Update-Prüfung fehlgeschlagen", e.message ?: "Bitte Internetverbindung prüfen und später erneut versuchen.")
                }
            }
        }.start()
    }

    private fun showUpdateDialog(release: Updater.Release) {
        AlertDialog.Builder(this)
            .setTitle("Update verfügbar: Version ${release.version}")
            .setMessage("Installiert: ${updater.installedVersion}\n\nÄnderungen:\n${release.notes}")
            .setPositiveButton("Herunterladen") { _, _ -> downloadUpdate(release) }
            .setNegativeButton("Später", null)
            .show()
    }

    private fun downloadUpdate(release: Updater.Release) {
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Update wird geladen …")
            .setView(bar)
            .setCancelable(false)
            .show()
        Thread {
            try {
                val file = updater.downloadAndVerify(release) { p -> runOnUiThread { bar.progress = p } }
                runOnUiThread {
                    dialog.dismiss()
                    pendingUpdateFile = file
                    AlertDialog.Builder(this)
                        .setTitle("Update geprüft")
                        .setMessage("Prüfsumme und Signatur sind in Ordnung. Android fragt jetzt, ob das Update installiert werden soll.")
                        .setPositiveButton("Installieren") { _, _ -> installPendingUpdate() }
                        .setNegativeButton("Später", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    message("Update nicht möglich", e.message ?: "Unbekannter Fehler")
                }
            }
        }.start()
    }

    private fun installPendingUpdate() {
        val file = pendingUpdateFile ?: return
        try {
            if (!updater.install(this, file)) {
                toast("Bitte „Apps aus dieser Quelle zulassen“ aktivieren und dann zurückkehren.")
            }
        } catch (e: Exception) {
            message("Installation nicht möglich", e.message ?: "Der Paket-Installer konnte nicht geöffnet werden.")
        }
    }

    override fun onResume() {
        super.onResume()
        // Nach der Erlaubnis "Unbekannte Apps installieren" die Installation fortsetzen
        val file = pendingUpdateFile
        if (file != null && Build.VERSION.SDK_INT >= 26 && packageManager.canRequestPackageInstalls() && file.exists()) {
            pendingUpdateFile = null
            try { updater.install(this, file) } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun message(title: String, text: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (customView != null) {
            exitFullscreen()
            return
        }
        val tab = current
        when {
            tab != null && tab.webView.canGoBack() -> {
                tab.errorUrl = null
                tab.webView.goBack()
            }
            tabs.size > 1 -> closeTab(currentIndex)
            else -> super.onBackPressed()
        }
    }

    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                getSharedPreferences("diag", MODE_PRIVATE).edit()
                    .putString("lastCrash", android.util.Log.getStackTraceString(error)).commit()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun showCrashReport(details: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(16))
        }
        layout.addView(Button(this).apply {
            text = "Browser trotzdem starten"
            isAllCaps = false
            setOnClickListener { recreate() }
        })
        layout.addView(TextView(this).apply {
            text = "PrivatBrowser ist beim letzten Mal abgestürzt.\nBitte sende einen Screenshot dieser Meldung.\n\n$details"
            textSize = 12f
            setTextColor(Color.BLACK)
            setTextIsSelectable(true)
        })
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun showStartupError(error: Throwable) {
        val details = android.util.Log.getStackTraceString(error)
        val message = TextView(this).apply {
            text = "PrivatBrowser – Startfehler\n\nBitte sende einen Screenshot dieser Meldung.\n\n$details"
            textSize = 13f
            setTextColor(Color.BLACK)
            setPadding(dp(16), dp(32), dp(16), dp(16))
            setTextIsSelectable(true)
            gravity = Gravity.START
        }
        setContentView(ScrollView(this).apply { addView(message) })
    }

    override fun onDestroy() {
        if (isFinishing && ::container.isInitialized) {
            try {
                clearSession()
            } catch (_: Throwable) {
            }
        }
        super.onDestroy()
    }
}
