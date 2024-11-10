package com.realme.callrecord

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class CallRecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var fileName: String? = null
    private lateinit var driveService: Drive
    private var isRecording = false
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var phoneStateListener: PhoneStateListener
    private lateinit var networkChangeReceiver: NetworkChangeReceiver

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        setupPhoneStateListener()

        // Register network change receiver
        networkChangeReceiver = NetworkChangeReceiver()
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkChangeReceiver, filter)
    }

    private fun setupPhoneStateListener() {
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        if (!isRecording) {
                            startRecording()
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (isRecording) {
                            stopRecording()
                        }
                    }
                }
            }
        }
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService() // Call to show the notification

        val accountName = getSavedAccountName()
        if (accountName != null) {
            initializeDriveService(accountName) // Initialize if not done already
        } else {
            Log.e("CallRecordingService", "No account found, cannot initialize Drive service.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("CallRecordingService", "Audio recording permission not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundService()
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (isRecording) return

        fileName = File(cacheDir, "call_${System.currentTimeMillis()}.3gp").toString()
        Log.d("Recording", "Attempting to start recording...")

        recorder = MediaRecorder().apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(fileName)
                prepare()
                start()
                isRecording = true
                Log.e("CallRecordingService", "Recording started: $fileName")
            } catch (e: IOException) {
                Log.e("CallRecordingService", "MediaRecorder prepare/start failed", e)
                stopSelf()
            }
        }

    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundService() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notificationChannelId = "call_recording_channel_id"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                notificationChannelId,
                "Call Recording",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Recording Call")
            .setContentText("Recording ongoing...")
            .setSmallIcon(R.drawable.baseline_local_phone_24)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun stopRecording() {
        recorder?.apply {
            try {
                stop()
                Log.d("CallRecordingService", "Recording stopped: $fileName")
                checkAndUploadFile(fileName)
            } catch (e: RuntimeException) {
                Log.e("CallRecordingService", "MediaRecorder stop failed", e)
            } finally {
                release()
            }
        }
        isRecording = false
        recorder = null
    }

    private fun checkAndUploadFile(filePath: String?) {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo

        if (activeNetwork?.isConnected == true) {
            uploadFileToDrive(filePath)
        } else {
            Log.e("CallRecordingService", "No internet connection. File will be uploaded later.")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun uploadFileToDrive(filePath: String?) {
        if (filePath == null) return

        val fileToUpload = File(filePath)
        val fileMetadata = com.google.api.services.drive.model.File().apply {
            name = fileToUpload.name
            mimeType = "audio/3gpp"
        }

        val mediaContent = FileContent("audio/3gpp", fileToUpload)

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val file = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                Log.d("DriveService", "File uploaded successfully: ID: ${file.id}")
            } catch (e: Exception) {
                Log.e("DriveService", "Upload failed", e)
            }
        }
    }


    private fun initializeDriveService(accountName: String) {
        if (DriveServiceHolder.driveService != null) {
            Log.e("DriveService", "Drive service already initialized.")
            return
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            this, listOf(DriveScopes.DRIVE, DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccountName = accountName

        DriveServiceHolder.driveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            JacksonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("YourAppName").build()

        Log.e("DriveService", "Drive service initialized with account: $accountName")
    }

    private fun getSavedAccountName(): String? {
        val sharedPreferences = getSharedPreferences("your_prefs", Context.MODE_PRIVATE)
        val accountName = sharedPreferences.getString("google_account", null)

        Log.e("google_account", accountName.toString())

        return accountName
    }

    private fun notifyUploadSuccess(fileId: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, "upload_channel")
            .setContentTitle("Upload Successful")
            .setContentText("File uploaded with ID: $fileId")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        unregisterReceiver(networkChangeReceiver)
        stopRecording()
//        restartService()
    }

//    @SuppressLint("ScheduleExactAlarm")
//    private fun restartService() {
//        val restartServiceIntent = Intent(applicationContext, CallRecordingService::class.java)
//        val pendingIntent = PendingIntent.getService(applicationContext, 1, restartServiceIntent,
//            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
//
//        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
//        alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pendingIntent)
//    }


    override fun onBind(intent: Intent?): IBinder? = null
}
