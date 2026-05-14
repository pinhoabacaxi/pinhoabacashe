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
        // 1. Criar canal e Foreground ANTES de qualquer lógica
        createNotificationChannel()
        setForeground(createForegroundInfo(fileName))
        val repo = YouTubeRepository(applicationContext)
        val song = repo.extractAudioLink(videoId) ?: return Result.failure()
        return try {
            val repo = YouTubeRepository(applicationContext)
            val song = repo.extractAudioLink(videoId) ?: return Result.failure()
            val url = song.url

            val client = OkHttpClient()
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) return Result.failure()

            val body = response.body ?: return Result.failure()
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val targetDir = if (!playlistName.isNullOrEmpty()) File(baseDir, playlistName).apply { mkdirs() } else baseDir
            val file = File(targetDir, fileName)

            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Erro: ${e.message}")
            Result.failure()
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
