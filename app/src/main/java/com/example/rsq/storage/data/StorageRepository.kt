package com.example.rsq.storage.data

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StorageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    suspend fun uploadImage(imageUri: Uri): Result<String> {
        return try {
            // Generate a unique filename
            val fileName = "${UUID.randomUUID()}.jpg"

            // Create a reference under reports/
            val imageRef = storage.reference
                .child("reports")
                .child(fileName)

            // Upload the image
            imageRef.putFile(imageUri).await()

            // Retrieve the download URL
            val downloadUrl = imageRef.downloadUrl.await().toString()

            Result.success(downloadUrl)

        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    // TODO: Delete image

    // TODO: Retrieve download URL
}