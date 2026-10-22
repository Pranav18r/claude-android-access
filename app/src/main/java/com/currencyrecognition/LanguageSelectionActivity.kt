package com.currencyrecognition

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * LanguageSelectionActivity / Home Page
 *
 * Repurposed as a Home Page where the user picks English, Hindi, or Malayalam.
 * Stores the choice in SharedPreferences and opens CameraActivity.
 */
class LanguageSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_selection)

        findViewById<Button>(R.id.btnEnglish).setOnClickListener {
            saveLanguageAndProceed("en")
        }

        findViewById<Button>(R.id.btnHindi).setOnClickListener {
            saveLanguageAndProceed("hi")
        }

        findViewById<Button>(R.id.btnMalayalam).setOnClickListener {
            saveLanguageAndProceed("ml")
        }
    }

    private fun saveLanguageAndProceed(langCode: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, langCode)
            .apply()

        startActivity(Intent(this, CameraActivity::class.java))
        // We don't necessarily want to finish() if it's a home page, 
        // but for now we follow previous logic to go to camera.
        finish()
    }

    companion object {
        private const val PREFS_NAME   = "currency_prefs"
        private const val KEY_LANGUAGE = "language"
    }
}
