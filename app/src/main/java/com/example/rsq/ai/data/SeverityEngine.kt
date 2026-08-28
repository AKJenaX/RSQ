package com.example.rsq.ai.data

import android.content.Context
import android.net.Uri
import com.example.rsq.ai.domain.ImageSeverityAnalyzer
import com.example.rsq.ai.domain.TextSeverityAnalyzer
import com.example.rsq.ai.model.*

/**
 * A multimodal AI engine that predicts the severity of an emergency report.
 * Refactored to delegate to text and image analyzers.
 */
object SeverityEngine {

    private val textAnalyzer: TextSeverityAnalyzer = KeywordTextAnalyzer()
    private var imageAnalyzer: ImageSeverityAnalyzer? = null

    /**
     * Initialize the engine with context to setup ONNX.
     */
    fun initialize(context: Context) {
        if (imageAnalyzer == null) {
            imageAnalyzer = OnnxImageAnalyzer(context.applicationContext)
        }
    }

    /**
     * Predicts severity based on title and description.
     * Maintains backward compatibility with the existing UI.
     */
    fun predictSeverity(title: String, description: String): SeverityPrediction {
        val textResult = textAnalyzer.analyze(title, description)
        
        // Since no image is provided in this legacy call, fuse with unavailable image result
        val fusedResult = SeverityFusionEngine.fuse(textResult, ImageAnalysisResult.unavailable())

        return SeverityPrediction(
            severity = fusedResult.severity,
            confidence = fusedResult.confidence,
            reason = fusedResult.reason
        )
    }

    /**
     * Performs a full multimodal analysis.
     */
    suspend fun analyzeMultimodal(
        title: String,
        description: String,
        imageUri: Uri?
    ): SeverityAnalysisResult {
        val textResult = textAnalyzer.analyze(title, description)
        val imageResult = if (imageUri != null && imageAnalyzer != null) {
            imageAnalyzer!!.analyze(imageUri)
        } else {
            val reason = if (imageUri == null) "No image provided" else "Image analyzer not initialized"
            ImageAnalysisResult.unavailable(reason)
        }

        return SeverityFusionEngine.fuse(textResult, imageResult)
    }
}
