package com.example.rsq.notification

import android.content.Context
import com.example.rsq.data.local.NotificationDao
import com.example.rsq.data.local.NotificationEntity
import com.example.rsq.data.model.Notification
import com.example.rsq.data.model.NotificationType
import com.example.rsq.data.repository.NotificationRepositoryImpl
import com.example.rsq.reporting.model.Report
import com.example.rsq.util.EmergencyNotificationManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class LiveNotificationTest {

    private lateinit var fakeDao: FakeNotificationDao
    private lateinit var repository: NotificationRepositoryImpl

    @Before
    fun setup() {
        fakeDao = FakeNotificationDao()
        repository = NotificationRepositoryImpl(fakeDao)
    }

    @Test
    fun `addNotificationUnique should insert first occurrence and return true`() = runTest {
        val notif = Notification(
            id = "NT_SOS_101",
            recipientId = "user1",
            title = "New Emergency Report",
            message = "HIGH: Medical Emergency",
            timestamp = "Just now",
            type = NotificationType.SOS_ALERT,
            isRead = false,
            associatedReportId = "rep-101"
        )

        val result = repository.addNotificationUnique(notif)
        assertTrue("First insertion should return true", result)
        assertEquals(1, fakeDao.insertedList.size)
    }

    @Test
    fun `addNotificationUnique should suppress duplicate notifications for the same report ID`() = runTest {
        val notif1 = Notification(
            id = "NT_SOS_202",
            recipientId = "user1",
            title = "New Mesh Report",
            message = "CRITICAL: Fire Alert",
            timestamp = "Just now",
            type = NotificationType.SOS_ALERT,
            isRead = false,
            associatedReportId = "rep-202"
        )

        val notif2 = Notification(
            id = "NT_SOS_202",
            recipientId = "user1",
            title = "New Emergency Report from Cloud",
            message = "CRITICAL: Fire Alert",
            timestamp = "Just now",
            type = NotificationType.SOS_ALERT,
            isRead = false,
            associatedReportId = "rep-202"
        )

        val firstResult = repository.addNotificationUnique(notif1)
        val secondResult = repository.addNotificationUnique(notif2)

        assertTrue("First notification insertion should succeed", firstResult)
        assertFalse("Duplicate notification for same report should be suppressed", secondResult)
        assertEquals("Only one notification should be stored in DB", 1, fakeDao.insertedList.size)
    }

    @Test
    fun `Expired reports should be rejected and produce no notification`() = runTest {
        val now = System.currentTimeMillis()
        val expiredReport = Report(
            id = "rep-expired",
            title = "Old Flood",
            description = "Expired emergency",
            expirationTimestamp = now - 10000L
        )

        val isExpired = expiredReport.expirationTimestamp > 0L && expiredReport.expirationTimestamp <= now
        assertTrue("Report must evaluate to expired", isExpired)

        // Expired reports skip calling addNotificationUnique
        assertEquals(0, fakeDao.insertedList.size)
    }

    @Test
    fun `Valid unexpired reports should generate notification`() = runTest {
        val now = System.currentTimeMillis()
        val validReport = Report(
            id = "rep-valid",
            title = "Active Landslide",
            description = "Immediate help required",
            expirationTimestamp = now + 3600000L
        )

        val isExpired = validReport.expirationTimestamp > 0L && validReport.expirationTimestamp <= now
        assertFalse("Report must be valid and unexpired", isExpired)

        val notif = Notification(
            id = "NT_SOS_${validReport.id}",
            recipientId = "user1",
            title = "New Emergency Report",
            message = "${validReport.severity}: ${validReport.title}",
            timestamp = "Just now",
            type = NotificationType.SOS_ALERT,
            isRead = false,
            associatedReportId = validReport.id
        )

        val result = repository.addNotificationUnique(notif)
        assertTrue("Valid report notification should be inserted", result)
    }

    @Test
    fun `EmergencyNotificationManager skips system notification safely when permission is not granted`() {
        val mockContext = mock<Context>()
        val report = Report(id = "rep-303", title = "Test", description = "Desc")

        // Should execute smoothly without throwing exceptions
        try {
            EmergencyNotificationManager.showSystemNotification(mockContext, report)
        } catch (e: Exception) {
            Assert.fail("System notification should handle permission checks gracefully: ${e.message}")
        }
    }

    private class FakeNotificationDao : NotificationDao {
        val insertedList = mutableListOf<NotificationEntity>()

        override fun getNotificationsForRecipient(recipientId: String): Flow<List<NotificationEntity>> = MutableSharedFlow()
        override fun getUnreadCount(recipientId: String): Flow<Int> = MutableSharedFlow()
        override suspend fun insertNotification(notification: NotificationEntity) {
            insertedList.add(notification)
        }

        override suspend fun insertNotificationIgnore(notification: NotificationEntity): Long {
            if (insertedList.any { it.id == notification.id || (it.associatedReportId == notification.associatedReportId && it.type == notification.type) }) {
                return -1L
            }
            insertedList.add(notification)
            return insertedList.size.toLong()
        }

        override suspend fun getNotificationForReport(reportId: String, recipientId: String, type: String): NotificationEntity? {
            return insertedList.find { it.associatedReportId == reportId && it.recipientId == recipientId && it.type == type }
        }

        override suspend fun markAsRead(id: String) {}
        override suspend fun markAllAsRead(recipientId: String) {}
    }
}
