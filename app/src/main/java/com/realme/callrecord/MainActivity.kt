package com.realme.callrecord

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes

class MainActivity : AppCompatActivity() {
    private companion object {
        const val REQUEST_PERMISSIONS_CODE = 1001
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        signInWithGoogle()

        if (arePermissionsGranted()) {
            startRecordingService()
        } else {
            requestPermissions()
        }
    }

    private fun signInWithGoogle() {
        val signInClient = getGoogleSignInClient(this)
        startActivityForResult(signInClient.signInIntent, REQUEST_PERMISSIONS_CODE)
    }

    private fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE))
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PERMISSIONS_CODE && resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        if (task.isSuccessful) {
            val googleAccount = task.result
            createDriveService(googleAccount)

            saveAccountName(googleAccount)

            startRecordingService() // Start service only after Drive is initialized
        } else {
            Log.e("SignInError", "Sign-in failed", task.exception)
            Toast.makeText(this, "Login Failed", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createDriveService(googleAccount: GoogleSignInAccount?) {
        if (googleAccount == null) {
            Log.e("CreateDriveService", "Google account is null")
            return
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            this, listOf(DriveScopes.DRIVE, DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = googleAccount.account!!

        Log.e("credential", "${googleAccount.account}")

        // Initialize Drive Service
        DriveServiceHolder.driveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            JacksonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(getString(R.string.app_name)).build()

        Log.d("DriveService", "Drive service initialized successfully")
    }

    private fun saveAccountName(googleAccount: GoogleSignInAccount) {
        val accountName = googleAccount.account?.name
        if (accountName != null) {
            val sharedPreferences = getSharedPreferences("your_prefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().putString("google_account", accountName).apply()
            Log.e("AccountNameSaved", "Saved account name: $accountName") // Log saved account name
        } else {
            Log.e("AccountNameSaveError", "Account name is null, cannot save.")
        }
    }


    @RequiresApi(Build.VERSION_CODES.P)
    private fun arePermissionsGranted(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.FOREGROUND_SERVICE
        )
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSIONS_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startRecordingService()
            } else {
                Toast.makeText(this, "Permissions are required to use this app.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startRecordingService() {
        val intent = Intent(this, CallRecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent) // This will start the service in the foreground
        } else {
            startService(intent) // Fallback for older versions
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecordingService()
        }
    }
}
