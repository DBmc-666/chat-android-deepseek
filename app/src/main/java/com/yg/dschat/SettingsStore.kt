package com.yg.dschat

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dschat_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        apiKey = prefs.getString("apiKey", "") ?: "",
        baseUrl = prefs.getString("baseUrl", "https://api.deepseek.com")
            ?.takeIf { it.isNotBlank() } ?: "https://api.deepseek.com",
        model = prefs.getString("model", "deepseek-flash")
            ?.takeIf { it.isNotBlank() } ?: "deepseek-flash",
        webSearch = prefs.getBoolean("webSearch", true),
        deepThinking = prefs.getBoolean("deepThinking", true),
        safetyHigh = prefs.getBoolean("safetyHigh", true),
        autoTitle = prefs.getBoolean("autoTitle", true),
        reminderOn = prefs.getBoolean("reminderOn", true),
        reminderPlace = prefs.getString("reminderPlace", "中国") ?: "中国",
        reminderLang = prefs.getString("reminderLang", "zh-CN") ?: "zh-CN",
        theme = prefs.getString("theme", "system") ?: "system",
        fontScale = prefs.getFloat("fontScale", 1f),
        followSystemFont = prefs.getBoolean("followSystemFont", true),
        voiceAutoSend = prefs.getBoolean("voiceAutoSend", false),
        showUsage = prefs.getBoolean("showUsage", false),
        memoEnabled = prefs.getBoolean("memoEnabled", false),
        memoActive = prefs.getBoolean("memoActive", true),
        compressEnabled = prefs.getBoolean("compressEnabled", true),
        compressThreshold = prefs.getInt("compressThreshold", 300_000),
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("apiKey", s.apiKey)
            .putString("baseUrl", s.baseUrl)
            .putString("model", s.model)
            .putBoolean("webSearch", s.webSearch)
            .putBoolean("deepThinking", s.deepThinking)
            .putBoolean("safetyHigh", s.safetyHigh)
            .putBoolean("autoTitle", s.autoTitle)
            .putBoolean("reminderOn", s.reminderOn)
            .putString("reminderPlace", s.reminderPlace)
            .putString("reminderLang", s.reminderLang)
            .putString("theme", s.theme)
            .putFloat("fontScale", s.fontScale)
            .putBoolean("followSystemFont", s.followSystemFont)
            .putBoolean("voiceAutoSend", s.voiceAutoSend)
            .putBoolean("showUsage", s.showUsage)
            .putBoolean("memoEnabled", s.memoEnabled)
            .putBoolean("memoActive", s.memoActive)
            .putBoolean("compressEnabled", s.compressEnabled)
            .putInt("compressThreshold", s.compressThreshold)
            .apply()
    }
}
