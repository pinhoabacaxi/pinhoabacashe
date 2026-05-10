package com.maxrave.exampleApp.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

class DownloadHelper(private val context: Context) {

    fun downloadMusic(title: String, url: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Baixando: $title")
            .setDescription("MaxRave Player")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "$title.mp3")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        
        // DICA: O Android MediaScanner vai indexar o arquivo automaticamente
        // e ele aparecerá no seu MusicLoader na próxima vez que você der "Scan".
    }
}
