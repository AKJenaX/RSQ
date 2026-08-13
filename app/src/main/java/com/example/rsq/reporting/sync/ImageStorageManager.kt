package com.example.rsq.reporting.sync

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object ImageStorageManager {
    fun copyToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val fileName = "offline_${UUID.randomUUID()}.jpg"
            val destFile = File(context.filesDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
