package com.realme.callrecord

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

class CallReceiver : BroadcastReceiver() {

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            Log.e("CallReceiver", "Phone state changed: $state")

            when (state) {
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    // Call started, start recording
                    if (!isServiceRunning(context, CallRecordingService::class.java)) {
                        Log.e("CallReceiver", "Call started, starting recording service")
                        val serviceIntent = Intent(context, CallRecordingService::class.java)
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.e("CallReceiver", "Call ended, stopping recording service")
                    val serviceIntent = Intent(context, CallRecordingService::class.java)
                    context.stopService(serviceIntent)
                }
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // Incoming call is ringing
                    Log.e("CallReceiver", "Incoming call ringing")
                }
            }
        }
    }
}
