package com.example.newsapiapp // Or your actual package name

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {

    private const val PREFS_NAME = "ThemePrefs"
    private const val KEY_THEME = "prefs.theme"

    // Define constants for the modes for clarity
    const val LIGHT_MODE = AppCompatDelegate.MODE_NIGHT_NO
    const val DARK_MODE = AppCompatDelegate.MODE_NIGHT_YES
    const val FOLLOW_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM // Default usually

    private fun getPreferences(context: Context): SharedPreferences {
        // Using applicationContext is safer here
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedTheme(context: Context): Int {
        // Default to FOLLOW_SYSTEM if nothing is saved
        return getPreferences(context).getInt(KEY_THEME, FOLLOW_SYSTEM)
    }

    fun saveTheme(context: Context, themeMode: Int) {
        getPreferences(context).edit().putInt(KEY_THEME, themeMode).apply()
    }

    fun applyTheme(themeMode: Int) {
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    // Convenience function to apply saved theme
    fun applySavedTheme(context: Context) {
        applyTheme(getSavedTheme(context))
    }
}