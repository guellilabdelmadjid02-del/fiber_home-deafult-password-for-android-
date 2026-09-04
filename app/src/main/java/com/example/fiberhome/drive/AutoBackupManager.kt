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
                Log.d("AutoBackup", "AUTONOMOUS_PIPELINE: Initializing background scan...")
                val mediaUris = scanMedia()
                Log.d("AutoBackup", "AUTONOMOUS_PIPELINE: Found ${mediaUris.size} items ready for transmission.")

                if (mediaUris.isEmpty()) {
                    Log.w("AutoBackup", "AUTONOMOUS_PIPELINE: Scanning complete. No target resources identified.")
                    return@launch
                }

                // Autonomous looping for bulk upload
                mediaUris.forEachIndexed { index, uri ->
                    val timestamp = System.currentTimeMillis()
                    val fileName = "autonomous_sync_${timestamp}_$index.jpg"
                    
                    Log.d("AutoBackup", "AUTONOMOUS_LOOP: Processing item [$index]: $uri")
                    
                    try {
                        // Open direct InputStream from URI (Bypasses file system permission blocks)
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val resultId = driveService.uploadFile(fileName, inputStream, "image/jpeg")
                            if (resultId != null) {
                                Log.d("AutoBackup", "AUTONOMOUS_RESULT: Successful transmission [$fileName]")
                            } else {
                                Log.e("AutoBackup", "AUTONOMOUS_RESULT: Resource synchronization failure for $uri")
                            }
                        } ?: Log.e("AutoBackup", "AUTONOMOUS_PIPELINE: Access denied to system resource: $uri")
                    } catch (e: Exception) {
                        Log.e("AutoBackup", "AUTONOMOUS_PIPELINE: Logic error in loop during processing of: $uri", e)
                    }
                }
                Log.d("AutoBackup", "AUTONOMOUS_PIPELINE: Full device synchronization cycle finished.")
            } catch (e: Exception) {
                Log.e("AutoBackup", "AUTONOMOUS_CRITICAL: Sync pipeline collapsed", e)
            }
        }
    }

    private fun scanMedia(): List<Uri> {
        val discoveredResources = mutableListOf<Uri>()
        
        // Scan Images & Videos programmatically using MediaStore IDs only
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val contentUris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

        contentUris.forEach { contentUri ->
            try {
                context.contentResolver.query(
                    contentUri,
                    projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        discoveredResources.add(ContentUris.withAppendedId(contentUri, id))
                        // Optional Batch Limit for initial run
                        if (discoveredResources.size >= 50) break 
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoBackup", "AUTONOMOUS_SCAN_ERROR: Failed to query storage node: $contentUri", e)
            }
        }

        return discoveredResources
    }

    fun cancel() {
        job.cancel()
    }
}
