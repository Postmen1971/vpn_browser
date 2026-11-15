package com.postmen1971.privatbrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar

    // Kleine Startliste. Sie wird später durch eine gepflegte Filterliste ersetzt.
    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "adservice.google.com",
        "connect.facebook.net"
    )

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
            initializeBrowser(savedInstanceState)
        } catch (error: Throwable) {
            showStartupError(error)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initializeBrowser(savedInstanceState: Bundle?) {
        clearPrivateData()
        window.statusBarColor = Color.rgb(245, 247, 250)
        window.navigationBarColor = Color.WHITE
        var uiFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = uiFlags

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 250))
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        // Android 15 zeichnet Apps bis unter Status- und Navigationsleiste: Abstand dafür einrechnen
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(dp(12), dp(8) + insets.systemWindowInsetTop, dp(12), dp(4) + insets.systemWindowInsetBottom)
            insets
        }

        val heading = TextView(this).apply {
            text = "🛡  PrivatBrowser"
            textSize = 22f
            setTextColor(Color.rgb(25, 45, 65))
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        root.addView(heading)

        address = EditText(this).apply {
            hint = "Webadresse oder Suchbegriff"
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.rgb(25, 45, 65))
            setHintTextColor(Color.GRAY)
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(Color.WHITE)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val addressRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addressRow.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        val goButton = Button(this).apply {
            text = "Los"
            isAllCaps = false
        }
        addressRow.addView(goButton, LinearLayout.LayoutParams(-2, dp(48)))
        root.addView(addressRow)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        fun addControl(label: String, action: () -> Unit) {
            val button = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 12f
                setOnClickListener { action() }
            }
            controls.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        addControl("Zurück") { if (webView.canGoBack()) webView.goBack() }
        addControl("Start") { webView.loadUrl("https://www.startpage.com/") }
        addControl("Neu laden") { webView.reload() }
        addControl("Löschen") {
            clearPrivateData()
            Toast.makeText(this, "Sitzungsdaten gelöscht", Toast.LENGTH_SHORT).show()
        }
        root.addView(controls)

        val vpnInfo = TextView(this).apply {
            text = "VPN: Noch nicht verbunden – VPN-Anbieter/Konfiguration erforderlich"
            textSize = 12f
            setTextColor(Color.rgb(90, 105, 120))
            setPadding(dp(4), dp(2), dp(4), dp(4))
        }
        root.addView(vpnInfo)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        webView = WebView(this)
        webView.setBackgroundColor(Color.WHITE)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = false
        webView.settings.saveFormData = false
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank() && (url.startsWith("https://") || url.startsWith("http://"))) {
                    address.setText(url)
                    address.setSelection(address.text.length)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val scheme = url.scheme?.lowercase()
                if (scheme != "https" && scheme != "http") return true
                return false
            }

            // Ohne diese Methode beendet Android die ganze App, wenn der WebView-Prozess abstürzt
            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                runOnUiThread { recreate() }
                return true
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) =
                if (request?.url?.host?.lowercase()?.let { host ->
                        blockedHosts.any { host == it || host.endsWith(".$it") }
                    } == true) {
                    android.webkit.WebResourceResponse("text/plain", "utf-8", null)
                } else {
                    super.shouldInterceptRequest(view, request!!)
                }
        }
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        fun openAddress() {
            val entered = address.text.toString().trim()
            if (entered.isBlank()) return
            val target = if (entered.contains(" ") || !entered.contains(".")) {
                "https://www.startpage.com/sp/search?query=" + java.net.URLEncoder.encode(entered, "UTF-8")
            } else if (entered.startsWith("http://") || entered.startsWith("https://")) {
                entered
            } else {
                "https://$entered"
            }
            webView.loadUrl(target)
        }
        // Manche Tastaturen (z. B. Fire-Tablets, Hardware-Tastatur) senden statt "GO" nur Enter
        address.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_NEXT || enter
            ) {
                if (event == null || event.action == KeyEvent.ACTION_DOWN) openAddress()
                true
            } else false
        }
        goButton.setOnClickListener { openAddress() }
        if (savedInstanceState == null) {
            webView.loadUrl("https://www.startpage.com/")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(16))
        }
        root.addView(Button(this).apply {
            text = "Browser trotzdem starten"
            isAllCaps = false
            setOnClickListener { recreate() }
        })
        root.addView(TextView(this).apply {
            text = "PrivatBrowser ist beim letzten Mal abgestürzt.\nBitte sende einen Screenshot dieser Meldung.\n\n$details"
            textSize = 12f
            setTextColor(Color.BLACK)
            setTextIsSelectable(true)
        })
        setContentView(android.widget.ScrollView(this).apply { addView(root) })
    }

    private fun showStartupError(error: Throwable) {
        val details = android.util.Log.getStackTraceString(error)
        val message = TextView(this).apply {
            text = "PrivatBrowser – Startfehler\n\nBitte sende einen Screenshot dieser Meldung.\n\n$details"
            textSize = 13f
            setTextColor(Color.BLACK)
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
        }
        setContentView(message)
    }

    private fun clearPrivateData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        if (::webView.isInitialized) {
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            webView.clearSslPreferences()
            webView.clearMatches()
            webView.evaluateJavascript("localStorage.clear(); sessionStorage.clear();", null)
        }
        android.webkit.WebStorage.getInstance().deleteAllData()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        try {
            clearPrivateData()
            if (::webView.isInitialized) {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}
