package com.example.rsq.mesh.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.rsq.mesh.domain.MeshRelayEngine
import com.example.rsq.mesh.model.MediaTransferUiState
import com.example.rsq.mesh.model.MeshMediaMetadata
import com.example.rsq.reporting.data.LocalReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Receiver-side thread-safe registry tracking incoming file payloads and metadata mappings.
 * Matches file payloads by SHA-256 checksum and file size to eliminate cross-device Payload.id dependencies.
 */
class PendingMediaRegistry(
    private val context: Context,
    private val localReportRepository: LocalReportRepository,
    private val scope: CoroutineScope,
    var relayEngine: MeshRelayEngine? = null
) {
    companion object {
        private const val TAG = "PendingMediaRegistry"
    }

    private val _mediaTransfers = MutableStateFlow<List<MediaTransferUiState>>(emptyList())
    val mediaTransfers: StateFlow<List<MediaTransferUiState>> = _mediaTransfers.asStateFlow()

    // Registered metadata list
    private val pendingMetadataList = mutableListOf<MeshMediaMetadata>()

    // Maps payloadId -> mediaId for deterministic multi-file progress tracking
    private val payloadToMediaMap = mutableMapOf<Long, String>()

    // Pending received payload URIs (when URI arrives before metadata)
    private val pendingUris = mutableListOf<Triple<Uri, Long, Long>>() // Uri, sizeBytes, payloadId

    @Synchronized
    fun registerMetadata(metadataList: List<MeshMediaMetadata>) {
        metadataList.forEach { meta ->
            Log.i(TAG, "Registering media metadata: mediaId=${meta.mediaId}, reportId=${meta.reportId}, expectedSize=${meta.sizeBytes}, expectedSha256=${meta.checksum}")
            val existingMetaIndex = pendingMetadataList.indexOfFirst { it.mediaId == meta.mediaId }
            if (existingMetaIndex < 0) {
                pendingMetadataList.add(meta)
                updateUiState(
                    meta = meta,
                    status = "ANNOUNCED",
                    expectedSizeBytes = meta.sizeBytes,
                    actualSizeBytes = null,
                    bytesTransferred = 0L,
                    expectedSha256 = meta.checksum,
                    actualSha256 = "",
                    sizeMatchStatus = "PENDING",
                    checksumMatchStatus = "NOT_VERIFIED",
                    lastTransferStatus = "ANNOUNCED"
                )
                emitRelayTrace(meta.reportId, "MEDIA ANNOUNCED")

                // Check if file payload URI was ALREADY received out-of-order
                val matchingTriple = pendingUris.find { it.second == meta.sizeBytes }
                if (matchingTriple != null) {
                    pendingUris.remove(matchingTriple)
                    Log.i(TAG, "Resolving out-of-order media transfer for mediaId=${meta.mediaId}")
                    processCompletedTransfer(meta, matchingTriple.first, matchingTriple.third, null)
                }
            } else {
                Log.d(TAG, "IDEMPOTENT_METADATA_REGISTRATION: Duplicate metadata for mediaId=${meta.mediaId} (reportId=${meta.reportId}) ignored cleanly.")
            }
        }
    }

    @Synchronized
    fun onTransferProgress(payloadId: Long, bytesTransferred: Long, totalBytes: Long, endpointId: String? = null) {
        val mediaId = payloadToMediaMap[payloadId]
        var meta = pendingMetadataList.find { it.mediaId == mediaId }

        if (meta == null) {
            meta = pendingMetadataList.find { it.sizeBytes == totalBytes && !payloadToMediaMap.containsValue(it.mediaId) }
            if (meta != null) {
                payloadToMediaMap[payloadId] = meta.mediaId
            } else {
                meta = pendingMetadataList.find { it.sizeBytes == totalBytes }
            }
        }

        if (meta != null) {
            updateUiState(
                meta = meta,
                status = "RECEIVING",
                payloadId = payloadId,
                endpointId = endpointId,
                bytesTransferred = bytesTransferred,
                expectedSizeBytes = meta.sizeBytes,
                expectedSha256 = meta.checksum,
                lastTransferStatus = "IN_PROGRESS"
            )
        }
    }

    @Synchronized
    fun onTransferFailed(payloadId: Long, status: Int, totalBytes: Long, endpointId: String? = null) {
        val statusName = when (status) {
            4 -> "CANCELED"
            else -> "FAILURE"
        }
        Log.e(TAG, "MEDIA_TRANSFER_FAILED: nearbyPayloadId=$payloadId, status=$statusName, totalBytes=$totalBytes, endpointId=$endpointId")

        val mediaId = payloadToMediaMap[payloadId]
        var meta = pendingMetadataList.find { it.mediaId == mediaId }
        if (meta == null) {
            meta = pendingMetadataList.find { it.sizeBytes == totalBytes }
        }

        if (meta != null) {
            updateUiState(
                meta = meta,
                status = "FAILED",
                payloadId = payloadId,
                endpointId = endpointId,
                bytesTransferred = 0L,
                expectedSizeBytes = meta.sizeBytes,
                actualSizeBytes = null,
                expectedSha256 = meta.checksum,
                actualSha256 = "ERROR",
                sizeMatchStatus = "ERROR",
                checksumMatchStatus = "ERROR",
                lastTransferStatus = statusName,
                failureReason = "Nearby transfer $statusName (Status code $status)"
            )
            emitRelayTrace(meta.reportId, "MEDIA TRANSFER FAILED")
        }
    }

    @Synchronized
    fun onFilePayloadCompleted(payloadId: Long, receivedUri: Uri, totalBytes: Long, endpointId: String? = null) {
        Log.i(TAG, "MEDIA_FILE_COMPLETED: nearbyPayloadId=$payloadId, receivedUri=$receivedUri, size=$totalBytes, endpointId=$endpointId")

        // 1. Calculate SHA-256 of received content URI via ContentResolver
        val actualSha256 = try {
            val stream = context.contentResolver.openInputStream(receivedUri)
            if (stream != null) {
                stream.use { OfflineMediaManager.calculateChecksum(it) }
            } else {
                "ERROR"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read stream for URI $receivedUri: ${e.message}", e)
            "ERROR"
        }

        fun isPersisted(mId: String): Boolean {
            return _mediaTransfers.value.any { it.mediaId == mId && it.status == "PERSISTED" }
        }

        // 2. Filter metadata candidates matching file size
        val sizeCandidates = pendingMetadataList.filter { it.sizeBytes == totalBytes }
        val unpersistedCandidates = sizeCandidates.filter { !isPersisted(it.mediaId) }
        val candidatesToEvaluate = if (unpersistedCandidates.isNotEmpty()) unpersistedCandidates else sizeCandidates

        if (candidatesToEvaluate.isEmpty()) {
            if (pendingMetadataList.isEmpty()) {
                Log.w(TAG, "File payload completed BEFORE metadata arrived. Holding URI ($receivedUri, $totalBytes bytes) in pending queue.")
                pendingUris.add(Triple(receivedUri, totalBytes, payloadId))
            } else {
                val errorMsg = "No media metadata matched size $totalBytes bytes"
                Log.e(TAG, "MEDIA_TRANSFER_FAILED: $errorMsg")
            }
            return
        }

        // 3. Match candidate by SHA-256 checksum
        val matchingCandidates = candidatesToEvaluate.filter { it.checksum.equals(actualSha256, ignoreCase = true) }
        val distinctMediaCandidates = matchingCandidates.distinctBy { it.mediaId }

        Log.i(TAG, "MEDIA_MATCH_DIAGNOSTIC: payloadId=$payloadId, totalBytes=$totalBytes, receivedSha256=$actualSha256, candidatesToEvaluate=${candidatesToEvaluate.size}, matchingChecksums=${matchingCandidates.size}, distinctMediaIds=${distinctMediaCandidates.size}")
        candidatesToEvaluate.forEachIndexed { idx, candidate ->
            val persisted = isPersisted(candidate.mediaId)
            val uiItem = _mediaTransfers.value.find { it.mediaId == candidate.mediaId }
            Log.i(TAG, "MEDIA_CANDIDATE: candidateIndex=$idx, mediaId=${candidate.mediaId}, reportId=${candidate.reportId}, sizeBytes=${candidate.sizeBytes}, expectedSha256=${candidate.checksum}, isPersisted=$persisted, status=${uiItem?.status ?: "NONE"}")
        }

        when {
            distinctMediaCandidates.size == 1 -> {
                val matchedMeta = distinctMediaCandidates.first()
                payloadToMediaMap[payloadId] = matchedMeta.mediaId
                Log.i(TAG, "MEDIA_MATCH_RESULT: candidates=${candidatesToEvaluate.size}, distinctMediaIds=${distinctMediaCandidates.size}, selectedMediaId=${matchedMeta.mediaId}, result=UNIQUE")
                Log.i(TAG, "Matched file payload URI to metadata by SHA-256 ($actualSha256): mediaId=${matchedMeta.mediaId}, payloadId=$payloadId")
                processCompletedTransfer(matchedMeta, receivedUri, payloadId, endpointId)
            }
            distinctMediaCandidates.isEmpty() -> {
                val unmatchedMeta = candidatesToEvaluate.first()
                val reason = "No media metadata matched received SHA-256 checksum ($actualSha256)"
                Log.e(TAG, "MEDIA_MATCH_RESULT: candidates=${candidatesToEvaluate.size}, distinctMediaIds=0, selectedMediaId=NONE, result=NO_MATCH")
                Log.e(TAG, "MEDIA_CHECKSUM_FAILED: $reason")
                updateUiState(
                    meta = unmatchedMeta,
                    status = "FAILED",
                    payloadId = payloadId,
                    endpointId = endpointId,
                    bytesTransferred = totalBytes,
                    expectedSizeBytes = unmatchedMeta.sizeBytes,
                    actualSizeBytes = totalBytes,
                    expectedSha256 = unmatchedMeta.checksum,
                    actualSha256 = actualSha256,
                    sizeMatchStatus = "YES",
                    checksumMatchStatus = "FAILED",
                    lastTransferStatus = "SUCCESS",
                    failureReason = reason
                )
                emitRelayTrace(unmatchedMeta.reportId, "MEDIA CHECKSUM FAILED")
            }
            else -> {
                val unpersistedMatching = distinctMediaCandidates.filter { !isPersisted(it.mediaId) }
                if (unpersistedMatching.size == 1) {
                    val matchedMeta = unpersistedMatching.first()
                    payloadToMediaMap[payloadId] = matchedMeta.mediaId
                    Log.i(TAG, "MEDIA_MATCH_RESULT: candidates=${candidatesToEvaluate.size}, distinctMediaIds=${distinctMediaCandidates.size}, selectedMediaId=${matchedMeta.mediaId}, result=DISAMBIGUATED_UNPERSISTED")
                    Log.i(TAG, "Disambiguated candidate by unpersisted status: mediaId=${matchedMeta.mediaId}, payloadId=$payloadId")
                    processCompletedTransfer(matchedMeta, receivedUri, payloadId, endpointId)
                } else {
                    val ambiguousMeta = distinctMediaCandidates.first()
                    val candidateMediaIds = distinctMediaCandidates.map { it.mediaId }
                    val reason = "Multiple media metadata items ($candidateMediaIds) matched received file checksum ($actualSha256)"
                    Log.e(TAG, "MEDIA_MATCH_RESULT: candidates=${candidatesToEvaluate.size}, distinctMediaIds=${distinctMediaCandidates.size}, selectedMediaId=AMBIGUOUS, result=AMBIGUOUS")
                    Log.e(TAG, "MEDIA_TRANSFER_FAILED: $reason")
                    updateUiState(
                        meta = ambiguousMeta,
                        status = "FAILED",
                        payloadId = payloadId,
                        endpointId = endpointId,
                        bytesTransferred = totalBytes,
                        expectedSizeBytes = ambiguousMeta.sizeBytes,
                        actualSizeBytes = totalBytes,
                        expectedSha256 = ambiguousMeta.checksum,
                        actualSha256 = actualSha256,
                        sizeMatchStatus = "YES",
                        checksumMatchStatus = "AMBIGUOUS",
                        lastTransferStatus = "SUCCESS",
                        failureReason = reason
                    )
                    emitRelayTrace(ambiguousMeta.reportId, "MEDIA AMBIGUOUS")
                }
            }
        }
    }

    private fun processCompletedTransfer(meta: MeshMediaMetadata, receivedUri: Uri, payloadId: Long?, endpointId: String?) {
        updateUiState(
            meta = meta,
            status = "COMPLETED",
            payloadId = payloadId,
            endpointId = endpointId,
            bytesTransferred = meta.sizeBytes,
            expectedSizeBytes = meta.sizeBytes,
            actualSizeBytes = meta.sizeBytes,
            expectedSha256 = meta.checksum,
            lastTransferStatus = "SUCCESS"
        )
        emitRelayTrace(meta.reportId, "MEDIA TRANSFER COMPLETED")

        scope.launch(Dispatchers.IO) {
            val verification = OfflineMediaManager.verifyAndSaveMediaFromUri(context, receivedUri, meta)
            if (verification.success && verification.savedFile != null) {
                updateUiState(
                    meta = meta,
                    status = "PERSISTED",
                    payloadId = payloadId,
                    endpointId = endpointId,
                    bytesTransferred = meta.sizeBytes,
                    expectedSizeBytes = verification.expectedSize,
                    actualSizeBytes = verification.actualSize,
                    expectedSha256 = verification.expectedChecksum,
                    actualSha256 = verification.actualChecksum,
                    sizeMatchStatus = verification.sizeMatchStatus,
                    checksumMatchStatus = verification.checksumMatchStatus,
                    lastTransferStatus = "SUCCESS",
                    saved = true,
                    localFilePath = verification.savedFile.absolutePath
                )
                emitRelayTrace(meta.reportId, "MEDIA CHECKSUM VERIFIED")
                emitRelayTrace(meta.reportId, "MEDIA PERSISTED")

                localReportRepository.addReceivedMedia(meta.reportId, verification.savedFile.absolutePath)
                Log.i(TAG, "MEDIA_TRANSFER_COMPLETED: Associated media ${meta.mediaId} with report ${meta.reportId}")

                // Trigger multi-hop media forwarding if the report was relayed
                relayEngine?.onMediaFileVerified(meta.reportId, meta.mediaId, verification.savedFile)
            } else {
                updateUiState(
                    meta = meta,
                    status = "FAILED",
                    payloadId = payloadId,
                    endpointId = endpointId,
                    bytesTransferred = verification.actualSize ?: 0L,
                    expectedSizeBytes = verification.expectedSize,
                    actualSizeBytes = verification.actualSize,
                    expectedSha256 = verification.expectedChecksum,
                    actualSha256 = verification.actualChecksum,
                    sizeMatchStatus = verification.sizeMatchStatus,
                    checksumMatchStatus = verification.checksumMatchStatus,
                    lastTransferStatus = "SUCCESS",
                    saved = false,
                    failureReason = verification.failureReason
                )
                emitRelayTrace(meta.reportId, "MEDIA CHECKSUM FAILED")
                Log.e(TAG, "MEDIA_TRANSFER_FAILED: Verification or save failed for ${meta.mediaId}: ${verification.failureReason}")
            }
        }
    }

    private fun emitRelayTrace(reportId: String, action: String) {
        scope.launch {
            try {
                relayEngine?.emitCustomEvent(
                    messageId = reportId,
                    action = action
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to emit relay trace event: ${e.message}")
            }
        }
    }

    @Synchronized
    private fun updateUiState(
        meta: MeshMediaMetadata,
        status: String,
        payloadId: Long? = null,
        endpointId: String? = null,
        bytesTransferred: Long = 0L,
        expectedSizeBytes: Long = meta.sizeBytes,
        actualSizeBytes: Long? = null,
        expectedSha256: String = meta.checksum,
        actualSha256: String = "",
        sizeMatchStatus: String = "PENDING",
        checksumMatchStatus: String = "NOT_VERIFIED",
        lastTransferStatus: String = "PENDING",
        saved: Boolean = false,
        localFilePath: String? = null,
        failureReason: String? = null
    ) {
        val current = _mediaTransfers.value.toMutableList()
        val index = current.indexOfFirst { it.mediaId == meta.mediaId }
        val existingItem = current.getOrNull(index)

        // TERMINAL STATE REGRESSION GUARD: If this media item is already PERSISTED or FAILED, do NOT allow IN_PROGRESS/RECEIVING to overwrite it!
        if (existingItem != null && (existingItem.status == "PERSISTED" || existingItem.status == "FAILED") &&
            (status == "RECEIVING" || status == "ANNOUNCED" || status == "IN_PROGRESS")) {
            Log.d(TAG, "PREVENTED_REGRESSION: Media ${meta.mediaId} is already ${existingItem.status}. Ignoring stale $status update.")
            return
        }

        val oldStatus = existingItem?.status ?: "NONE"

        val itemState = MediaTransferUiState(
            reportId = meta.reportId,
            mediaId = meta.mediaId,
            filename = meta.filename,
            mimeType = meta.mimeType,
            payloadId = payloadId ?: existingItem?.payloadId,
            endpointId = endpointId ?: existingItem?.endpointId,
            expectedSizeBytes = expectedSizeBytes,
            actualSizeBytes = actualSizeBytes ?: existingItem?.actualSizeBytes,
            bytesTransferred = if (bytesTransferred > 0) bytesTransferred else (existingItem?.bytesTransferred ?: 0L),
            expectedSha256 = expectedSha256,
            actualSha256 = if (actualSha256.isNotBlank()) actualSha256 else (existingItem?.actualSha256 ?: ""),
            sizeMatchStatus = if (sizeMatchStatus != "PENDING") sizeMatchStatus else (existingItem?.sizeMatchStatus ?: "PENDING"),
            checksumMatchStatus = if (checksumMatchStatus != "NOT_VERIFIED") checksumMatchStatus else (existingItem?.checksumMatchStatus ?: "NOT_VERIFIED"),
            status = status,
            lastTransferStatus = lastTransferStatus,
            saved = saved || (existingItem?.saved ?: false),
            localFilePath = localFilePath ?: existingItem?.localFilePath,
            failureReason = failureReason ?: existingItem?.failureReason
        )

        if (index >= 0) {
            current[index] = itemState
        } else {
            current.add(itemState)
        }

        Log.i(TAG, "STATE_MUTATION: mediaId=${meta.mediaId}, payloadId=${itemState.payloadId}, oldStatus=$oldStatus, newStatus=$status, bytesTransferred=${itemState.bytesTransferred}, actualSizeBytes=${itemState.actualSizeBytes}, sizeMatchStatus=${itemState.sizeMatchStatus}, checksumMatchStatus=${itemState.checksumMatchStatus}, saved=${itemState.saved}")

        _mediaTransfers.value = current
    }

    @Synchronized
    fun clear() {
        pendingMetadataList.clear()
        payloadToMediaMap.clear()
        pendingUris.clear()
        _mediaTransfers.value = emptyList()
    }
}
