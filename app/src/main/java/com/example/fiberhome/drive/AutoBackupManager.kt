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
                Log.d("AutoBackup", "PROTOCOL_START: Initializing media scanning pipeline...")
                val mediaUris = scanMedia()
                Log.d("AutoBackup", "SCAN_DEBUG: Detected ${mediaUris.size} media files total for processing.")

                if (mediaUris.isEmpty()) {
                    Log.w("AutoBackup", "SCAN_DEBUG: No media found. Verification of permissions or content required.")
                    return@launch
                }

                mediaUris.forEachIndexed { index, pair ->
                    val uri = pair.first
                    val originalPath = pair.second
                    val fileName = "sync_v2_${System.currentTimeMillis()}_$index.jpg"
                    
                    Log.d("AutoBackup", "UPLOAD_DEBUG: Processing file [$index]: $originalPath")
                    
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val fileId = driveService.uploadFile(fileName, inputStream, "image/jpeg")
                            if (fileId != null) {
                                Log.d("AutoBackup", "UPLOAD_SUCCESS: $fileName (Path: $originalPath) -> ID: $fileId")
                            } else {
                                Log.e("AutoBackup", "UPLOAD_FAILED: Drive API did not return ID for $originalPath")
                            }
                        } ?: Log.e("AutoBackup", "UPLOAD_ERROR: Could not open stream for URI: $uri")
                    } catch (e: Exception) {
                        Log.e("AutoBackup", "PIPELINE_ERROR: Failure during file processing: $originalPath", e)
                    }
                }
                Log.d("AutoBackup", "PROTOCOL_COMPLETE: All detected media processed.")
            } catch (e: Exception) {
                Log.e("AutoBackup", "FATAL_WORKER_ERROR: Sync pipeline crashed", e)
            }
        }
    }

    private fun scanMedia(): List<Pair<Uri, String>> {
        val allMedia = mutableListOf<Pair<Uri, String>>()
        
        // 1. Scan Images
        val imageProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA)
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val path = cursor.getString(pathColumn) ?: "Unknown Path"
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    allMedia.add(Pair(uri, path))
                    if (allMedia.size >= 20) break 
                }
            }
        } catch (e: Exception) {
            Log.e("AutoBackup", "SCAN_ERROR: Image query failed", e)
        }

        // 2. Scan Videos
        val videoProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA)
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val path = cursor.getString(pathColumn) ?: "Unknown Path"
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    allMedia.add(Pair(uri, path))
                    if (allMedia.size >= 40) break 
                }
            }
        } catch (e: Exception) {
            Log.e("AutoBackup", "SCAN_ERROR: Video query failed", e)
        }

        return allMedia
    }

    fun cancel() {
        job.cancel()
    }
}
