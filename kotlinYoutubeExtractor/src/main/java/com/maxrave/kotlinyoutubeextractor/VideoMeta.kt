package com.maxrave.kotlinyoutubeextractor

import java.io.Serializable

data class VideoMeta(
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String,
    val duration: Long,
    val viewCount: Long,
    val isLiveStream: Boolean,
    val description: String,
    val thumbnailUrl: String = "" // URL dinâmica vinda do YTSearch
) : Serializable {

    // Se o thumbnailUrl estiver vazio, usamos a reconstrução estática como fallback
    val bestThumbnail: String
        get() = thumbnailUrl.ifEmpty { hqImageUrl }

    // Reconstrução estática (Fallback para vídeos normais)
    val thumbUrl: String get() = "$IMAGE_BASE_URL$videoId/default.jpg"
    val mqImageUrl: String get() = "$IMAGE_BASE_URL$videoId/mqdefault.jpg"
    val hqImageUrl: String get() = "$IMAGE_BASE_URL$videoId/hqdefault.jpg"
    val sdImageUrl: String get() = "$IMAGE_BASE_URL$videoId/sddefault.jpg"
    val maxResImageUrl: String get() = "$IMAGE_BASE_URL$videoId/maxresdefault.jpg"

    companion object {
        private const val IMAGE_BASE_URL = "https://i.ytimg.com/vi/"
    }
}
