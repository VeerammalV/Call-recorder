package com.realme.callrecord

import android.content.Context
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes

object DriveServiceHolder {
    var driveService: Drive? = null

    fun initialize(context: Context, accountName: String) {
        if (driveService == null) {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE, DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccountName = accountName

            driveService = Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Call Record").build()
        }
    }
}



