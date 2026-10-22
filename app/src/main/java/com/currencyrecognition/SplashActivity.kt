package com.currencyrecognition

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * SplashActivity
 *
 * Shown for 1.5 seconds on launch.
 * Navigates to:
 *  - LanguageSelectionActivity  if no language preference is stored yet
 *  - CameraActivity             otherwise
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val prefs       = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hasLanguage = prefs.contains(KEY_LANGUAGE)

            val next = if (hasLanguage) {
                Intent(this, CameraActivity::class.java)
            } else {
                Intent(this, LanguageSelectionActivity::class.java)
            }

            startActivity(next)
            finish()
        }, SPLASH_DELAY_MS)
    }

    companion object {
        private const val SPLASH_DELAY_MS = 1500L
        private const val PREFS_NAME      = "currency_prefs"
        private const val KEY_LANGUAGE    = "language"
    }
}
