package com.example.rsq.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.rsq.ai.domain.ImageSeverityAnalyzer
import com.example.rsq.ai.model.HazardType
import com.example.rsq.ai.model.ImageAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.exp

/**
 * Real ONNX-based image classifier for RSQ.
 * Implements MobileNetV3-Large inference with NCHW layout and ImageNet normalization.
 */
class OnnxImageAnalyzer(
    private val context: Context,
    private val modelFileName: String = "rsq_mobilenetv3_best.onnx"
) : ImageSeverityAnalyzer {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null

    // ImageNet normalization constants
    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std = floatArrayOf(0.229f, 0.224f, 0.225f)
    private val inputSize = 224

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            Log.d("OnnxImageAnalyzer", "Loading model asset: $modelFileName")
            val modelBytes = context.assets.open(modelFileName).readBytes()
            ortSession = ortEnv.createSession(modelBytes)
            
            ortSession?.let { session ->
                Log.d("OnnxImageAnalyzer", "ONNX session initialization succeeded")
                Log.d("OnnxImageAnalyzer", "Input names: ${session.inputNames}")
                // Using session.numInputs and iterating if needed, but for now just basic success log
                // since some metadata APIs might vary by ORT version.
            }
        } catch (e: Exception) {
            Log.e("OnnxImageAnalyzer", "Failed to load model", e)
            e.printStackTrace()
        }
    }

    override suspend fun analyze(imageUri: Uri): ImageAnalysisResult = withContext(Dispatchers.Default) {
        try {
            Log.d("OnnxImageAnalyzer", "Starting analysis for URI: $imageUri")
            val session = ortSession ?: return@withContext ImageAnalysisResult.unavailable("ONNX Session not initialized")

            // 1. Decode Bitmap
            val bitmap = decodeBitmap(imageUri) ?: return@withContext ImageAnalysisResult.unavailable("Failed to decode bitmap")
            Log.d("OnnxImageAnalyzer", "Bitmap decoded: ${bitmap.width}x${bitmap.height} config=${bitmap.config}")

            // 2. Preprocess (Resize, RGB, Normalize, NCHW)
            val floatBuffer = preprocess(bitmap)
            Log.d("OnnxImageAnalyzer", "Preprocessing completed")

            // 3. Create Input Tensor [1, 3, 224, 224]
            val shape = longArrayOf(1, 3, 224, 224)
            Log.d("OnnxImageAnalyzer", "Creating input tensor with shape: ${shape.contentToString()}")
            val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, shape)

            // 4. Run Inference with resource safety
            inputTensor.use { tensor ->
                val inputName = session.inputNames.iterator().next()
                Log.d("OnnxImageAnalyzer", "Running session.run() with input: $inputName")
                session.run(Collections.singletonMap(inputName, tensor)).use { result ->
                    Log.d("OnnxImageAnalyzer", "session.run() succeeded")
                    @Suppress("UNCHECKED_CAST")
                    val output = result[0].value as Array<FloatArray>
                    val logits = output[0]
                    Log.d("OnnxImageAnalyzer", "Raw output logits: ${logits.contentToString()}")

                    // 5. Softmax & Max Probability
                    val probabilities = softmax(logits)
                    Log.d("OnnxImageAnalyzer", "Softmax probabilities: ${probabilities.contentToString()}")
                    
                    val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
                    val confidence = probabilities[maxIndex]
                    Log.d("OnnxImageAnalyzer", "maxIndex=$maxIndex, confidence=$confidence")

                    // 6. Map to RSQ HazardType
                    // Mapping: 0=COLLAPSED_STRUCTURE, 1=FIRE_SMOKE, 2=FLOOD, 3=NORMAL
                    val hazard = when (maxIndex) {
                        0 -> HazardType.COLLAPSED_STRUCTURE
                        1 -> HazardType.FIRE_SMOKE
                        2 -> HazardType.FLOOD
                        else -> HazardType.UNKNOWN // NORMAL is handled by returning result with appropriate score
                    }
                    Log.d("OnnxImageAnalyzer", "Mapped HazardType: ${hazard.name}")

                    val hazards = if (hazard != HazardType.UNKNOWN) listOf(hazard) else emptyList()

                    // If predicted as NORMAL, the score is based on the max prob of NORMAL class
                    // In RSQ architecture, score is used for fusion.
                    // If maxIndex is 3 (NORMAL), the disaster signal is low.
                    val score = if (maxIndex == 3) 0.1f else confidence // Assign baseline low score for normal
                    Log.d("OnnxImageAnalyzer", "Final image score: $score")

                    ImageAnalysisResult(
                        score = score,
                        confidence = confidence,
                        detectedHazards = hazards,
                        reason = "ONNX MobileNetV3 prediction: ${hazard.name} (Conf: ${String.format("%.2f", confidence)})",
                        isAvailable = true
                    )
                }
            }

        } catch (e: Exception) {
            Log.e("OnnxImageAnalyzer", "Inference error", e)
            e.printStackTrace()
            ImageAnalysisResult.unavailable("Inference error: ${e.message}")
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        // NCHW format: [1, 3, 224, 224]
        val buffer = FloatBuffer.allocate(1 * 3 * inputSize * inputSize)

        // Channel-wise filling for NCHW
        for (c in 0 until 3) {
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val value = when (c) {
                    0 -> (pixel shr 16) and 0xFF // R
                    1 -> (pixel shr 8) and 0xFF  // G
                    else -> pixel and 0xFF       // B
                }
                // Normalize: (val / 255.0 - mean) / std
                val normalized = (value / 255.0f - mean[c]) / std[c]
                buffer.put(normalized)
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp(it - maxLogit) }
        val sumExps = exps.sum()
        return exps.map { it / sumExps }.toFloatArray()
    }

    fun close() {
        ortSession?.close()
        ortEnv.close()
    }
}
