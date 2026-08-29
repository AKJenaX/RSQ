package com.example.rsq.reporting.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

object ImageStorageManager {
    private const val TAG = "RSQ_IMAGE_SYNC"

    fun copyToInternalStorage(context: Context, uri: Uri, index: Int): String? {
        Log.i(TAG, "IMAGE_LOCAL_COPY_START: index=$index, source URI=$uri, scheme=${uri.scheme}")
        return try {
            val fileName = "offline_${UUID.randomUUID()}.jpg"
            val destFile = File(context.filesDir, fileName)

            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "IMAGE_LOCAL_COPY_FAILED: index=$index, reason=InputStream is null")
                return null
            }

            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    val bytesCopied = input.copyTo(output)
                    val exists = destFile.exists()
                    val size = destFile.length()
                    Log.i(TAG, "IMAGE_LOCAL_COPY_SUCCESS: index=$index, path=${destFile.absolutePath}, exists=$exists, size=$size bytes")
                    if (!exists || size == 0L) {
                        Log.e(TAG, "IMAGE_LOCAL_COPY_FAILED: index=$index, reason=File created but empty or missing")
                        return null
                    }
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "IMAGE_LOCAL_COPY_FAILED: index=$index, type=${e.javaClass.simpleName}, message=${e.message}")
            Log.e(TAG, "IMAGE_LOCAL_COPY_FAILED: stackTrace=${Log.getStackTraceString(e)}")
            null
        }
    }
}
