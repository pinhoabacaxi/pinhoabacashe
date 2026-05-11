package com.maxrave.exampleApp.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

class DownloadHelper(private val context: Context) {

    fun startDownload(title: String, artist: String, url: String) {
        [span_65](start_span)val fileName = "$artist - $title.mp3".replace("/", "-")[span_65](end_span)
        
        val request = DownloadManager.Request(Uri.parse(url))
            [span_66](start_span).setTitle("A baixar: $title")[span_66](end_span)
            .setDescription(artist)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            [span_67](start_span).setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)[span_67](end_span)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        [span_68](start_span)manager.enqueue(request)[span_68](end_span)
        
        Toast.makeText(context, "Download iniciado...", Toast.LENGTH_SHORT).show()
    }
}
