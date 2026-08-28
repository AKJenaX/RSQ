package com.example.rsq.ai.model

/**
 * Configuration class containing the keyword rules used by the AI Severity Engine.
 */
object SeverityRules {
    val CRITICAL_KEYWORDS = listOf("fire", "explosion", "collapse", "earthquake", "flood", "building collapse")
    val HIGH_KEYWORDS = listOf("accident", "unconscious", "bleeding", "trapped", "severe injury")
    val MEDIUM_KEYWORDS = listOf("injury", "medical", "power outage", "road blocked", "stranded")
}
