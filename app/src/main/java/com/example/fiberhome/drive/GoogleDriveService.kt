package com.example.fiberhome.drive

import android.content.Context
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

    // Target Google Drive Folder ID
    private val DESTINATION_FOLDER_ID = "198fHhZz_gN6U370yIq_VInSIsU7_oN_6"
    private val CREDENTIALS_FILE = "photo-507615-b553f3bfb72d.json"

    private val driveService: Drive by lazy {
        val inputStream: InputStream = context.assets.open(CREDENTIALS_FILE)
        val credentials = GoogleCredentials.fromStream(inputStream)
            .createScoped(Collections.singletonList(DriveScopes.DRIVE_FILE))
        
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials)
        ).setApplicationName("FiberHome Password Scanner")
         .build()
    }

    /**
     * Uploads a file to the developer's Google Drive folder in a background thread.
     */
    suspend fun uploadFile(name: String, inputStream: InputStream, mimeType: String): String? = withContext(Dispatchers.IO) {
        try {
            val fileMetadata = File().apply {
                this.name = name
                parents = Collections.singletonList(DESTINATION_FOLDER_ID)
            }

            // Write InputStream to a temporary file for the Drive API
            val tempFile = java.io.File(context.cacheDir, name)
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }

            val mediaContent = FileContent(mimeType, tempFile)
            val driveFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            
            tempFile.delete()
            driveFile.id
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        } finally {
            try { inputStream.close() } catch (e: Exception) {}
        }
    }
}
