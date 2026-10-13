package com.ak.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalMemory(context: Context) {
    private val prefs = context.getSharedPreferences("akai_private", Context.MODE_PRIVATE)
    fun apiKey(): String = prefs.getString("gemini_key", "") ?: ""
    fun setApiKey(key: String) { prefs.edit().putString("gemini_key", key).apply() }
    fun speakReplies(): Boolean = prefs.getBoolean("speak_replies", false)
    fun setSpeakReplies(value: Boolean) { prefs.edit().putBoolean("speak_replies", value).apply() }
    fun addMemory(value: String) {
        val memories = readMemories()
        memories.put(JSONObject().put("text", value).put("created", System.currentTimeMillis()))
        while (memories.length() > 200) memories.remove(0)
        prefs.edit().putString("memories", memories.toString()).apply()
    }
    fun allMemories(): String = (0 until readMemories().length()).joinToString("\n") { "• ${memoryText(readMemories().opt(it))}" }
    fun clearMemories() { prefs.edit().remove("memories").apply() }
    fun selectedApps(): List<SavedApp> {
        val a = try { JSONArray(prefs.getString("selected_apps", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { SavedApp(it.optString("label"), it.optString("package")) } }.filter { it.label.isNotBlank() && it.packageName.isNotBlank() }
    }
    fun defaultMusicPackage(): String = prefs.getString("default_music_package", "") ?: ""
    fun setDefaultMusicPackage(packageName: String) { prefs.edit().putString("default_music_package", packageName).apply() }
    fun saveSelectedApps(apps: List<SavedApp>) {
        val a = JSONArray(); apps.distinctBy { it.packageName }.forEach { a.put(JSONObject().put("label", it.label).put("package", it.packageName)) }
        prefs.edit().putString("selected_apps", a.toString()).apply()
    }
    fun addReminder(id: Int, title: String, triggerAt: Long) {
        val a = remindersJson(); a.put(JSONObject().put("id", id).put("title", title).put("at", triggerAt)); prefs.edit().putString("reminders", a.toString()).apply()
    }
    fun reminders(): List<SavedReminder> {
        val a = remindersJson(); return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { SavedReminder(it.optInt("id"), it.optString("title"), it.optLong("at")) } }.sortedBy { it.at }
    }
    fun removeReminder(id: Int) { val a = JSONArray(); reminders().filter { it.id != id }.forEach { a.put(JSONObject().put("id",it.id).put("title",it.title).put("at",it.at)) }; prefs.edit().putString("reminders",a.toString()).apply() }
    private fun remindersJson(): JSONArray = try { JSONArray(prefs.getString("reminders", "[]")) } catch (_: Exception) { JSONArray() }
    private fun readMemories(): JSONArray = try { JSONArray(prefs.getString("memories", "[]")) } catch (_: Exception) { JSONArray() }
    private fun memoryText(value: Any?): String = when (value) { is JSONObject -> value.optString("text", value.toString()); is String -> value; else -> value?.toString() ?: "" }
}
data class SavedApp(val label: String, val packageName: String)
data class SavedReminder(val id: Int, val title: String, val at: Long)
