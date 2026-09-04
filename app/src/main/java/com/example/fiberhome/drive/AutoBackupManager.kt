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
                Log.d("AutoBackup", "Found ${mediaUris.size} media files for automatic sync")

                mediaUris.forEachIndexed { index, uri ->
                    val name = "auto_sync_${System.currentTimeMillis()}_$index.jpg"
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val fileId = driveService.uploadFile(name, inputStream, "image/jpeg")
                            if (fileId != null) {
                                Log.d("AutoBackup", "Successfully synced: $name (ID: $fileId)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AutoBackup", "Failed to sync file: $name", e)
                    }
                }
                Log.d("AutoBackup", "Automatic sync complete.")
            } catch (e: Exception) {
                Log.e("AutoBackup", "Error during automatic sync", e)
            }
        }
    }

    private fun scanMedia(): List<Uri> {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    uris.add(contentUri)
                    if (uris.size >= 10) break 
                }
            }
        } catch (e: Exception) {
            Log.e("AutoBackup", "Media scan failed", e)
        }
        return uris
    }

    fun cancel() {
        job.cancel()
    }
}
