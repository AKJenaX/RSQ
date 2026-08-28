package com.example.rsq.ai.data

import com.example.rsq.ai.domain.TextSeverityAnalyzer
import com.example.rsq.ai.model.HazardType
import com.example.rsq.ai.model.SeverityRules
import com.example.rsq.ai.model.TextAnalysisResult

/**
 * Implementation of [TextSeverityAnalyzer] that uses keyword matching rules.
 */
class KeywordTextAnalyzer : TextSeverityAnalyzer {

    override fun analyze(title: String, description: String): TextAnalysisResult {
        val content = "${title} ${description}".lowercase()

        val detectedHazards = mutableListOf<HazardType>()
        if (content.contains("fire") || content.contains("smoke")) {
            detectedHazards.add(HazardType.FIRE_SMOKE)
        }
        if (content.contains("flood") || content.contains("earthquake")) {
            detectedHazards.add(HazardType.FLOOD)
        }
        if (content.contains("collapse")) {
            detectedHazards.add(HazardType.COLLAPSED_STRUCTURE)
        }

        return when {
            containsAny(content, SeverityRules.CRITICAL_KEYWORDS) -> {
                TextAnalysisResult(
                    score = 0.95f,
                    confidence = 0.95f,
                    detectedHazards = detectedHazards,
                    reason = "Critical keywords detected in text."
                )
            }
            containsAny(content, SeverityRules.HIGH_KEYWORDS) -> {
                TextAnalysisResult(
                    score = 0.80f,
                    confidence = 0.90f,
                    detectedHazards = detectedHazards,
                    reason = "High severity keywords detected in text."
                )
            }
            containsAny(content, SeverityRules.MEDIUM_KEYWORDS) -> {
                TextAnalysisResult(
                    score = 0.55f,
                    confidence = 0.85f,
                    detectedHazards = detectedHazards,
                    reason = "Medium severity keywords detected in text."
                )
            }
            else -> {
                TextAnalysisResult(
                    score = 0.20f,
                    confidence = 0.70f,
                    detectedHazards = detectedHazards,
                    reason = "No high-risk keywords detected in text."
                )
            }
        }
    }

    private fun containsAny(content: String, keywords: List<String>): Boolean {
        return keywords.any { content.contains(it) }
    }
}
