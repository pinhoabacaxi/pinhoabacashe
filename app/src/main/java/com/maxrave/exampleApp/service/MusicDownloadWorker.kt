package com.maxrave.exampleApp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.maxrave.exampleApp.repository.YouTubeRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MusicDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private val channelId = "download_channel"
    private val NOTIFICATION_ID = 101

    override suspend fun doWork(): Result {
        // 1. EXTRAÇÃO DOS DADOS (Sem 'return' nas propriedades da classe)
        val videoId = inputData.getString("VIDEO_ID") ?: return Result.failure()
        var audioUrl = inputData.getString("URL")
        val fileName = inputData.getString("FILE_NAME") ?: "musica_${System.currentTimeMillis()}.mp3"
        val playlistName = inputData.getString("PLAYLIST_NAME")

        val repo = YouTubeRepository(context)

        // 2. BYPASS DE LINK EXPIRADO: Extraímos um link fresco se necessário
        // O videoId aqui já é garantido como não nulo pela verificação acima
        val song = repo.extractAudioLink(videoId) 
        if (song != null) {
            audioUrl = song.url
        }

        if (audioUrl.isNullOrEmpty()) {
            Log.e("DownloadWorker", "Erro: Nenhuma URL de áudio disponível para $fileName")
            return Result.failure()
        }

        createNotificationChannel()
        setForeground(createForegroundInfo(fileName))

        return try {
            val client = OkHttpClient()
            val request = Request.Builder().url(audioUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return Result.failure()

            val body = response.body ?: return Result.failure()
            val inputStream: InputStream = body.byteStream()
            
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val targetFolder = if (!playlistName.isNullOrEmpty()) {
                File(baseDir, playlistName).apply { if (!exists()) mkdirs() }
            } else {
                baseDir
            }

            val file = File(targetFolder, fileName)
            val outputStream = FileOutputStream(file)
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            val fileSize = body.contentLength()
            var totalBytesRead: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                if (fileSize > 0) {
                    val progress = (totalBytesRead * 100 / fileSize).toInt()
                    if (progress % 5 == 0) {
                        updateNotification(fileName, progress)
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Log.d("DownloadWorker", "Sucesso: $fileName salvo em ${file.absolutePath}")
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Erro no download de $fileName: ${e.message}")
            Result.failure()
        }
    }

    private fun createForegroundInfo(fileName: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Baixando Música")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(fileName: String, progress: Int) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Baixando: $fileName")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setSilent(true) 
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Downloads de Música",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progresso de download de músicas do YouTube"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
