package com.example.rsq.storage.data

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class StorageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    private val TAG = "RSQ_IMAGE_SYNC"

    suspend fun uploadImage(imageUri: Uri, reportId: String? = null, index: Int = 0): Result<String> {
        val currentUser = FirebaseAuth.getInstance().currentUser
        Log.i(TAG, "STORAGE_UPLOAD_START: reportId=$reportId, imageIndex=$index, authUid=${currentUser?.uid}, URI=$imageUri")

        if (currentUser == null) {
            val error = "STORAGE_UPLOAD_FAILED: User not authenticated"
            Log.e(TAG, error)
            return Result.failure(Exception(error))
        }

        return try {
            // 1. Source File Validation
            if (imageUri.scheme == "file") {
                val path = imageUri.path ?: ""
                val file = File(path)
                if (!file.exists()) {
                    val error = "STORAGE_UPLOAD_FAILED: Local file does not exist at $path"
                    Log.e(TAG, error)
                    return Result.failure(Exception(error))
                }
                Log.i(TAG, "STORAGE_UPLOAD_FILE_INFO: index=$index, size=${file.length()} bytes, path=$path")
            } else {
                Log.w(TAG, "STORAGE_UPLOAD_URI_SCHEME: index=$index, scheme=${imageUri.scheme} (Expected file:// for background sync)")
            }

            // 2. Generate Storage Reference
            val fileName = "${UUID.randomUUID()}.jpg"
            val storagePath = if (reportId != null) "reports/$reportId/$fileName" else "reports/$fileName"
            val imageRef = storage.reference.child(storagePath)
            Log.i(TAG, "STORAGE_REFERENCE_CREATED: index=$index, path=$storagePath")

            // 3. STORAGE_PUT
            Log.i(TAG, "STORAGE_UPLOAD_PUT_START: index=$index")
            imageRef.putFile(imageUri).await()
            Log.i(TAG, "STORAGE_UPLOAD_SUCCESS: index=$index, StoragePath=$storagePath")

            // 4. DOWNLOAD_URL
            Log.i(TAG, "DOWNLOAD_URL_START: index=$index")
            val downloadUrlResult = imageRef.downloadUrl.await()
            val finalUrl = downloadUrlResult.toString()
            Log.i(TAG, "DOWNLOAD_URL_SUCCESS: index=$index, URL=$finalUrl")

            Result.success(finalUrl)

        } catch (exception: Exception) {
            Log.e(TAG, "STORAGE_UPLOAD_FAILED: index=$index, type=${exception.javaClass.simpleName}, message=${exception.message}")
            if (exception is StorageException) {
                Log.e(TAG, "STORAGE_UPLOAD_FAILED: Firebase Code=${exception.errorCode}")
            }
            Log.e(TAG, "STORAGE_UPLOAD_FAILED: stackTrace=${Log.getStackTraceString(exception)}")
            Result.failure(exception)
        }
    }
}
