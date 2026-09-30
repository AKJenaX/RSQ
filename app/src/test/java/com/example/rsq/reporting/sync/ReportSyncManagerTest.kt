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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.io.IOException

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

        val result = syncManager.syncPendingReports()

        assertTrue(result.isSuccess)
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
    fun `syncReport should classify transient network failure as recoverable`() = runTest {
        val entity = createTestEntity("rep-retry", SyncStatus.LOCAL_ONLY)
        whenever(mockLocalRepository.getPendingReports()).thenReturn(listOf(entity))
        whenever(mockLocalRepository.getReportById("rep-retry")).thenReturn(entity)
        whenever(mockCloudRepository.submitReport(any())).thenReturn(Result.failure(IOException("Network connection lost")))

        val result = syncManager.syncPendingReports()

        assertTrue("Transient network failure should return failure to trigger worker retry", result.isFailure)
        assertFalse("Exception should NOT be permanent", syncManager.isPermanentError(result.exceptionOrNull()))
        verify(mockLocalRepository).updateSyncStatus("rep-retry", SyncStatus.SYNCING)
        verify(mockLocalRepository).updateSyncStatus("rep-retry", SyncStatus.FAILED)
        verify(mockLocalRepository, never()).updateSyncStatus("rep-retry", SyncStatus.SYNCED)
    }

    @Test
    fun `syncReport should classify missing authenticated user as permanent failure`() = runTest {
        val entity = createTestEntity("rep-no-auth", SyncStatus.LOCAL_ONLY).copy(userId = "")
        whenever(mockLocalRepository.getPendingReports()).thenReturn(listOf(entity))
        whenever(mockLocalRepository.getReportById("rep-no-auth")).thenReturn(entity)

        val result = syncManager.syncPendingReports()

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue("Missing user auth should be permanent failure", syncManager.isPermanentError(err))
    }

    @Test
    fun `batch sync with Report A success and Report B transient failure should preserve Report A SYNCED state`() = runTest {
        val entityA = createTestEntity("rep-A", SyncStatus.LOCAL_ONLY)
        val entityB = createTestEntity("rep-B", SyncStatus.LOCAL_ONLY)

        whenever(mockLocalRepository.getPendingReports()).thenReturn(listOf(entityA, entityB))
        whenever(mockLocalRepository.getReportById("rep-A")).thenReturn(entityA)
        whenever(mockLocalRepository.getReportById("rep-B")).thenReturn(entityB)

        whenever(mockCloudRepository.submitReport(argThat { id == "rep-A" })).thenReturn(Result.success(Unit))
        whenever(mockCloudRepository.submitReport(argThat { id == "rep-B" })).thenReturn(Result.failure(IOException("Timeout")))

        val result = syncManager.syncPendingReports()

        assertTrue("Batch with transient failure should return failure to trigger worker retry for rep-B", result.isFailure)
        verify(mockLocalRepository).updateSyncStatus("rep-A", SyncStatus.SYNCED)
        verify(mockLocalRepository).updateSyncStatus("rep-B", SyncStatus.FAILED)
        verify(mockLocalRepository, never()).updateSyncStatus("rep-B", SyncStatus.SYNCED)
    }

    @Test
    fun `syncReport reuses existing imageUrls during retry without re-uploading uploaded images`() = runTest {
        val entity = createTestEntity("rep-images", SyncStatus.LOCAL_ONLY).copy(
            localImagePaths = emptyList(),
            imageUrls = listOf("https://storage.firebase.com/img1.jpg")
        )
        whenever(mockLocalRepository.getPendingReports()).thenReturn(listOf(entity))
        whenever(mockLocalRepository.getReportById("rep-images")).thenReturn(entity)
        whenever(mockCloudRepository.submitReport(any())).thenReturn(Result.success(Unit))

        syncManager.syncPendingReports()

        val captor = argumentCaptor<Report>()
        verify(mockCloudRepository).submitReport(captor.capture())
        assertEquals("Existing uploaded image URL should be preserved", "https://storage.firebase.com/img1.jpg", captor.firstValue.imageUrls.firstOrNull())
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
        syncStatus = status,
        aiScore = 0f,
        detectedHazards = emptyList(),
        recommendedResources = emptyList(),
        expirationTimestamp = 0L
    )
}
