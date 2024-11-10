package com.realme.callrecord

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import androidx.core.content.ContextCompat

class NetworkChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
        if (activeNetwork?.isConnected == true) {
            // Check for recordings in cache and upload
            val serviceIntent = Intent(context, CallRecordingService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
