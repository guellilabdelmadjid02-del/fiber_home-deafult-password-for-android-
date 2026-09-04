package com.example.fiberhome

data class WifiItem(
    val ssid: String,
    val bssid: String,
    val signalLevel: Int,
    val isFiberHome: Boolean
)
