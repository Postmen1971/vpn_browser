package com.postmen1971.privatbrowser

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
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
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clearPrivateData()
        window.statusBarColor = Color.rgb(245, 247, 250)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 250))
            setPadding(16, 12, 16, 8)
        }

        val heading = TextView(this).apply {
            text = "🛡  PrivatBrowser"
            textSize = 22f
            setTextColor(Color.rgb(25, 45, 65))
            setPadding(4, 8, 4, 12)
        }
        root.addView(heading)

        address = EditText(this).apply {
            hint = "Webadresse oder Suchbegriff"
            singleLine = true
            textSize = 16f
            setTextColor(Color.rgb(25, 45, 65))
            setHintTextColor(Color.GRAY)
            setPadding(16, 10, 16, 10)
            setBackgroundColor(Color.WHITE)
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        root.addView(address, LinearLayout.LayoutParams(-1, 52))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        fun addControl(label: String, action: () -> Unit) {
            val button = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 12f
                setOnClickListener { action() }
            }
            controls.addView(button, LinearLayout.LayoutParams(0, 48, 1f))
        }
        addControl("Zurück") { if (webView.canGoBack()) webView.goBack() }
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
            setPadding(4, 4, 4, 8)
        }
        root.addView(vpnInfo)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, 4))

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
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val scheme = url.scheme?.lowercase()
                if (scheme != "https" && scheme != "http") return true
                return false
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
                "https://www.google.com/search?q=" + java.net.URLEncoder.encode(entered, "UTF-8")
            } else if (entered.startsWith("http://") || entered.startsWith("https://")) {
                entered
            } else {
                "https://$entered"
            }
            webView.loadUrl(target)
        }
        address.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openAddress()
                true
            } else false
        }
        if (savedInstanceState == null) {
            webView.loadUrl("https://www.startpage.com/")
        } else {
            webView.restoreState(savedInstanceState)
        }
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
        clearPrivateData()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }
}
