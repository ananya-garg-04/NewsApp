package com.example.newsapiapp

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log // Import Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
// Import NavController and AppBarConfiguration
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp // Import the correct navigateUp extension
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.newsapiapp.databinding.ActivityMainBinding
import com.example.newsapiapp.ui.FragmentBreakingNews // Import the Fragment class
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.newsapiapp.ThemeHelper

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Splash Screen Condition ---
        var keepSplashOnScreen = true
        lifecycleScope.launch {
            delay(1500)
            keepSplashOnScreen = false
        }
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }

        // --- Toolbar Setup (Optional) ---
        // Uncomment if using a Toolbar with id 'mainToolbar'
        // try {
        //    setSupportActionBar(binding.mainToolbar)
        //    android.util.Log.i("MainActivity", "Toolbar set as ActionBar.")
        // } catch (e: Exception) {
        //    android.util.Log.w("MainActivity", "Toolbar not found or setup failed: ${e.message}")
        // }

        // --- Navigation Component Setup ---
        try {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
            navController = navHostFragment.navController

            appBarConfiguration = AppBarConfiguration(
                setOf(R.id.fragmentBreakingNews, R.id.fragmentSavedNews)
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            binding.bottomNavigationView.setupWithNavController(navController)

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting up Navigation components: ${e.message}")
        }
    }

    // --- Inflate Options Menu ---
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu) // Inflate main menu

        // Configure Theme Switch
        val themeSwitchItem = menu.findItem(R.id.action_theme_switch)
        if (themeSwitchItem != null) {
            val themeSwitch = themeSwitchItem.actionView as SwitchCompat
            val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            themeSwitch.isChecked = (currentNightMode == Configuration.UI_MODE_NIGHT_YES)
            themeSwitch.setOnCheckedChangeListener { _, isChecked ->
                val newTheme = if (isChecked) ThemeHelper.DARK_MODE else ThemeHelper.LIGHT_MODE
                ThemeHelper.saveTheme(this, newTheme)
                ThemeHelper.applyTheme(newTheme)
                recreate()
            }
        } else {
            android.util.Log.w("MainActivity", "Theme switch item not found in menu.")
        }
        return true
    }

    // --- Handle Clicks on Base Menu Items ---
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle clicks on items in R.menu.menu
        return when (item.itemId) {
            R.id.action_voice_search -> {
                // --- FIND FRAGMENT AND CALL ITS METHOD ---
                Log.d("MainActivity", "Voice Search icon clicked! Attempting find/call FragmentBreakingNews.")

                val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as? NavHostFragment
                val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment

                if (currentFragment is FragmentBreakingNews) {
                    // If it's the correct fragment, call its public method
                    Log.d("MainActivity", "Found FragmentBreakingNews, calling checkAndStartVoiceRecognition.")
                    // Ensure checkAndStartVoiceRecognition() is public in FragmentBreakingNews
                    try {
                        currentFragment.checkAndStartVoiceRecognition() // Call fragment's method
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error calling checkAndStartVoiceRecognition on fragment", e)
                        Toast.makeText(this, "Error starting voice search.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Log if the current fragment isn't the one we expect
                    Log.w("MainActivity", "Current fragment is not FragmentBreakingNews or not found. Cannot start voice search.")
                    Toast.makeText(this, "Voice search not available here.", Toast.LENGTH_SHORT).show()
                }
                true // Indicate the click was handled here
            }
            // Let NavigationUI handle Up button via onSupportNavigateUp
            // Let setupWithNavController handle BottomNav clicks
            else -> super.onOptionsItemSelected(item) // Default handling
        }
    }

    // --- Handle the Up button click using NavigationUI ---
    override fun onSupportNavigateUp(): Boolean {
        // Use the navigateUp extension function
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}