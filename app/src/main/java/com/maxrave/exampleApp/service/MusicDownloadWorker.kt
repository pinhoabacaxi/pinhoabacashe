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
import java.util.concurrent.TimeUnit

class MusicDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "download_channel"
    private val NOTIFICATION_ID = 101

    override suspend fun doWork(): Result {
        val videoId = inputData.getString("VIDEO_ID") ?: return Result.failure()
        val fileName = inputData.getString("FILE_NAME") ?: "music_${System.currentTimeMillis()}.mp3"
        val playlistName = inputData.getString("PLAYLIST_NAME")
    
        // 1. Inicializa o canal e o serviço em primeiro plano (Obrigatório para downloads longos)
        createNotificationChannel()
        try {
            setForeground(createForegroundInfo(fileName))
        } catch (e: Exception) {
            Log.e("Worker", "Falha ao iniciar Foreground: ${e.message}")
        }
       
        val repo = YouTubeRepository(applicationContext)
        
        // 2. Extração do link fresco (Garante que a URL não expire durante o download de playlists longas)
        val song = repo.extractAudioLink(videoId) ?: return Result.failure()
        
        // 3. Configuração do cliente HTTP com timeout estendido para arquivos grandes
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return try {
            val request = Request.Builder().url(song.url).build()
            val response = client.newCall(request).execute()
    
            if (!response.isSuccessful || response.body == null) return Result.failure()
            val body = response.body!!
    
            // 4. LÓGICA DE DIRETÓRIO: Salva em subpasta se fizer parte de uma playlist
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val targetDir = if (!playlistName.isNullOrEmpty()) {
                File(baseDir, playlistName).apply { if (!exists()) mkdirs() }
            } else {
                baseDir
            }
            
            val file = File(targetDir, fileName)
            
            // 5. DOWNLOAD COM PROGRESSO: Atualiza a notificação enquanto baixa
            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        
                        // Atualiza o progresso apenas em intervalos para não sobrecarregar o sistema
                        if (totalBytes > 0) {
                            val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                            if (progress % 5 == 0) { // Atualiza a cada 5%
                                updateNotification(fileName, progress)
                            }
                        }
                    }
                }
            }
            
            Log.d("DownloadWorker", "Sucesso: $fileName salvo em ${file.absolutePath}")
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Erro no download: ${e.message}")
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
            val channel = NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progresso de download de músicas"
            }
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
            .setSilent(true) // Evita que o celular vibre a cada atualização de 1%
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
