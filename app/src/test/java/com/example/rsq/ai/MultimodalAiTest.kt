package com.example.rsq.ai

import com.example.rsq.ai.data.KeywordTextAnalyzer
import com.example.rsq.ai.data.SeverityFusionEngine
import com.example.rsq.ai.model.HazardType
import com.example.rsq.ai.model.ImageAnalysisResult
import com.example.rsq.ai.model.TextAnalysisResult
import org.junit.Assert.*
import org.junit.Test

class MultimodalAiTest {

    private val textAnalyzer = KeywordTextAnalyzer()

    @Test
    fun `text keyword detection identifies correct hazards`() {
        val result = textAnalyzer.analyze("House Fire", "Lots of smoke and flames")
        assertTrue(result.detectedHazards.contains(HazardType.FIRE_SMOKE))
        assertEquals(0.95f, result.score)
    }

    @Test
    fun `text with no keywords returns low score`() {
        val result = textAnalyzer.analyze("Hello", "Nothing here")
        assertTrue(result.detectedHazards.isEmpty())
        assertEquals(0.20f, result.score)
    }

    @Test
    fun `fusion applies 70-30 weighting correctly`() {
        val textResult = TextAnalysisResult(0.5f, 1f, emptyList(), "Text")
        val imageResult = ImageAnalysisResult(1.0f, 1f, emptyList(), "Image", true)

        val fused = SeverityFusionEngine.fuse(textResult, imageResult)

        // (1.0 * 0.7) + (0.5 * 0.3) = 0.7 + 0.15 = 0.85
        assertEquals(0.85f, fused.finalScore, 0.001f)
    }

    @Test
    fun `fusion falls back to text if image is unavailable`() {
        val textResult = TextAnalysisResult(0.6f, 1f, emptyList(), "Text")
        val imageResult = ImageAnalysisResult.unavailable()

        val fused = SeverityFusionEngine.fuse(textResult, imageResult)

        assertEquals(0.6f, fused.finalScore)
    }

    @Test
    fun `score thresholds correctly map to severity labels`() {
        val baseText = TextAnalysisResult(0f, 1f, emptyList(), "")

        // LOW: 0.39
        assertEquals("LOW", SeverityFusionEngine.fuse(baseText.copy(score = 0.39f), ImageAnalysisResult.unavailable()).severity)

        // MEDIUM: 0.40
        assertEquals("MEDIUM", SeverityFusionEngine.fuse(baseText.copy(score = 0.40f), ImageAnalysisResult.unavailable()).severity)

        // MEDIUM: 0.69
        assertEquals("MEDIUM", SeverityFusionEngine.fuse(baseText.copy(score = 0.69f), ImageAnalysisResult.unavailable()).severity)

        // HIGH: 0.70
        assertEquals("HIGH", SeverityFusionEngine.fuse(baseText.copy(score = 0.70f), ImageAnalysisResult.unavailable()).severity)

        // HIGH: 0.84
        assertEquals("HIGH", SeverityFusionEngine.fuse(baseText.copy(score = 0.84f), ImageAnalysisResult.unavailable()).severity)

        // CRITICAL: 0.85
        assertEquals("CRITICAL", SeverityFusionEngine.fuse(baseText.copy(score = 0.85f), ImageAnalysisResult.unavailable()).severity)
    }

    @Test
    fun `resource recommender is deterministic for hazards`() {
        val textResult = textAnalyzer.analyze("Flood emergency", "Need a boat")
        val imageResult = ImageAnalysisResult.unavailable()
        val fused = SeverityFusionEngine.fuse(textResult, imageResult)

        assertTrue(fused.recommendedResources.contains("rescue boat"))
        assertTrue(fused.recommendedResources.contains("life jackets"))
    }
}
