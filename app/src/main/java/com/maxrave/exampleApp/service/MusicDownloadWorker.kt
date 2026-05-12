package com.maxrave.exampleApp.service

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class MusicDownloadWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val videoUrl = inputData.getString("URL") ?: return Result.failure()
        val fileName = inputData.getString("FILE_NAME") ?: "download_${System.currentTimeMillis()}.mp3"
        
        return try {
            // Caminho: /Android/data/com.maxrave.exampleApp/files/Music/
            val directory = File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "AppDownloads")
            if (!directory.exists()) directory.mkdirs()
            
            val outputFile = File(directory, fileName)
            
            // Início do download
            Log.d("DownloadWorker", "Iniciando download: $videoUrl")
            
            val connection = URL(videoUrl).openConnection()
            connection.connect()
            
            val inputStream = connection.getInputStream()
            val outputStream = FileOutputStream(outputFile)
            
            val data = ByteArray(4096)
            var count: Int
            while (inputStream.read(data).also { count = it } != -1) {
                outputStream.write(data, 0, count)
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Log.d("DownloadWorker", "Download concluído: ${outputFile.absolutePath}")
            
            // Retorna o caminho do arquivo para o app saber onde a música está agora
            Result.success(workDataOf("FILE_PATH" to outputFile.absolutePath))
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Erro no download: ${e.message}")
            Result.failure()
        }
    }
}
