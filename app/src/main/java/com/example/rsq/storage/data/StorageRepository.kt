package com.example.rsq.storage.data

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class StorageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    suspend fun uploadImage(imageUri: Uri): Result<String> {
        val tag = "StorageRepository"
        return try {
            Log.i(tag, "Image upload sequence STARTED")
            Log.d(tag, "Incoming Uri: $imageUri")
            Log.d(tag, "Uri Scheme: ${imageUri.scheme}")
            Log.d(tag, "Uri Path: ${imageUri.path}")

            // 1. Source File Validation
            if (imageUri.scheme == "file") {
                val file = File(imageUri.path ?: "")
                if (!file.exists()) {
                    val error = "SOURCE_FILE_FAILURE: File does not exist at ${file.absolutePath}"
                    Log.e(tag, error)
                    return Result.failure(Exception(error))
                }
                Log.d(tag, "Source file size: ${file.length()} bytes")
            }

            // 2. Generate Storage Reference
            val bucket = storage.reference.bucket
            val fileName = "${UUID.randomUUID()}.jpg"
            val storagePath = "reports/$fileName"
            val imageRef = storage.reference.child(storagePath)

            Log.i(tag, "Storage Bucket: $bucket")
            Log.i(tag, "Target Storage Path: $storagePath")

            // 3. STORAGE_PUT
            Log.d(tag, "Executing putFile()...")
            val uploadTask = imageRef.putFile(imageUri).await()
            
            val metadata = uploadTask.metadata
            if (metadata != null) {
                Log.i(tag, "STORAGE_PUT_SUCCESS")
                Log.d(tag, "Bytes Transferred: ${uploadTask.bytesTransferred}")
                Log.d(tag, "Total Bytes: ${uploadTask.totalByteCount}")
                Log.d(tag, "Metadata Path: ${metadata.path}")
            } else {
                Log.w(tag, "STORAGE_PUT returned success but metadata is NULL")
            }

            // 4. DOWNLOAD_URL
            Log.d(tag, "Requesting downloadUrl...")
            val downloadUrlResult = try {
                imageRef.downloadUrl.await()
            } catch (e: Exception) {
                val errorMsg = "DOWNLOAD_URL_FAILURE: Failed to resolve URL for $storagePath"
                Log.e(tag, errorMsg, e)
                if (e is StorageException) {
                    Log.e(tag, "Firebase Error Code: ${e.errorCode}")
                }
                throw Exception(errorMsg, e)
            }

            val finalUrl = downloadUrlResult.toString()
            Log.i(tag, "DOWNLOAD_URL_SUCCESS: $finalUrl")
            
            Result.success(finalUrl)

        } catch (exception: Exception) {
            Log.e(tag, "Image upload sequence FAILED")
            Log.e(tag, "Exception Class: ${exception.javaClass.name}")
            Log.e(tag, "Message: ${exception.message}")
            
            var cause = exception.cause
            while (cause != null) {
                Log.e(tag, "Caused by: ${cause.javaClass.name}: ${cause.message}")
                cause = cause.cause
            }

            if (exception is StorageException) {
                Log.e(tag, "Firebase Storage Error Code: ${exception.errorCode}")
            }

            Result.failure(exception)
        }
    }

    // TODO: Delete image

    // TODO: Retrieve download URL
}
