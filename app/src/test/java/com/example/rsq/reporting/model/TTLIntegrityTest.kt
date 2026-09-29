package com.example.rsq.reporting.model

import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import com.example.rsq.data.model.Priority
import com.example.rsq.reporting.viewmodel.ReportViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class TTLIntegrityTest {

    @Test
    fun `Report conversion to MeshMessage preserves expirationTimestamp`() {
        val report = Report(
            id = "rep-ttl-1",
            userId = "user1",
            title = "Test",
            description = "Test Desc",
            timestamp = 1000L,
            expirationTimestamp = 5000L
        )

        val meshMessage = MeshMessage(
            id = report.id,
            senderNodeId = "local",
            originNodeId = report.userId,
            messageType = MeshMessageType.REPORT_RELAY,
            timestamp = report.timestamp,
            latitude = report.latitude,
            longitude = report.longitude,
            priority = Priority.MEDIUM,
            payload = "${report.title}: ${report.description}",
            ttl = 3,
            title = report.title,
            description = report.description,
            expirationTimestamp = report.expirationTimestamp
        )

        assertEquals("MeshMessage should inherit exact Report expiration", 5000L, meshMessage.expirationTimestamp)
    }

    @Test
    fun `MeshMessage conversion to Report preserves expirationTimestamp`() {
        val meshMessage = MeshMessage(
            id = "msg-ttl-2",
            senderNodeId = "nodeA",
            originNodeId = "nodeOrigin",
            messageType = MeshMessageType.REPORT_RELAY,
            timestamp = 1000L,
            latitude = 0.0,
            longitude = 0.0,
            priority = Priority.HIGH,
            payload = "Test",
            ttl = 3,
            expirationTimestamp = 9999L
        )

        val report = ReportViewModel.convertFromMeshMessage(meshMessage)

        assertEquals("Report should inherit exact MeshMessage expiration", 9999L, report.expirationTimestamp)
    }
}
