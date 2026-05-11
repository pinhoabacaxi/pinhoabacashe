package com.maxrave.exampleApp.repository

import android.content.Context
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.YTExtractor
import com.maxrave.kotlinyoutubeextractor.getAudioOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeRepository(private val context: Context) {

    private val extractor = YTExtractor(context, CACHING = false, LOGGING = true)

    // No teu YouTubeRepository ou similar
    suspend fun downloadMusic(videoId: String) = withContext(Dispatchers.IO) {
        val extractor = YTExtractor(context, CACHING = false)
        val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
    
        extractor.extract(youtubeUrl)
    
        val meta = extractor.getVideoMeta()
        val ytFiles = extractor.getYTFiles()
    
    // Utiliza a tua extensão getAudioOnly() para pegar o itag 251 ou similar
        val bestAudio = ytFiles?.getAudioOnly()?.firstOrNull()

        if (meta != null && bestAudio?.url != null) {
            val downloader = DownloadHelper(context)
            downloader.startDownload(
                title = meta.title ?: "Som",
                artist = meta.author ?: "Desconhecido",
                url = bestAudio.url!!
            )
        }
    }

    suspend fun extractMusicInfo(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        
        // Chama a função de extração da tua biblioteca antiga
        extractor.extract(url) 
        
        val meta = extractor.getVideoMeta()
        val ytFiles = extractor.getYTFiles()

        if (meta != null && ytFiles != null) {
            // Usa a tua extensão getAudioOnly() para pegar o melhor formato de áudio
            val audioFiles = ytFiles.getAudioOnly()
            val bestAudio = audioFiles.firstOrNull()?.url // Pega o primeiro link de áudio disponível

            return@withContext OnlineSong(
                videoId = videoId,
                title = meta.title ?: "Sem título",
                artist = meta.author ?: "Artista desconhecido",
                thumbnailUrl = meta.maxResImageUrl,
                streamUrl = bestAudio
            )
        }
        null
    }
}
