// Make sure this package matches your project structure
package com.example.newsapiapp.mvvm // Or wherever your NewsApplication class is located

import android.app.Application
// Import your ThemeHelper - Adjust the path if you placed ThemeHelper elsewhere
import com.example.newsapiapp.ThemeHelper // Or com.example.newsapiapp.utils.ThemeHelper etc.
import dagger.hilt.android.HiltAndroidApp // *** ADD THIS IF YOU ARE USING HILT ***
import kotlin.text.Typography.dagger

// *** ADD @HiltAndroidApp annotation IF YOU ARE USING HILT ***
@HiltAndroidApp // <-- Add this line only if you use Hilt for dependency injection
class NewsApplication : Application() {

    override fun onCreate() {
        super.onCreate() // Always call the superclass method first

        // --- THIS IS THE LINE YOU NEED TO ADD ---
        // Apply the saved theme preference when the application starts
        ThemeHelper.applySavedTheme(this)
        // --- END OF ADDED LINE ---

        // Any other application-level initializations you might have go here...
    }
}