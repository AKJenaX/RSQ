package com.example.rsq.reporting.sync

import android.util.Log
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.data.local.ReportEntity
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.SyncStatus
import com.example.rsq.storage.data.StorageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReportSyncManagerTest {

    private lateinit var mockLocalRepository: LocalReportRepository
    private lateinit var mockCloudRepository: ReportRepository
    private lateinit var mockStorageRepository: StorageRepository
    private lateinit var syncManager: ReportSyncManager
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setup() {
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockLocalRepository = mock()
        mockCloudRepository = mock()
        mockStorageRepository = mock()
        syncManager = ReportSyncManager(mock(), mockLocalRepository, mockCloudRepository, mockStorageRepository)
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Test
    fun `syncPendingReports should mark syncing then synced on success`() = runTest {
        val entity = createTestEntity("rep-1", SyncStatus.LOCAL_ONLY)
        whenever(mockLocalRepository.getPendingReports()).thenReturn(listOf(entity))
        whenever(mockLocalRepository.getReportById("rep-1")).thenReturn(entity)
        whenever(mockCloudRepository.submitReport(any())).thenReturn(Result.success(Unit))

        syncManager.syncPendingReports()

        verify(mockLocalRepository).updateSyncStatus("rep-1", SyncStatus.SYNCING)
        verify(mockLocalRepository).updateSyncStatus("rep-1", SyncStatus.SYNCED)
    }

    @Test
    fun `syncReport should preserve the existing stable ID during Firestore upload`() = runTest {
        val entity = createTestEntity("stable-id-123", SyncStatus.LOCAL_ONLY)
        whenever(mockLocalRepository.getPendingReports()).thenReturn(listOf(entity))
        whenever(mockLocalRepository.getReportById("stable-id-123")).thenReturn(entity)
        whenever(mockCloudRepository.submitReport(any())).thenReturn(Result.success(Unit))

        syncManager.syncPendingReports()

        val captor = argumentCaptor<Report>()
        verify(mockCloudRepository).submitReport(captor.capture())
        assertEquals("stable-id-123", captor.firstValue.id)
    }

    @Test
    fun `syncReport should remain retryable (FAILED state) when cloud upload fails`() = runTest {
        val entity = createTestEntity("rep-retry", SyncStatus.LOCAL_ONLY)
        whenever(mockLocalRepository.getPendingReports()).thenReturn(listOf(entity))
        whenever(mockLocalRepository.getReportById("rep-retry")).thenReturn(entity)
        whenever(mockCloudRepository.submitReport(any())).thenReturn(Result.failure(Exception("Network error")))

        syncManager.syncPendingReports()

        verify(mockLocalRepository).updateSyncStatus("rep-retry", SyncStatus.SYNCING)
        verify(mockLocalRepository).updateSyncStatus("rep-retry", SyncStatus.FAILED)
        verify(mockLocalRepository, never()).updateSyncStatus("rep-retry", SyncStatus.SYNCED)
    }

    @Test
    fun `syncing a report in SYNCING state (recovery) should still work`() = runTest {
        val entity = createTestEntity("rep-stale", SyncStatus.SYNCING)
        whenever(mockLocalRepository.getPendingReports()).thenReturn(listOf(entity))
        whenever(mockLocalRepository.getReportById("rep-stale")).thenReturn(entity)
        whenever(mockCloudRepository.submitReport(any())).thenReturn(Result.success(Unit))

        syncManager.syncPendingReports()

        verify(mockLocalRepository).updateSyncStatus("rep-stale", SyncStatus.SYNCING)
        verify(mockLocalRepository).updateSyncStatus("rep-stale", SyncStatus.SYNCED)
    }

    private fun createTestEntity(id: String, status: SyncStatus) = ReportEntity(
        id = id,
        userId = "u1",
        title = "T",
        description = "D",
        severity = "HIGH",
        status = "OPEN",
        timestamp = 1000L,
        latitude = null,
        longitude = null,
        imageUrl = null,
        localImagePath = null,
        isOffline = false,
        syncStatus = status
    )
}
