package com.maxrave.kotlinyoutubeextractor

import java.io.Serializable
/**
 * VideoMeta contém todas as informações disponíveis para um vídeo do YouTube, 
 * como título, autor, miniatura, contagem de visualizações, etc. 
 */
data class VideoMeta(
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String,
    val duration: Long,
    val viewCount: Long,
    val isLiveStream: Boolean,
    val description: String,
    val thumbnailUrl: String = "" // Novo campo adicionado
) : Serializable
 {

    // 120 x 90
    val thumbUrl: String
        get() = "${IMAGE_BASE_URL}${videoId}/default.jpg"

    // 320 x 180
    val mqImageUrl: String
        get() = "${IMAGE_BASE_URL}${videoId}/mqdefault.jpg"

    // 480 x 360
    val hqImageUrl: String
        get() = "${IMAGE_BASE_URL}${videoId}/hqdefault.jpg"

    // 640 x 480
    val sdImageUrl: String
        get() = "${IMAGE_BASE_URL}${videoId}/sddefault.jpg"

    /**
     * Retorna a miniatura em resolução máxima.
     * Nota: Nem todos os vídeos possuem esta versão disponível. 
     */
    val maxResImageUrl: String
        get() = "${IMAGE_BASE_URL}${videoId}/maxresdefault.jpg"

    companion object {
        // Atualizado para HTTPS para cumprir políticas de segurança do Android (Network Security Policy)
        private const val IMAGE_BASE_URL = "https://i.ytimg.com/vi/"
    }
}
