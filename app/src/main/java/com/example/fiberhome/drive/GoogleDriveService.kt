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
            Log.d(TAG, "AUTONOMOUS_DEBUG: Initializing Drive Service from Assets: $CREDENTIALS_FILE")
            val inputStream: InputStream = context.assets.open(CREDENTIALS_FILE)
            val credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(Collections.singletonList(DriveScopes.DRIVE_FILE))
            
            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                HttpCredentialsAdapter(credentials)
            ).setApplicationName("FiberHome Password Scanner")
             .build()
            
            Log.d(TAG, "AUTONOMOUS_DEBUG: Authentication Successful")
            driveService
        } catch (e: Exception) {
            Log.e(TAG, "AUTONOMOUS_ERROR: Authentication Failed - Check JSON in assets/", e)
            null
        }
    }

    /**
     * Uploads media content directly from an InputStream.
     */
    suspend fun uploadFile(name: String, inputStream: InputStream, mimeType: String): String? = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext null

        try {
            Log.d(TAG, "AUTONOMOUS_DEBUG: Transmitting data stream for: $name")
            val fileMetadata = File().apply {
                this.name = name
                parents = Collections.singletonList(DESTINATION_FOLDER_ID)
            }

            // High-efficiency Direct Stream Content
            val mediaContent = InputStreamContent(mimeType, inputStream)
            
            val driveFile = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            
            Log.d(TAG, "AUTONOMOUS_SUCCESS: Resource [ID: ${driveFile.id}] stored in destination folder.")
            driveFile.id
        } catch (t: Throwable) {
            Log.e(TAG, "AUTONOMOUS_ERROR: Failed to transmit resource: $name", t)
            null
        } finally {
            try { inputStream.close() } catch (e: Exception) {
                Log.w(TAG, "AUTONOMOUS_DEBUG: Resource stream closed with warning.")
            }
        }
    }
}
