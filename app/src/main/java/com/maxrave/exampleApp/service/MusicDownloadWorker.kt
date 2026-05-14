package com.maxrave.exampleApp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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

class MusicDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "download_channel"

    override suspend fun doWork(): Result {
        val videoId = inputData.getString("VIDEO_ID") ?: return Result.failure()
        val fileName = inputData.getString("FILE_NAME") ?: "music_${System.currentTimeMillis()}.mp3"
        val playlistName = inputData.getString("PLAYLIST_NAME")
    
        // 1. Inicializa notificações IMEDIATAMENTE (Essencial para não dar crash no Android 12+)
        createNotificationChannel()
        try {
            setForeground(createForegroundInfo(fileName))
        } catch (e: Exception) {
            Log.e("Worker", "Erro ao iniciar foreground: ${e.message}")
        }
    
        return try {
            // 2. Extração do link (Chamamos apenas UMA vez)
            val repo = YouTubeRepository(applicationContext)
            val song = repo.extractAudioLink(videoId) ?: return Result.failure()
            val url = song.url
    
            // 3. Configuração do OkHttp
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
    
            if (!response.isSuccessful || response.body == null) return Result.failure()
    
            // 4. Definição do local de salvamento (Pasta de Música Pública)
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val targetDir = if (!playlistName.isNullOrEmpty()) {
                File(baseDir, playlistName).apply { mkdirs() }
            } else {
                baseDir
            }
            val file = File(targetDir, fileName)
    
            // 5. Download com atualização de progresso (Para a notificação não ficar travada)
            val body = response.body!!
            val totalBytes = body.contentLength()
            var bytesBaixados = 0L
    
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesBaixados += bytesRead
                        
                        // Atualiza a notificação a cada 500kb aproximadamente
                        if (totalBytes > 0) {
                            val progress = ((bytesBaixados * 100) / totalBytes).toInt()
                            updateNotification(fileName, progress)
                        }
                    }
                }
            }
    
            Log.d("DownloadWorker", "Sucesso: Salvo em ${file.absolutePath}")
            Result.success()
    
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Erro crítico no download: ${e.message}")
            Result.failure()
        }
    }
    private fun createForegroundInfo(fileName: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setContentTitle("Baixando Música")
                .setContentText(fileName)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true) // Impede que o usuário feche a notificação durante o download
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(101, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(101, notification)
            }
        }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
