package com.realme.callrecord

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            // Start the CallRecordingService
            val serviceIntent = Intent(context, CallRecordingService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
