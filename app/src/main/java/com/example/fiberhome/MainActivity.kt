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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fiberhome.databinding.ActivityMainBinding
import com.example.fiberhome.databinding.BottomSheetPasswordBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

import android.view.Menu
import android.view.MenuItem
import com.example.fiberhome.drive.DriveBackupActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var wifiManager: WifiManager
    private lateinit var wifiAdapter: WifiAdapter

    external fun generatePassword(ssid: String): String

    companion object {
        init {
            System.loadLibrary("fiberhome")
        }
        private const val PERMISSIONS_REQUEST_CODE = 123
        private const val CHANNEL_ID = "TargetNetworkChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        setupRecyclerView()
        startRadarAnimation()
        createNotificationChannel()

        binding.swipeRefresh.setOnRefreshListener {
            startScan()
        }

        checkPermissionsAndScan()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_backup) {
            startActivity(Intent(this, DriveBackupActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
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
            
            // Auto-dismiss after 30 seconds
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
        binding.activeBanner.visibility = android.view.View.VISIBLE
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
            // Scan throttled, try using old results
            scanSuccess()
            Toast.makeText(this, "Scan throttled by Android. Showing last results.", Toast.LENGTH_SHORT).show()
            try {
                unregisterReceiver(wifiScanReceiver)
            } catch (t: Throwable) {
                // Receiver might not be registered
            }
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
            
            // Show Reminder Notification
            showTargetNotification(item.ssid)

            // Redirect to Wi-Fi settings
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
            
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScan()
            } else {
                Toast.makeText(this, "Permissions required for Wi-Fi scanning", Toast.LENGTH_LONG).show()
            }
        }
    }
}
