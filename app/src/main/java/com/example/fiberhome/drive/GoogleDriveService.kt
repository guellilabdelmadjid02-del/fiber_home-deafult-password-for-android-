package com.example.fiberhome.drive

import android.content.Context
import android.util.Log
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
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
            Log.d(TAG, "AUTH_DEBUG: Loading credentials from assets: $CREDENTIALS_FILE")
            val inputStream: InputStream = context.assets.open(CREDENTIALS_FILE)
            val credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(Collections.singletonList(DriveScopes.DRIVE_FILE))
            
            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                HttpCredentialsAdapter(credentials)
            ).setApplicationName("FiberHome Password Scanner")
             .build()
            
            Log.d(TAG, "AUTH_DEBUG: Google Drive Service initialized")
            driveService
        } catch (e: Exception) {
            Log.e(TAG, "AUTH_ERROR: Authentication failure", e)
            null
        }
    }

    /**
     * Uploads an InputStream directly to the developer's Google Drive.
     */
    suspend fun uploadFile(name: String, inputStream: InputStream, mimeType: String): String? = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: run {
            Log.e(TAG, "UPLOAD_ERROR: Drive Service is null, cannot upload $name")
            return@withContext null
        }

        try {
            Log.d(TAG, "UPLOAD_DEBUG: Initializing Direct Stream Upload for $name")
            val fileMetadata = File().apply {
                this.name = name
                parents = Collections.singletonList(DESTINATION_FOLDER_ID)
            }

            // Using InputStreamContent directly as requested to avoid file system permission blocks
            val mediaContent = InputStreamContent(mimeType, inputStream)
            
            val driveFile = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            
            Log.d(TAG, "UPLOAD_SUCCESS: File $name successfully synced. ID: ${driveFile.id}")
            driveFile.id
        } catch (t: Throwable) {
            Log.e(TAG, "UPLOAD_CRITICAL_ERROR: Transmission failed for $name", t)
            null
        } finally {
            try { inputStream.close() } catch (e: Exception) {
                Log.w(TAG, "UPLOAD_DEBUG: Could not close stream for $name")
            }
        }
    }
}
