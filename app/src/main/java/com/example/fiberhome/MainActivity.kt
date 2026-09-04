package com.example.fiberhome

import android.Manifest
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
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.fiberhome.databinding.ActivityMainBinding
import com.example.fiberhome.databinding.BottomSheetPasswordBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        setupRecyclerView()
        startRadarAnimation()

        binding.swipeRefresh.setOnRefreshListener {
            startScan()
        }

        checkPermissionsAndScan()
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
            showPasswordDialog(wifiItem)
        }
        binding.recyclerView.adapter = wifiAdapter
    }

    private fun checkPermissionsAndScan() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
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
