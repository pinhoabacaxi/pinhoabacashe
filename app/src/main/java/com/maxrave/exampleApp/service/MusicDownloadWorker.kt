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
        val fileName = inputData.getString("FILE_NAME") ?: "music.mp3"
        val playlistName = inputData.getString("PLAYLIST_NAME")
    
        createNotificationChannel()
        try {
        setForeground(createForegroundInfo(fileName))
        } catch (e: Exception) {
            Log.e("Worker", "Falha ao iniciar Foreground: ${e.message}")
        }
       
        val repo = YouTubeRepository(applicationContext)
        // Extrai o link fresco dentro do Worker para evitar URLs expiradas
        val song = repo.extractAudioLink(videoId) ?: return Result.failure()
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
        return try {
            val request = okhttp3.Request.Builder().url(song.url).build()
            val response = okhttp3.OkHttpClient().newCall(request).execute()
    
            if (!response.isSuccessful || response.body == null) return Result.failure()
    
            // Salva na pasta pública de Música
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val targetDir = if (!playlistName.isNullOrEmpty()) {
                File(baseDir, playlistName).apply { mkdirs() }
            } else {
                baseDir
            }
            
            val file = File(targetDir, fileName)
            response.body!!.byteStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Result.success()
        } catch (e: Exception) {
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
    private fun updateNotification(fileName: String, progress: Int) {
        val notification = NotificationCompat.Builder(applicationContext, "download_channel")
            .setContentTitle("Baixando: $fileName")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        
        notificationManager.notify(101, notification)
    }
}
