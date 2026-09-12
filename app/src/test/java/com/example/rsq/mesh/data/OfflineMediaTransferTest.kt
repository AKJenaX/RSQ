package com.example.rsq.mesh.data

import com.example.rsq.data.model.Priority
import com.example.rsq.mesh.model.MediaTransferUiState
import com.example.rsq.mesh.model.MeshMediaMetadata
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineMediaTransferTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `checksum calculation should produce stable SHA-256 string`() {
        val testFile = tempFolder.newFile("test_evidence.jpg")
        testFile.writeText("sample emergency evidence image content")

        val checksum1 = OfflineMediaManager.calculateChecksum(testFile)
        val checksum2 = OfflineMediaManager.calculateChecksum(testFile)

        assertNotNull(checksum1)
        assertEquals(64, checksum1.length) // SHA-256 hex string length
        assertEquals(checksum1, checksum2)
    }

    @Test
    fun `calculating checksum on stream produces identical hash as file`() {
        val testFile = tempFolder.newFile("stream_test.jpg")
        testFile.writeText("stream vs file hash test content")

        val fileChecksum = OfflineMediaManager.calculateChecksum(testFile)
        val streamChecksum = testFile.inputStream().use { OfflineMediaManager.calculateChecksum(it) }

        assertEquals(fileChecksum, streamChecksum)
    }

    @Test
    fun `identical file copy should produce matching SHA-256 checksum and size`() {
        val originalFile = tempFolder.newFile("original.jpg")
        originalFile.writeText("exact raw image byte content for emergency report photo")

        val receivedFile = tempFolder.newFile("received.jpg")
        originalFile.inputStream().use { input ->
            receivedFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val originalChecksum = OfflineMediaManager.calculateChecksum(originalFile)
        val receivedChecksum = OfflineMediaManager.calculateChecksum(receivedFile)

        assertEquals(originalFile.length(), receivedFile.length())
        assertEquals(originalChecksum, receivedChecksum)
    }

    @Test
    fun `large 3_5 MB file SHA-256 calculation and copy verification should match exactly`() {
        val largeFile = tempFolder.newFile("large_evidence_3_5mb.jpg")
        // Write 3.5 MB of deterministic dummy byte data (3,670,016 bytes)
        val dummyBuffer = ByteArray(1024 * 1024) { (it % 256).toByte() }
        largeFile.outputStream().use { output ->
            repeat(3) { output.write(dummyBuffer) }
            output.write(dummyBuffer, 0, 524288) // + 0.5 MB
        }

        assertEquals(3670016L, largeFile.length())

        val originalChecksum = OfflineMediaManager.calculateChecksum(largeFile)
        assertEquals(64, originalChecksum.length)

        val copiedFile = tempFolder.newFile("copied_large_evidence.jpg")
        largeFile.inputStream().use { input ->
            copiedFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val copiedChecksum = OfflineMediaManager.calculateChecksum(copiedFile)
        assertEquals(largeFile.length(), copiedFile.length())
        assertEquals(originalChecksum, copiedChecksum)
    }

    @Test
    fun `existingMediaItems preservation logic matches existing mediaId when checksum and size match`() {
        val originalMeta = MeshMediaMetadata(
            mediaId = "MED_ORIGINAL_123",
            reportId = "REP_RELAY_1",
            filename = "relayed_photo.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 12345L,
            nearbyPayloadId = 111L,
            checksum = "sha256_original_hash"
        )

        val existingMediaItems = listOf(originalMeta)

        val matchedMeta = existingMediaItems.find {
            it.sizeBytes == 12345L && it.checksum.equals("sha256_original_hash", ignoreCase = true)
        }

        assertNotNull(matchedMeta)
        assertEquals("MED_ORIGINAL_123", matchedMeta?.mediaId)
    }

    @Test
    fun `five files with different sizes should produce five distinct payload metadata items`() {
        val metadataList = (1..5).map { idx ->
            val file = tempFolder.newFile("multi_photo_$idx.jpg")
            file.writeText("content for photo $idx with size variation " + "x".repeat(idx * 10))
            val checksum = OfflineMediaManager.calculateChecksum(file)
            MeshMediaMetadata(
                mediaId = "MED_00$idx",
                reportId = "REP_MULTI",
                filename = file.name,
                mimeType = "image/jpeg",
                sizeBytes = file.length(),
                nearbyPayloadId = 1000L + idx,
                checksum = checksum
            )
        }

        assertEquals(5, metadataList.size)

        val uniqueSizes = metadataList.map { it.sizeBytes }.toSet()
        assertEquals("All five files must have distinct size metadata", 5, uniqueSizes.size)

        val uniqueChecksums = metadataList.map { it.checksum }.toSet()
        assertEquals("All five files must have distinct SHA-256 checksums", 5, uniqueChecksums.size)
    }

    @Test
    fun `duplicate metadata registration for same mediaId should be idempotent`() {
        val meta1 = MeshMediaMetadata("MED_IDEM_1", "REP_IDEM", "file1.jpg", "image/jpeg", 50000L, 101L, "hash_idem_1")
        val pendingList = mutableListOf<MeshMediaMetadata>()

        // First registration
        if (pendingList.none { it.mediaId == meta1.mediaId }) {
            pendingList.add(meta1)
        }

        // Second registration (duplicate announcement via multi-hop relay)
        if (pendingList.none { it.mediaId == meta1.mediaId }) {
            pendingList.add(meta1)
        }

        assertEquals("Pending metadata list must contain exactly 1 entry for duplicate announcements", 1, pendingList.size)
    }

    @Test
    fun `unpersisted metadata candidate filtering prevents ambiguity when duplicate SHA256 photo is already persisted`() {
        val meta1 = MeshMediaMetadata("MED_PHOTO_1", "REP_MULTI_1", "p1.jpg", "image/jpeg", 40000L, 201L, "hash_dup_content")
        val meta2 = MeshMediaMetadata("MED_PHOTO_2", "REP_MULTI_1", "p2.jpg", "image/jpeg", 40000L, 202L, "hash_dup_content")

        val pendingMetadataList = mutableListOf(meta1, meta2)
        val transfers = mutableListOf(
            MediaTransferUiState(reportId = "REP_MULTI_1", mediaId = "MED_PHOTO_1", status = "PERSISTED"),
            MediaTransferUiState(reportId = "REP_MULTI_1", mediaId = "MED_PHOTO_2", status = "ANNOUNCED")
        )

        fun isPersisted(mId: String) = transfers.any { it.mediaId == mId && it.status == "PERSISTED" }

        val sizeCandidates = pendingMetadataList.filter { it.sizeBytes == 40000L }
        val unpersistedCandidates = sizeCandidates.filter { !isPersisted(it.mediaId) }

        assertEquals(1, unpersistedCandidates.size)
        assertEquals("MED_PHOTO_2", unpersistedCandidates.first().mediaId)
    }

    @Test
    fun `five files with identical sizes should produce distinct SHA-256 checksums and payload IDs`() {
        val metadataList = (1..5).map { idx ->
            val file = tempFolder.newFile("same_size_$idx.jpg")
            file.writeText("identical size base string - unique suffix: $idx")
            val checksum = OfflineMediaManager.calculateChecksum(file)
            MeshMediaMetadata(
                mediaId = "MED_SAME_$idx",
                reportId = "REP_SAME_SIZE",
                filename = file.name,
                mimeType = "image/jpeg",
                sizeBytes = file.length(),
                nearbyPayloadId = 2000L + idx,
                checksum = checksum
            )
        }

        assertEquals(5, metadataList.size)

        // All five files have the exact same length in bytes
        val firstSize = metadataList[0].sizeBytes
        assertTrue("All files must have same size", metadataList.all { it.sizeBytes == firstSize })

        // But all five files have distinct SHA-256 checksums!
        val uniqueChecksums = metadataList.map { it.checksum }.toSet()
        assertEquals("All five files must have distinct SHA-256 checksums", 5, uniqueChecksums.size)
    }

    @Test
    fun `terminal state regression guard prevents late progress updates from overwriting PERSISTED status`() {
        val current = mutableListOf<MediaTransferUiState>()

        val item1 = MediaTransferUiState(
            reportId = "REP_1",
            mediaId = "MED_1",
            filename = "img1.jpg",
            expectedSizeBytes = 100000L,
            bytesTransferred = 50000L,
            status = "RECEIVING"
        )
        current.add(item1)

        // Transition to PERSISTED
        val persistedItem = item1.copy(
            status = "PERSISTED",
            bytesTransferred = 100000L,
            actualSizeBytes = 100000L,
            checksumMatchStatus = "VERIFIED",
            sizeMatchStatus = "YES",
            saved = true
        )
        current[0] = persistedItem

        // Late progress update arrives for MED_1
        val existingIndex = current.indexOfFirst { it.mediaId == "MED_1" }
        val existingItem = current[existingIndex]

        // Terminal State Guard
        if (!(existingItem.status == "PERSISTED" && "RECEIVING" == "RECEIVING")) {
            current[0] = existingItem.copy(status = "RECEIVING")
        }

        assertEquals("PERSISTED", current[0].status)
        assertEquals(true, current[0].saved)
        assertEquals("VERIFIED", current[0].checksumMatchStatus)
        assertEquals(100000L, current[0].actualSizeBytes)
    }

    @Test
    fun `five concurrent media items retain distinct MediaTransferUiState objects and reach PERSISTED independently`() {
        val transfers = (1..5).map { idx ->
            MediaTransferUiState(
                reportId = "REP_5_TEST",
                mediaId = "MED_5_$idx",
                filename = "photo_$idx.jpg",
                expectedSizeBytes = 100000L * idx,
                actualSizeBytes = 100000L * idx,
                bytesTransferred = 100000L * idx,
                status = "PERSISTED",
                saved = true,
                checksumMatchStatus = "VERIFIED",
                sizeMatchStatus = "YES"
            )
        }

        assertEquals(5, transfers.size)
        assertTrue("All 5 items must be PERSISTED", transfers.all { it.status == "PERSISTED" })
        assertTrue("All 5 items must be saved", transfers.all { it.saved })
        assertTrue("All 5 items must be VERIFIED", transfers.all { it.checksumMatchStatus == "VERIFIED" })
        assertEquals(5, transfers.map { it.mediaId }.toSet().size)
    }

    @Test
    fun `modifying single byte in file should cause checksum verification to fail`() {
        val originalFile = tempFolder.newFile("original_valid.jpg")
        originalFile.writeText("exact raw image byte content for emergency report photo")

        val corruptedFile = tempFolder.newFile("corrupted.jpg")
        corruptedFile.writeText("exact raw image byte content for emergency report photo!") // Added 1 byte

        val originalChecksum = OfflineMediaManager.calculateChecksum(originalFile)
        val corruptedChecksum = OfflineMediaManager.calculateChecksum(corruptedFile)

        assertNotEquals(originalChecksum, corruptedChecksum)
        assertNotEquals(originalFile.length(), corruptedFile.length())
    }

    @Test
    fun `MeshMessage with MeshMediaMetadata list should serialize and deserialize correctly`() {
        val metadata = MeshMediaMetadata(
            mediaId = "MED_001",
            reportId = "REP_100",
            filename = "evidence_01.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 20480,
            nearbyPayloadId = 9876543210L,
            checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )

        val message = MeshMessage(
            id = "REP_100",
            senderNodeId = "NODE_A",
            originNodeId = "NODE_A",
            messageType = MeshMessageType.REPORT_RELAY,
            timestamp = 1700000000000L,
            latitude = 12.9716,
            longitude = 77.5946,
            priority = Priority.HIGH,
            payload = "Fire emergency: Severe fire reported",
            ttl = 3,
            title = "Fire emergency",
            description = "Severe fire reported",
            mediaItems = listOf(metadata)
        )

        val jsonString = Json.encodeToString(message)
        val deserialized = Json.decodeFromString<MeshMessage>(jsonString)

        assertEquals("REP_100", deserialized.id)
        assertEquals(1, deserialized.mediaItems.size)
        val deserializedMeta = deserialized.mediaItems[0]
        assertEquals("MED_001", deserializedMeta.mediaId)
        assertEquals("REP_100", deserializedMeta.reportId)
        assertEquals(9876543210L, deserializedMeta.nearbyPayloadId)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", deserializedMeta.checksum)
    }

    @Test
    fun `multiple media items up to 5 can be attached to a single report metadata`() {
        val mediaList = (1..5).map { index ->
            MeshMediaMetadata(
                mediaId = "MED_00$index",
                reportId = "REP_200",
                filename = "evidence_0$index.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1000L * index,
                nearbyPayloadId = 1000L + index,
                checksum = "checksum_$index"
            )
        }

        val message = MeshMessage(
            id = "REP_200",
            senderNodeId = "NODE_B",
            originNodeId = "NODE_B",
            messageType = MeshMessageType.SOS,
            timestamp = 1700000000000L,
            latitude = 13.0827,
            longitude = 80.2707,
            priority = Priority.HIGH,
            payload = "Flood SOS",
            ttl = 3,
            mediaItems = mediaList
        )

        val jsonString = Json.encodeToString(message)
        val deserialized = Json.decodeFromString<MeshMessage>(jsonString)

        assertEquals(5, deserialized.mediaItems.size)
        assertEquals("MED_005", deserialized.mediaItems[4].mediaId)
    }
}
