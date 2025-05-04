package com.example.newsapiapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope // Import lifecycleScope if needed for delays
import kotlinx.coroutines.delay // Import delay if needed for delays
import kotlinx.coroutines.launch // Import launch if needed for delays

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Call installSplashScreen() *BEFORE* super.onCreate()
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)


        var keepSplashOnScreen = true
        lifecycleScope.launch {
            delay(2000) // Simulate loading delay *without* blocking UI
            keepSplashOnScreen = false
        }
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }



        setContentView(R.layout.activity_main)

    }
}