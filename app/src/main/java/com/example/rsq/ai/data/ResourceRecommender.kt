package com.example.rsq.ai.data

import com.example.rsq.ai.model.HazardType

/**
 * Deterministically recommends resources based on detected hazards and severity.
 */
object ResourceRecommender {

    fun recommend(hazards: List<HazardType>, severity: String): List<String> {
        val resources = mutableSetOf<String>()

        // Hazard-based recommendations
        hazards.forEach { hazard ->
            when (hazard) {
                HazardType.FLOOD -> {
                    resources.add("rescue boat")
                    resources.add("life jackets")
                    resources.add("evacuation support")
                }
                HazardType.FIRE_SMOKE -> {
                    resources.add("fire response")
                    resources.add("breathing protection")
                    resources.add("medical support")
                }
                HazardType.COLLAPSED_STRUCTURE -> {
                    resources.add("search and rescue")
                    resources.add("structural rescue team")
                    resources.add("ambulance")
                }
                else -> Unit
            }
        }

        // Severity-based boosters
        if (severity == "CRITICAL" || severity == "HIGH") {
            resources.add("emergency medical team")
            resources.add("advanced life support")
        }

        return resources.toList().sorted()
    }
}
