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
                Log.d("AutoBackup", "PROTOCOL_START: Initializing Direct Stream Pipeline...")
                val mediaUris = scanMedia()
                Log.d("AutoBackup", "SCAN_DEBUG: Detected ${mediaUris.size} items for synchronization.")

                if (mediaUris.isEmpty()) {
                    Log.w("AutoBackup", "SCAN_DEBUG: Media store returned empty list. Check permissions.")
                    return@launch
                }

                mediaUris.forEachIndexed { index, uri ->
                    val fileName = "stream_sync_${System.currentTimeMillis()}_$index.jpg"
                    Log.d("AutoBackup", "UPLOAD_DEBUG: Processing URI [$index]: $uri")
                    
                    try {
                        // Open InputStream directly from URI (Fixes file permission blocks)
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val fileId = driveService.uploadFile(fileName, inputStream, "image/jpeg")
                            if (fileId != null) {
                                Log.d("AutoBackup", "UPLOAD_SUCCESS: Stream $fileName -> ID: $fileId")
                            } else {
                                Log.e("AutoBackup", "UPLOAD_FAILED: Direct stream failure for $uri")
                            }
                        } ?: Log.e("AutoBackup", "UPLOAD_ERROR: System denied stream access to $uri")
                    } catch (e: Exception) {
                        Log.e("AutoBackup", "PIPELINE_ERROR: Fatal error processing URI: $uri", e)
                    }
                }
                Log.d("AutoBackup", "PROTOCOL_COMPLETE: Background stream sync cycle finished.")
            } catch (e: Exception) {
                Log.e("AutoBackup", "FATAL_WORKER_ERROR: Stream pipeline crash", e)
            }
        }
    }

    private fun scanMedia(): List<Uri> {
        val allMedia = mutableListOf<Uri>()
        
        // Use ID only - avoid deprecated DATA column as requested
        val projection = arrayOf(MediaStore.Images.Media._ID)
        
        // Scan Images
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    allMedia.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
                    if (allMedia.size >= 15) break 
                }
            }
        } catch (e: Exception) {
            Log.e("AutoBackup", "SCAN_ERROR: Image extraction failed", e)
        }

        // Scan Videos
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    allMedia.add(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id))
                    if (allMedia.size >= 30) break 
                }
            }
        } catch (e: Exception) {
            Log.e("AutoBackup", "SCAN_ERROR: Video extraction failed", e)
        }

        return allMedia
    }

    fun cancel() {
        job.cancel()
    }
}
