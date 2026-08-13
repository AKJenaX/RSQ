package com.example.rsq.reporting.sync

import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.data.local.ReportEntity
import com.example.rsq.reporting.model.SyncStatus
import com.example.rsq.storage.data.StorageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReportSyncManagerTest {

    private lateinit var mockLocalRepository: LocalReportRepository
    private lateinit var mockCloudRepository: ReportRepository
    private lateinit var mockStorageRepository: StorageRepository
    private lateinit var syncManager: ReportSyncManager

    @Before
    fun setup() {
        mockLocalRepository = mock()
        mockCloudRepository = mock()
        mockStorageRepository = mock()
        syncManager = ReportSyncManager(mock(), mockLocalRepository, mockCloudRepository, mockStorageRepository)
    }

    @Test
    fun `syncPendingReports should mark syncing then synced on success`() = runTest {
        val entity = createTestEntity("rep-1", SyncStatus.LOCAL_ONLY)
        whenever(mockLocalRepository.getPendingReports()).thenReturn(listOf(entity))
        whenever(mockCloudRepository.submitReport(any())).thenReturn(Result.success(Unit))

        syncManager.syncPendingReports()

        verify(mockLocalRepository).updateSyncStatus("rep-1", SyncStatus.SYNCING)
        verify(mockLocalRepository).updateSyncStatus("rep-1", SyncStatus.SYNCED)
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
