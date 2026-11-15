package com.postmen1971.privatbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lesezeichen – nur lokal auf dem Gerät gespeichert, keine Cloud-Synchronisierung. */
class Bookmarks(context: Context) {
    data class Entry(val title: String, val url: String)

    private val prefs = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)

    fun all(): List<Entry> {
        val raw = prefs.getString("list", "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                val obj = array.getJSONObject(it)
                Entry(obj.optString("title"), obj.optString("url"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun contains(url: String): Boolean = all().any { it.url == url }

    fun add(title: String, url: String) {
        if (contains(url)) return
        save(all() + Entry(title.ifBlank { url }, url))
    }

    fun remove(url: String) = save(all().filterNot { it.url == url })

    private fun save(list: List<Entry>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("title", it.title).put("url", it.url)) }
        prefs.edit().putString("list", array.toString()).apply()
    }
}
