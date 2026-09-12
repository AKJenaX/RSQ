package com.example.rsq.mesh.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.rsq.mesh.model.MeshMediaMetadata
import com.google.android.gms.nearby.connection.Payload
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

object OfflineMediaManager {

    private const val TAG = "OfflineMediaManager"

    data class VerificationResult(
        val success: Boolean,
        val expectedSize: Long,
        val actualSize: Long?,
        val expectedChecksum: String,
        val actualChecksum: String,
        val sizeMatchStatus: String, // YES, NO, ERROR
        val checksumMatchStatus: String, // VERIFIED, FAILED, ERROR
        val savedFile: File?,
        val failureReason: String?
    )

    fun calculateChecksum(inputStream: InputStream): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logE("Failed to calculate SHA-256 checksum", e)
            "ERROR"
        }
    }

    fun calculateChecksum(file: File): String {
        return try {
            file.inputStream().use { calculateChecksum(it) }
        } catch (e: Exception) {
            logE("Failed to open input stream for file ${file.name}", e)
            "ERROR"
        }
    }

    fun prepareMediaPayloads(
        reportId: String,
        mediaFiles: List<File>,
        existingMediaItems: List<MeshMediaMetadata> = emptyList()
    ): Pair<List<MeshMediaMetadata>, Map<Long, Payload>> {
        val metadataList = mutableListOf<MeshMediaMetadata>()
        val payloadMap = mutableMapOf<Long, Payload>()

        mediaFiles.take(5).forEachIndexed { index, file ->
            if (!file.exists() || file.length() == 0L) {
                logW("Skipping invalid media file: ${file.absolutePath}")
                return@forEachIndexed
            }

            try {
                val sizeBytes = file.length()
                val checksum = calculateChecksum(file)
                val payload = Payload.fromFile(file)
                val payloadId = payload.id

                // Preserve existing mediaId if this media item was already announced in existingMediaItems
                val existingMeta = existingMediaItems.find {
                    it.sizeBytes == sizeBytes && it.checksum.equals(checksum, ignoreCase = true)
                } ?: existingMediaItems.find {
                    it.reportId == reportId && it.checksum.equals(checksum, ignoreCase = true)
                } ?: existingMediaItems.getOrNull(index)

                val mediaId = existingMeta?.mediaId ?: "MED_${UUID.randomUUID().toString().take(8)}"
                val filename = existingMeta?.filename ?: file.name
                val mimeType = existingMeta?.mimeType ?: (if (filename.endsWith(".png", true)) "image/png" else "image/jpeg")

                val meta = MeshMediaMetadata(
                    mediaId = mediaId,
                    reportId = reportId,
                    filename = filename,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    nearbyPayloadId = payloadId,
                    checksum = checksum
                )

                metadataList.add(meta)
                payloadMap[payloadId] = payload
                logI("MEDIA_PAYLOAD_CREATED: reportId=$reportId, mediaId=$mediaId, filename=$filename, expectedSize=$sizeBytes, expectedSha256=$checksum")
                logI("MEDIA_ANNOUNCED: reportId=$reportId, mediaId=$mediaId, payloadId=$payloadId, size=$sizeBytes bytes")
            } catch (e: Exception) {
                logE("Failed to create Payload.fromFile for ${file.name}", e)
            }
        }

        return Pair(metadataList, payloadMap)
    }

    fun verifyAndSaveMediaFromUri(
        context: Context,
        receivedUri: Uri,
        metadata: MeshMediaMetadata
    ): VerificationResult {
        val expectedSize = metadata.sizeBytes
        val expectedChecksum = metadata.checksum
        val extension = if (metadata.filename.endsWith(".png", true)) "png" else "jpg"
        val targetFilename = "media_${metadata.reportId}_${metadata.mediaId}.$extension"
        val targetFile = File(context.filesDir, targetFilename)

        try {
            if (targetFile.exists() && targetFile.length() == expectedSize) {
                val existingChecksum = calculateChecksum(targetFile)
                if (existingChecksum.equals(expectedChecksum, ignoreCase = true)) {
                    logI("MEDIA_DUPLICATE_DISCARDED: File already exists and verified for $targetFilename")
                    return VerificationResult(
                        success = true,
                        expectedSize = expectedSize,
                        actualSize = targetFile.length(),
                        expectedChecksum = expectedChecksum,
                        actualChecksum = existingChecksum,
                        sizeMatchStatus = "YES",
                        checksumMatchStatus = "VERIFIED",
                        savedFile = targetFile,
                        failureReason = null
                    )
                }
            }

            // Open stream via ContentResolver to safely read Nearby Content URI without EACCES
            val inputStream = context.contentResolver.openInputStream(receivedUri)
            if (inputStream == null) {
                val reason = "Unable to open input stream for URI: $receivedUri"
                logE("MEDIA_TRANSFER_FAILED: $reason")
                return VerificationResult(
                    success = false,
                    expectedSize = expectedSize,
                    actualSize = null,
                    expectedChecksum = expectedChecksum,
                    actualChecksum = "ERROR",
                    sizeMatchStatus = "ERROR",
                    checksumMatchStatus = "ERROR",
                    savedFile = null,
                    failureReason = reason
                )
            }

            inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val actualSize = targetFile.length()
            val sizeMatches = (actualSize == expectedSize)
            val sizeStatus = if (sizeMatches) "YES" else "NO"

            val actualChecksum = calculateChecksum(targetFile)
            val checksumMatches = actualChecksum.equals(expectedChecksum, ignoreCase = true)
            val checksumStatus = if (checksumMatches) "VERIFIED" else "FAILED"

            logI("MEDIA_CHECKSUM_VERIFY: reportId=${metadata.reportId}, mediaId=${metadata.mediaId}, expectedSize=$expectedSize, actualSize=$actualSize, sizeMatches=$sizeMatches, expectedSha256=$expectedChecksum, actualSha256=$actualChecksum, checksumMatches=$checksumMatches")

            if (!sizeMatches) {
                val reason = "Size mismatch: Expected $expectedSize B, got $actualSize B"
                logE("MEDIA_TRANSFER_FAILED: $reason")
                targetFile.delete()
                return VerificationResult(
                    success = false,
                    expectedSize = expectedSize,
                    actualSize = actualSize,
                    expectedChecksum = expectedChecksum,
                    actualChecksum = actualChecksum,
                    sizeMatchStatus = sizeStatus,
                    checksumMatchStatus = checksumStatus,
                    savedFile = null,
                    failureReason = reason
                )
            }

            if (!checksumMatches) {
                val reason = "SHA-256 mismatch: Expected $expectedChecksum, got $actualChecksum"
                logE("MEDIA_CHECKSUM_FAILED: $reason")
                targetFile.delete()
                return VerificationResult(
                    success = false,
                    expectedSize = expectedSize,
                    actualSize = actualSize,
                    expectedChecksum = expectedChecksum,
                    actualChecksum = actualChecksum,
                    sizeMatchStatus = sizeStatus,
                    checksumMatchStatus = checksumStatus,
                    savedFile = null,
                    failureReason = reason
                )
            }

            logI("MEDIA_TRANSFER_COMPLETED: Successfully verified & saved $targetFilename")
            return VerificationResult(
                success = true,
                expectedSize = expectedSize,
                actualSize = actualSize,
                expectedChecksum = expectedChecksum,
                actualChecksum = actualChecksum,
                sizeMatchStatus = sizeStatus,
                checksumMatchStatus = checksumStatus,
                savedFile = targetFile,
                failureReason = null
            )

        } catch (e: Exception) {
            val reason = "Unable to read received Nearby file: ${e.message}"
            logE(reason, e)
            targetFile.delete()
            return VerificationResult(
                success = false,
                expectedSize = expectedSize,
                actualSize = null,
                expectedChecksum = expectedChecksum,
                actualChecksum = "ERROR",
                sizeMatchStatus = "ERROR",
                checksumMatchStatus = "ERROR",
                savedFile = null,
                failureReason = reason
            )
        }
    }

    private fun logI(msg: String) {
        try { Log.i(TAG, msg) } catch (t: Throwable) { println("$TAG: $msg") }
    }

    private fun logW(msg: String) {
        try { Log.w(TAG, msg) } catch (t: Throwable) { println("$TAG: $msg") }
    }

    private fun logE(msg: String, throwable: Throwable? = null) {
        try { Log.e(TAG, msg, throwable) } catch (t: Throwable) { println("$TAG: $msg ${throwable?.message}") }
    }
}
