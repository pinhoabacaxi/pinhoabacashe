package com.maxrave.exampleApp.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

class DownloadHelper(private val context: Context) {

    fun startDownload(title: String, artist: String, url: String) {
        // Limpeza rigorosa para evitar erro de caracteres inválidos no sistema de arquivos
        val rawName = "$artist - $title"
        val fileName = rawName.replace("[\\\\/:*?\"<>|]".toRegex(), "_") + ".mp3"
        
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Baixando: $title")
                .setDescription(artist)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                [span_9](start_span).setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName) //[span_9](end_span)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            
            Toast.makeText(context, "Download iniciado: $title", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao iniciar download", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}
