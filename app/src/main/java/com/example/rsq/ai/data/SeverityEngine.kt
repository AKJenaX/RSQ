package com.example.rsq.ai.data

import com.example.rsq.ai.model.SeverityPrediction
import com.example.rsq.ai.model.SeverityRules

/**
 * A rule-based AI engine that predicts the severity of an emergency report
 * based on keywords found in the title and description.
 */
object SeverityEngine {

    /**
     * Predicts severity based on keyword matching from [SeverityRules].
     */
    fun predictSeverity(title: String, description: String): SeverityPrediction {
        val content = "${title} ${description}".lowercase()

        return when {
            containsAny(content, SeverityRules.CRITICAL_KEYWORDS) -> {
                SeverityPrediction(
                    severity = "CRITICAL",
                    confidence = 0.95f,
                    reason = "High-risk keywords related to large-scale disasters or fires detected."
                )
            }
            containsAny(content, SeverityRules.HIGH_KEYWORDS) -> {
                SeverityPrediction(
                    severity = "HIGH",
                    confidence = 0.90f,
                    reason = "Keywords indicating life-threatening injuries or accidents detected."
                )
            }
            containsAny(content, SeverityRules.MEDIUM_KEYWORDS) -> {
                SeverityPrediction(
                    severity = "MEDIUM",
                    confidence = 0.80f,
                    reason = "Keywords related to medical needs or infrastructure issues detected."
                )
            }
            else -> {
                SeverityPrediction(
                    severity = "LOW",
                    confidence = 0.60f,
                    reason = "No high-risk keywords detected."
                )
            }
        }
    }

    private fun containsAny(content: String, keywords: List<String>): Boolean {
        return keywords.any { content.contains(it) }
    }
}
