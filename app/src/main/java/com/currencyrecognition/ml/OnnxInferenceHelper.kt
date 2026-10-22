package com.currencyrecognition.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer

/**
 * OnnxInferenceHelper
 *
 * Loads best.onnx from app assets and runs YOLO11n inference.
 *
 * Input  tensor — name: "images",  shape: [1, 3, 640, 640], float32, values in [0, 1]
 * Output tensor — name: "output0", shape: [1, 11, 8400],    float32
 *   rows 0-3  : cx, cy, w, h  (in 640-pixel space)
 *   rows 4-10 : class scores  (Rs10, Rs20, Rs50, Rs100, Rs200, Rs500, Rs2000)
 *
 * Call detect() on a background thread — it is NOT thread-safe.
 * Call close() when done (e.g., in Activity.onDestroy()).
 */
class OnnxInferenceHelper(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        // Load model bytes from assets
        val modelBytes = context.assets.open(MODEL_FILENAME).readBytes()
        session = env.createSession(modelBytes, OrtSession.SessionOptions())
    }

    // ── Data classes ──────────────────────────────────────────────────────

    /**
     * A single detected currency note.
     * Coordinates are in [0, 1] relative to the image (not pixels).
     */
    data class Detection(
        val x1: Float,         // left   (0–1)
        val y1: Float,         // top    (0–1)
        val x2: Float,         // right  (0–1)
        val y2: Float,         // bottom (0–1)
        val confidence: Float,
        val classId: Int,
    )

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Runs inference on [bitmap].
     * @param confThreshold Minimum class score to accept a detection (0–1).
     * @return List of detections after NMS, sorted by confidence (descending).
     */
    fun detect(bitmap: Bitmap, confThreshold: Float): List<Detection> {
        val inputArray = preprocessBitmap(bitmap)

        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null

        return try {
            inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(inputArray),
                longArrayOf(1L, 3L, MODEL_SIZE.toLong(), MODEL_SIZE.toLong())
            )

            results = session.run(mapOf("images" to inputTensor))

            // Get output tensor — Ultralytics YOLO11n ONNX output name is "output0"
            val outputTensor = results.get("output0").get() as OnnxTensor
            val outputBuffer = outputTensor.floatBuffer
            val outputArray = FloatArray(outputBuffer.remaining())
            outputBuffer.get(outputArray)

            parseOutput(outputArray, confThreshold)
        } finally {
            inputTensor?.close()
            results?.close()
        }
    }

    fun close() {
        session.close()
        // Note: do NOT call env.close() — OrtEnvironment.getEnvironment() returns a
        // process-wide singleton. Closing it here would break any future ORT calls.
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Resize bitmap to 640×640 and convert to CHW float array, values in [0, 1].
     * Format: [R₀₀, R₀₁, …, G₀₀, G₀₁, …, B₀₀, B₀₁, …]  (channel-first)
     */
    private fun preprocessBitmap(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, MODEL_SIZE, MODEL_SIZE, true)
        val pixels  = IntArray(MODEL_SIZE * MODEL_SIZE)
        resized.getPixels(pixels, 0, MODEL_SIZE, 0, 0, MODEL_SIZE, MODEL_SIZE)

        if (resized != bitmap) resized.recycle()

        val n = MODEL_SIZE * MODEL_SIZE
        val floatArray = FloatArray(3 * n)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            floatArray[i]         = ((pixel shr 16) and 0xFF) / 255f   // R
            floatArray[n + i]     = ((pixel shr 8)  and 0xFF) / 255f   // G
            floatArray[2 * n + i] =  (pixel         and 0xFF) / 255f   // B
        }

        return floatArray
    }

    /**
     * Parses the raw [1, 11, 8400] output tensor.
     *
     * Memory layout (row-major, batch dim squeezed):
     *   output[feature * NUM_ANCHORS + anchor]
     *
     * Features 0-3: cx, cy, w, h  (in 640-pixel space)
     * Features 4-10: class_0 … class_6 scores
     */
    private fun parseOutput(output: FloatArray, confThreshold: Float): List<Detection> {
        val rawDetections = mutableListOf<Detection>()

        for (a in 0 until NUM_ANCHORS) {
            val cx = output[0 * NUM_ANCHORS + a]
            val cy = output[1 * NUM_ANCHORS + a]
            val w  = output[2 * NUM_ANCHORS + a]
            val h  = output[3 * NUM_ANCHORS + a]

            // Find the best class score for this anchor
            var bestClass = -1
            var bestScore = 0f
            for (c in 0 until NUM_CLASSES) {
                val score = output[(4 + c) * NUM_ANCHORS + a]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }

            if (bestScore < confThreshold || bestClass < 0) continue

            // Convert from 640-pixel space to [0, 1]
            val x1 = ((cx - w / 2f) / MODEL_SIZE).coerceIn(0f, 1f)
            val y1 = ((cy - h / 2f) / MODEL_SIZE).coerceIn(0f, 1f)
            val x2 = ((cx + w / 2f) / MODEL_SIZE).coerceIn(0f, 1f)
            val y2 = ((cy + h / 2f) / MODEL_SIZE).coerceIn(0f, 1f)

            if (x2 <= x1 || y2 <= y1) continue  // degenerate box

            rawDetections.add(Detection(x1, y1, x2, y2, bestScore, bestClass))
        }

        return nms(rawDetections, IOU_THRESHOLD)
    }

    /**
     * Non-Maximum Suppression.
     * For each class independently: keep the highest-confidence box and suppress
     * any overlapping box (IoU > [iouThreshold]).
     */
    private fun nms(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted    = detections.sortedByDescending { it.confidence }
        val suppressed = BooleanArray(sorted.size)
        val result    = mutableListOf<Detection>()

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            result.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                // Only suppress boxes of the same class
                if (sorted[i].classId == sorted[j].classId &&
                    iou(sorted[i], sorted[j]) > iouThreshold
                ) {
                    suppressed[j] = true
                }
            }
        }

        return result
    }

    private fun iou(a: Detection, b: Detection): Float {
        val interX1 = maxOf(a.x1, b.x1)
        val interY1 = maxOf(a.y1, b.y1)
        val interX2 = minOf(a.x2, b.x2)
        val interY2 = minOf(a.y2, b.y2)

        val interW = maxOf(0f, interX2 - interX1)
        val interH = maxOf(0f, interY2 - interY1)
        val intersection = interW * interH

        if (intersection == 0f) return 0f

        val aArea = (a.x2 - a.x1) * (a.y2 - a.y1)
        val bArea = (b.x2 - b.x1) * (b.y2 - b.y1)
        val union = aArea + bArea - intersection

        return if (union <= 0f) 0f else intersection / union
    }

    // ── Constants ─────────────────────────────────────────────────────────

    companion object {
        private const val MODEL_FILENAME = "best.onnx"
        private const val MODEL_SIZE     = 640
        private const val NUM_CLASSES    = 7
        private const val NUM_ANCHORS    = 8400
        private const val IOU_THRESHOLD  = 0.45f

        /** Display names matching class_id order used during training */
        val CLASS_NAMES = arrayOf("₹10", "₹20", "₹50", "₹100", "₹200", "₹500", "₹2000")
    }
}
