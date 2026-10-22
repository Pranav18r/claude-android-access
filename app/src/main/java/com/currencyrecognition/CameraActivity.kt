package com.currencyrecognition

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.currencyrecognition.ml.OnnxInferenceHelper
import com.currencyrecognition.ui.OverlayView
import com.currencyrecognition.util.TtsHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraActivity — main detection screen.
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var viewFinder:    PreviewView
    private lateinit var overlayView:   OverlayView
    private lateinit var tvDenomination: TextView

    private lateinit var inferenceHelper: OnnxInferenceHelper
    private lateinit var ttsHelper:       TtsHelper
    private lateinit var cameraExecutor: ExecutorService

    private var lastSpokenClassId = -1
    private var lastSpeakTimeMs   = 0L
    
    // Logic for "No money detected"
    private var lastDetectionTimeMs = System.currentTimeMillis()
    private var hasSpokenNoDetection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder     = findViewById(R.id.viewFinder)
        overlayView    = findViewById(R.id.overlayView)
        tvDenomination = findViewById(R.id.tvDenomination)

        val prefs    = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val langCode = prefs.getString(KEY_LANGUAGE, "en") ?: "en"

        ttsHelper = TtsHelper(this, langCode)

        try {
            inferenceHelper = OnnxInferenceHelper(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ONNX model", e)
            Toast.makeText(this, "Model load failed", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (isCameraPermissionGranted()) {
            startCamera()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs    = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val langCode = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        ttsHelper.setLanguage(langCode)
        
        // Reset no-detection timer on resume
        lastDetectionTimeMs = System.currentTimeMillis()
        hasSpokenNoDetection = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized)   cameraExecutor.shutdown()
        if (::inferenceHelper.isInitialized)  inferenceHelper.close()
        if (::ttsHelper.isInitialized)        ttsHelper.shutdown()
    }

    private fun isCameraPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(cameraExecutor) { imageProxy -> processFrame(imageProxy) } }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
            } catch (e: Exception) { Log.e(TAG, "Binding failed", e) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        imageProxy.close()

        val detections = inferenceHelper.detect(bitmap, CONF_THRESHOLD)
        bitmap.recycle()

        runOnUiThread {
            overlayView.setDetections(detections)
            val now = System.currentTimeMillis()

            if (detections.isNotEmpty()) {
                lastDetectionTimeMs = now
                hasSpokenNoDetection = false

                val best = detections.maxByOrNull { it.confidence }!!
                tvDenomination.text = OnnxInferenceHelper.CLASS_NAMES[best.classId]
                tvDenomination.visibility = View.VISIBLE

                val sameClass    = best.classId == lastSpokenClassId
                val cooldownDone = (now - lastSpeakTimeMs) >= TTS_COOLDOWN_MS

                if (!sameClass || cooldownDone) {
                    ttsHelper.speak(best.classId)
                    lastSpokenClassId = best.classId
                    lastSpeakTimeMs   = now
                }
            } else {
                tvDenomination.visibility = View.INVISIBLE
                lastSpokenClassId = -1
                
                // If no detection for 5 seconds and we haven't spoken yet, say "No money detected"
                if (!hasSpokenNoDetection && (now - lastDetectionTimeMs) >= NO_DETECTION_TIMEOUT_MS) {
                    ttsHelper.speakNoDetection()
                    hasSpokenNoDetection = true
                    lastSpeakTimeMs = now // Use detection cooldown for this too
                }
            }
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        buffer.rewind()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val CAMERA_REQUEST_CODE = 100
        private const val CONF_THRESHOLD = 0.60f
        
        // Slower response: 6 seconds cooldown
        private const val TTS_COOLDOWN_MS = 6_000L
        
        // No detection feedback after 5 seconds of empty frames
        private const val NO_DETECTION_TIMEOUT_MS = 5_000L
        
        private const val PREFS_NAME = "currency_prefs"
        private const val KEY_LANGUAGE = "language"
    }
}
