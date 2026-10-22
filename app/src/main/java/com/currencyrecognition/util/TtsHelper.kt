package com.currencyrecognition.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.currencyrecognition.R
import java.util.Locale

/**
 * TtsHelper
 *
 * Wraps Android TextToSpeech for currency denomination announcements.
 * Supports English, Hindi and Malayalam.
 */
class TtsHelper(private val context: Context, language: String) : TextToSpeech.OnInitListener {

    private val tts: TextToSpeech = TextToSpeech(context, this)
    private var isReady = false
    private var pendingLanguage: String = language

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            applyLanguage(pendingLanguage)
        } else {
            Log.e(TAG, "TextToSpeech initialisation failed with status $status")
        }
    }

    /** Call this when the user changes language in Settings. */
    fun setLanguage(langCode: String) {
        pendingLanguage = langCode
        if (isReady) applyLanguage(langCode)
    }

    /**
     * Speak the denomination for the given [classId].
     */
    fun speak(classId: Int) {
        if (!isReady) return
        if (classId < 0 || classId >= CLASS_NAMES_EN.size) return

        val prefs    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val langCode = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        val rate     = prefs.getFloat(KEY_SPEECH_RATE, 0.85f)

        applyLanguage(langCode)
        tts.setSpeechRate(rate)

        val text = when (langCode) {
            "ml" -> CLASS_NAMES_ML[classId]
            "hi" -> CLASS_NAMES_HI[classId]
            else -> CLASS_NAMES_EN[classId]
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_$classId")
    }

    /**
     * Speak "No money detected" in the current language.
     */
    fun speakNoDetection() {
        if (!isReady) return

        val prefs    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val langCode = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        val rate     = prefs.getFloat(KEY_SPEECH_RATE, 0.85f)

        applyLanguage(langCode)
        tts.setSpeechRate(rate)

        val text = when (langCode) {
            "ml" -> context.getString(R.string.no_money_detected_ml)
            "hi" -> context.getString(R.string.no_money_detected_hi)
            else -> context.getString(R.string.no_money_detected_en)
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_none")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    // ── Private ───────────────────────────────────────────────────────────

    private fun applyLanguage(langCode: String) {
        val locale = when (langCode) {
            "ml" -> Locale("ml", "IN")
            "hi" -> Locale("hi", "IN")
            else -> Locale.ENGLISH
        }
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language pack not available for $langCode, falling back to English")
            tts.setLanguage(Locale.ENGLISH)
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────

    companion object {
        private const val TAG             = "TtsHelper"
        private const val PREFS_NAME      = "currency_prefs"
        private const val KEY_LANGUAGE    = "language"
        private const val KEY_SPEECH_RATE = "speech_rate"

        private val CLASS_NAMES_EN = arrayOf(
            "10 Rupees",
            "20 Rupees",
            "50 Rupees",
            "100 Rupees",
            "200 Rupees",
            "500 Rupees",
            "2000 Rupees"
        )

        private val CLASS_NAMES_HI = arrayOf(
            "दस रुपये",
            "बीസ് रुपये",
            "पचास रुपये",
            "एक सौ रुपये",
            "दो सौ रुपये",
            "पाँच सौ रुपये",
            "दो हज़ार रुपये"
        )

        private val CLASS_NAMES_ML = arrayOf(
            "പത്ത് രൂപ",
            "ഇരുപത് രൂപ",
            "അമ്പത് രൂപ",
            "നൂറ് രൂപ",
            "ഇരുനൂറ് രൂപ",
            "അഞ്ഞൂറ് രൂപ",
            "രണ്ടായിരം രൂപ"
        )
    }
}
