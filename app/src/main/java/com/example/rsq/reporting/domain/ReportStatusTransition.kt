package com.example.rsq.reporting.domain

import com.example.rsq.reporting.model.ReportStatus

object ReportStatusTransition {

    /**
     * Checks if a transition from one [ReportStatus] to another is valid.
     * Staying in the same state is always allowed.
     */
    fun canTransition(from: ReportStatus, to: ReportStatus): Boolean {
        if (from == to) return true

        return when (from) {
            ReportStatus.OPEN -> to == ReportStatus.ASSIGNED
            ReportStatus.ASSIGNED -> to == ReportStatus.IN_PROGRESS
            ReportStatus.IN_PROGRESS -> to == ReportStatus.RESOLVED
            ReportStatus.RESOLVED -> false // Terminal state
        }
    }

    /**
     * Returns a list of valid next [ReportStatus] values given the current state.
     */
    fun nextStates(current: ReportStatus): List<ReportStatus> {
        return when (current) {
            ReportStatus.OPEN -> listOf(ReportStatus.ASSIGNED)
            ReportStatus.ASSIGNED -> listOf(ReportStatus.IN_PROGRESS)
            ReportStatus.IN_PROGRESS -> listOf(ReportStatus.RESOLVED)
            ReportStatus.RESOLVED -> emptyList()
        }
    }
}
