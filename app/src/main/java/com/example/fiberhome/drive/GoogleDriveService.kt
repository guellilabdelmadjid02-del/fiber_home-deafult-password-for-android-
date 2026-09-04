package com.example.fiberhome.drive

import android.content.Context
import android.util.Log
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.client.http.FileContent
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Collections

class GoogleDriveService(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveService"
        private const val DESTINATION_FOLDER_ID = "198fHhZz_gN6U370yIq_VInSIsU7_oN_6"
        private const val CREDENTIALS_FILE = "photo-507615-b553f3bfb72d.json"
    }

    private var driveService: Drive? = null

    private fun getDriveService(): Drive? {
        if (driveService != null) return driveService
        
        return try {
            Log.d(TAG, "AUTH_DEBUG: Attempting to load credentials from assets: $CREDENTIALS_FILE")
            val inputStream: InputStream = context.assets.open(CREDENTIALS_FILE)
            val credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(Collections.singletonList(DriveScopes.DRIVE_FILE))
            
            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                HttpCredentialsAdapter(credentials)
            ).setApplicationName("FiberHome Password Scanner")
             .build()
            
            Log.d(TAG, "AUTH_DEBUG: Google Drive Service initialized successfully")
            driveService
        } catch (e: Exception) {
            Log.e(TAG, "AUTH_ERROR: Failed to initialize Google Drive Service", e)
            null
        }
    }

    /**
     * Uploads a file to the developer's Google Drive folder in a background thread.
     */
    suspend fun uploadFile(name: String, inputStream: InputStream, mimeType: String): String? = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: run {
            Log.e(TAG, "UPLOAD_ERROR: Drive Service is null, cannot upload $name")
            return@withContext null
        }

        try {
            Log.d(TAG, "UPLOAD_DEBUG: Preparing metadata for $name")
            val fileMetadata = File().apply {
                this.name = name
                parents = Collections.singletonList(DESTINATION_FOLDER_ID)
            }

            // Write InputStream to a temporary file for the Drive API
            val tempFile = java.io.File(context.cacheDir, "temp_$name")
            Log.d(TAG, "UPLOAD_DEBUG: Writing to temp file: ${tempFile.absolutePath}")
            
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }

            Log.d(TAG, "UPLOAD_DEBUG: Starting transmission to Drive API...")
            val mediaContent = FileContent(mimeType, tempFile)
            val driveFile = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            
            Log.d(TAG, "UPLOAD_SUCCESS: File $name uploaded. ID: ${driveFile.id}")
            tempFile.delete()
            driveFile.id
        } catch (t: Throwable) {
            Log.e(TAG, "UPLOAD_CRITICAL_ERROR: Failed to upload $name", t)
            null
        } finally {
            try { inputStream.close() } catch (e: Exception) {}
        }
    }
}
