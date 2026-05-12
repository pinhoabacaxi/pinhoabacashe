package com.maxrave.kotlinyoutubeextractor

/**
 * Representa as especificações técnicas de um formato de vídeo ou áudio do YouTube.
 */
data class Format(
    val itag: Int,
    val ext: String?,
    val height: Int,
    val fps: Int,
    val videoCodec: VCodec?,
    val audioCodec: ACodec?,
    val audioBitrate: Int,
    val isDashContainer: Boolean,
    val isHlsContent: Boolean = false
) {
    enum class VCodec {
        H263, H264, MPEG4, VP8, VP9, AV1, NONE
    }

    enum class ACodec {
        MP3, AAC, VORBIS, OPUS, NONE
    }

    // Construtor utilizado no init do YTExtractor para formatos padrão
    internal constructor(
        itag: Int,
        ext: String?,
        height: Int,
        vCodec: VCodec?,
        aCodec: ACodec?,
        isDashContainer: Boolean
    ) : this(itag, ext, height, 30, vCodec, aCodec, -1, isDashContainer, false)

    // Construtor focado em Áudio (usado para M4A/WebM Audio)
    internal constructor(
        itag: Int,
        ext: String?,
        vCodec: VCodec?,
        aCodec: ACodec?,
        audioBitrate: Int,
        isDashContainer: Boolean
    ) : this(itag, ext, -1, 30, vCodec, aCodec, audioBitrate, isDashContainer, false)

    // Construtor para formatos com Bitrate e Resolução (ex: Streaming Adaptativo)
    internal constructor(
        itag: Int,
        ext: String?,
        height: Int,
        vCodec: VCodec?,
        aCodec: ACodec?,
        audioBitrate: Int,
        isDashContainer: Boolean
    ) : this(itag, ext, height, 30, vCodec, aCodec, audioBitrate, isDashContainer, false)

    // Construtor para High Frame Rate (60fps) ou vídeos com FPS variável
    internal constructor(
        itag: Int,
        ext: String?,
        height: Int,
        vCodec: VCodec?,
        fps: Int,
        aCodec: ACodec?,
        isDashContainer: Boolean
    ) : this(itag, ext, height, fps, vCodec, aCodec, -1, isDashContainer, false)
}
