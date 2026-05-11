package com.maxrave.exampleApp.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

class DownloadHelper(private val context: Context) {

    fun startDownload(title: String, artist: String, url: String) {
        val fileName = "$artist - $title.mp3".replace("/", "-")
        
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("A baixar: $title")
            .setDescription(artist)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        
        Toast.makeText(context, "Download iniciado...", Toast.LENGTH_SHORT).show()
    }
}
