package com.currencyrecognition

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

/**
 * SettingsActivity
 *
 * Allows the user to change:
 *  - Language (English / Hindi / Malayalam)
 *  - Speech rate (Slow / Normal / Fast)
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // ── Language ──────────────────────────────────────────────────────
        val rgLanguage  = findViewById<RadioGroup>(R.id.rgLanguage)
        val rbEnglish   = findViewById<RadioButton>(R.id.rbEnglish)
        val rbHindi     = findViewById<RadioButton>(R.id.rbHindi)
        val rbMalayalam = findViewById<RadioButton>(R.id.rbMalayalam)

        val currentLang = prefs.getString(KEY_LANGUAGE, "en")
        when (currentLang) {
            "hi" -> rbHindi.isChecked = true
            "ml" -> rbMalayalam.isChecked = true
            else -> rbEnglish.isChecked = true
        }

        rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val lang = when (checkedId) {
                R.id.rbHindi     -> "hi"
                R.id.rbMalayalam -> "ml"
                else             -> "en"
            }
            prefs.edit().putString(KEY_LANGUAGE, lang).apply()
        }

        // ── Speech rate ───────────────────────────────────────────────────
        val spinnerRate  = findViewById<Spinner>(R.id.spinnerSpeechRate)
        val rateLabels   = arrayOf("Slow", "Normal", "Fast")
        val rateValues   = floatArrayOf(0.6f, 0.85f, 1.1f)

        spinnerRate.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            rateLabels
        )

        val currentRate     = prefs.getFloat(KEY_SPEECH_RATE, 0.85f)
        val currentRateIdx  = rateValues.indexOfFirst { Math.abs(it - currentRate) < 0.1f }
        spinnerRate.setSelection(if (currentRateIdx < 0) 1 else currentRateIdx)

        spinnerRate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putFloat(KEY_SPEECH_RATE, rateValues[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { /* no-op */ }
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    companion object {
        private const val PREFS_NAME      = "currency_prefs"
        private const val KEY_LANGUAGE    = "language"
        private const val KEY_SPEECH_RATE = "speech_rate"
    }
}
