package com.maxrave.exampleApp.repository

import android.content.Context
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.YTExtractor
import com.maxrave.kotlinyoutubeextractor.getAudioOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeRepository(private val context: Context) {

    // Instância única para evitar overhead
    private val extractor = YTExtractor(context, CACHING = false, LOGGING = true)

    suspend fun downloadMusic(videoId: String) = withContext(Dispatchers.IO) {
        val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
    
        try {
            extractor.extract(youtubeUrl)
            val meta = extractor.getVideoMeta()
            [span_3](start_span)val ytFiles = extractor.getYTFiles() //[span_3](end_span)
    
            // Pega o melhor formato de áudio disponível
            val bestAudio = ytFiles?.getAudioOnly()?.firstOrNull()

            if (meta != null && bestAudio?.url != null) {
                val downloader = DownloadHelper(context)
                downloader.startDownload(
                    [span_4](start_span)title = meta.title ?: "Som", //[span_4](end_span)
                    artist = meta.author ?: "Desconhecido",
                    url = bestAudio.url!! [span_5](start_span)//[span_5](end_span)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun extractMusicInfo(videoId: String): OnlineSong? = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        
        try {
            extractor.extract(url) 
            val meta = extractor.getVideoMeta()
            val ytFiles = extractor.getYTFiles()

            if (meta != null && ytFiles != null) {
                [span_6](start_span)val audioFiles = ytFiles.getAudioOnly() //[span_6](end_span)
                val bestAudio = audioFiles.firstOrNull()?.url

                return@withContext OnlineSong(
                    videoId = videoId,
                    [span_7](start_span)title = meta.title ?: "Sem título", //[span_7](end_span)
                    author = meta.author ?: "Artista desconhecido",
                    thumbnailUrl = meta.maxResImageUrl,
                    streamUrl = bestAudio
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
