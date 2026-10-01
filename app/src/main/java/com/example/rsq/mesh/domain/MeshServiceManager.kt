package com.example.rsq.mesh.domain

import android.content.Context
import android.util.Log
import com.example.rsq.mesh.data.LocalMeshMessageRepository
import com.example.rsq.mesh.data.MeshTransportFactory
import com.example.rsq.mesh.data.NodeIdentityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import com.example.rsq.mesh.model.MeshMessageType
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.data.local.LocalReportDatabase
import com.example.rsq.reporting.model.SyncStatus
import com.example.rsq.reporting.sync.OfflineSyncCoordinator
import com.example.rsq.reporting.sync.ReportSyncManager
import com.example.rsq.reporting.sync.SyncScheduler
import com.example.rsq.reporting.viewmodel.ReportViewModel
import com.example.rsq.storage.data.StorageRepository
import com.example.rsq.data.repository.NotificationRepositoryImpl
import com.example.rsq.data.model.Notification
import com.example.rsq.data.model.NotificationType
import com.example.rsq.util.EmergencyNotificationManager
import java.util.UUID

object MeshServiceManager {
    private var engine: MeshRelayEngine? = null
    private var transport: MeshTransport? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun initialize(context: Context) {
        if (engine != null) return
        val appContext = context.applicationContext
        val identityProvider = NodeIdentityRepository(appContext)
        val repo = LocalMeshMessageRepository(appContext)
        
        transport = MeshTransportFactory.createTransport(appContext, identityProvider)
        engine = MeshRelayEngine(transport!!, repo, identityProvider, scope)

        // Observe incoming mesh traffic independent of UI
        val localReportDb = LocalReportDatabase.getDatabase(appContext)
        val localReportRepo = LocalReportRepository(localReportDb.reportDao())
        val notifRepo = NotificationRepositoryImpl(localReportDb.notificationDao())

        scope.launch {
            engine!!.processedMessages
                .filter { it.messageType == MeshMessageType.REPORT_RELAY || it.messageType == MeshMessageType.SOS }
                .filter { msg ->
                    val currentTime = System.currentTimeMillis()
                    !(msg.expirationTimestamp > 0L && msg.expirationTimestamp <= currentTime)
                }
                .collect { meshMsg ->
                    val report = ReportViewModel.convertFromMeshMessage(meshMsg)
                    
                    // Persist to Room
                    localReportRepo.saveReport(report, emptyList(), SyncStatus.LOCAL_ONLY)

                    // Notification
                    val userId = identityProvider.getNodeId()
                    if (userId.isNotBlank()) {
                        val notif = Notification(
                            id = "NT_SOS_${report.id}",
                            recipientId = userId,
                            title = "New Mesh Report",
                            message = "${report.severity}: ${report.title}",
                            timestamp = "Just now",
                            type = NotificationType.SOS_ALERT,
                            isRead = false,
                            associatedReportId = report.id
                        )
                        val isNewNotif = notifRepo.addNotificationUnique(notif)
                        if (isNewNotif) {
                            EmergencyNotificationManager.showSystemNotification(appContext, report)
                        }
                    }

                    // Route offline sync trigger through centralized OfflineSyncCoordinator
                    OfflineSyncCoordinator.triggerSync(appContext, report.id, scope, localReportRepo)
                }
        }
    }

    fun getTransport(): MeshTransport? = transport
    fun getEngine(): MeshRelayEngine? = engine
}
