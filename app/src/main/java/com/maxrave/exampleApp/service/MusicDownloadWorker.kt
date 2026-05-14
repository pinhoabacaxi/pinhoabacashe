package com.maxrave.exampleApp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.maxrave.exampleApp.repository.PlaylistRepository
import com.maxrave.exampleApp.repository.YouTubeRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class MusicDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "download_channel"
    private val NOTIFICATION_ID = 101

    override suspend fun doWork(): Result {
        val videoId = inputData.getString("VIDEO_ID") ?: return Result.failure()
        val fileName = inputData.getString("FILE_NAME") ?: "music_${System.currentTimeMillis()}.mp3"
        val playlistName = inputData.getString("PLAYLIST_NAME")
    
        createNotificationChannel()
        try {
            setForeground(createForegroundInfo(fileName))
        } catch (e: Exception) {
            Log.e("Worker", "Falha ao iniciar Foreground: ${e.message}")
        }
       
        val youtubeRepo = YouTubeRepository(applicationContext)
        val playlistRepo = PlaylistRepository(applicationContext)
        
        // 1. Extração do link fresco
        val song = youtubeRepo.extractAudioLink(videoId) ?: return Result.failure()
        
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return try {
            val request = Request.Builder().url(song.url).build()
            val response = client.newCall(request).execute()
    
            if (!response.isSuccessful || response.body == null) return Result.failure()
            val body = response.body!!
    
            // 2. Lógica de Diretório
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val targetDir = if (!playlistName.isNullOrEmpty()) {
                File(baseDir, playlistName).apply { if (!exists()) mkdirs() }
            } else {
                baseDir
            }
            
            val file = File(targetDir, fileName)
            
            // 3. Download com progresso
            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        
                        if (totalBytes > 0) {
                            val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                            if (progress % 5 == 0) updateNotification(fileName, progress)
                        }
                    }
                }
            }
            
            // 4. ESCANEAMENTO DE MÍDIA: Notifica o Android sobre o novo arquivo
            MediaScannerConnection.scanFile(applicationContext, arrayOf(file.absolutePath), null) { path, _ ->
                Log.d("Worker", "Mídia escaneada: $path")
            }

            // 5. REGISTRO NO BANCO DE DADOS (ROOM): O segredo para aparecer na aba Local
            if (!playlistName.isNullOrEmpty()) {
                val playlistId = playlistRepo.getPlaylistIdByName(playlistName)
                if (playlistId != null) {
                    // Adiciona a música ao banco vinculando-a à playlist e marcando o caminho físico
                    playlistRepo.addSongToPlaylist(
                        playlistId = playlistId,
                        item = song, 
                        localPath = file.absolutePath
                    )
                    Log.d("Worker", "Música vinculada à playlist $playlistName no Room")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Erro: ${e.message}")
            Result.failure()
        }
    }

    private fun createForegroundInfo(fileName: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Baixando Música")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(fileName: String, progress: Int) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Baixando: $fileName")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
