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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.content.pm.ServiceInfo

class MusicDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val audioUrl = inputData.getString("URL") ?: return Result.failure()
        val fileName = inputData.getString("FILE_NAME") ?: "downloaded_music.mp3"

        // Configura a notificação de primeiro plano (obrigatório para Android 12+)
        setForeground(createForegroundInfo(fileName))

        return try {
            val client = OkHttpClient()
            val request = Request.Builder().url(audioUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return Result.failure()

            val body = response.body ?: return Result.failure()
            val inputStream: InputStream = body.byteStream()

            // Define o diretório de destino: Pasta de Músicas padrão do Android
            // Isso garante que o MusicLoader.kt encontre o arquivo via MediaStore
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (!directory.exists()) directory.mkdirs()

            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            val fileSize = body.contentLength()
            var downloadedBytes = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                
                // Atualiza o progresso da notificação opcionalmente
                val progress = (downloadedBytes * 100 / fileSize).toInt()
                updateNotification(fileName, progress)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Log.d("DownloadWorker", "Sucesso: Arquivo salvo em ${file.absolutePath}")
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Erro no download: ${e.message}")
            Result.failure()
        }
    }

    private fun createForegroundInfo(notification: Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Para Android 10+ e obrigatório no Android 14 (API 34)
            ForegroundInfo(
                NOTIFICATION_ID, // O ID numérico da sua notificação
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC // <-- O SEGREDO ESTÁ AQUI
            )
        } else {
        // Para versões mais antigas do Android
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Baixando Música")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        return ForegroundInfo(101, notification)
    }

    private fun updateNotification(fileName: String, progress: Int) {
        val notification = NotificationCompat.Builder(context, "download_channel")
            .setContentTitle("Baixando: $fileName")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .build()
        notificationManager.notify(101, notification)
    }
}
