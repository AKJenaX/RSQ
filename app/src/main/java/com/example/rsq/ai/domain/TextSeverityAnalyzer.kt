package com.example.rsq.ai.domain

import com.example.rsq.ai.model.TextAnalysisResult

/**
 * Interface for analyzing the severity of an emergency based on text input.
 */
interface TextSeverityAnalyzer {
    /**
     * Analyzes the title and description of a report.
     */
    fun analyze(title: String, description: String): TextAnalysisResult
}
