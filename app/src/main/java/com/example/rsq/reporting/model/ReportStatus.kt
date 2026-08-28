package com.example.rsq.reporting.model

enum class ReportStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED;

    fun toFirestoreValue(): String {
        return this.name
    }

    companion object {
        fun fromString(value: String): ReportStatus {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                OPEN
            }
        }
    }
}
