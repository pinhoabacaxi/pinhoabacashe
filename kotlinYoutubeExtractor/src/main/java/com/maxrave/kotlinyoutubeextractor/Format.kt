package com.maxrave.kotlinyoutubeextractor

class Format(
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
        H263, H264, MPEG4, VP8, VP9, NONE
    }

    enum class ACodec {
        MP3, AAC, VORBIS, OPUS, NONE
    }

    // Construtor 1: Padrão (utilizado na maioria dos formatos de vídeo)
    internal constructor(
        itag: Int,
        ext: String?,
        height: Int,
        vCodec: VCodec?,
        aCodec: ACodec?,
        isDashContainer: Boolean
    ) : this(itag, ext, height, 30, vCodec, aCodec, -1, isDashContainer, false)

    // Construtor 2: Focado em Áudio (sem definição de altura/height)
    internal constructor(
        itag: Int,
        ext: String?,
        vCodec: VCodec?,
        aCodec: ACodec?,
        audioBitrate: Int,
        isDashContainer: Boolean
    ) : this(itag, ext, -1, 30, vCodec, aCodec, audioBitrate, isDashContainer, false)

    // Construtor 3: Com Bitrate definido
    internal constructor(
        itag: Int,
        ext: String?,
        height: Int,
        vCodec: VCodec?,
        aCodec: ACodec?,
        audioBitrate: Int,
        isDashContainer: Boolean
    ) : this(itag, ext, height, 30, vCodec, aCodec, audioBitrate, isDashContainer, false)

    // Construtor 4: Completo (incluindo flag HLS)
    internal constructor(
        itag: Int,
        ext: String?,
        height: Int,
        vCodec: VCodec?,
        aCodec: ACodec?,
        audioBitrate: Int,
        isDashContainer: Boolean,
        isHlsContent: Boolean
    ) : this(itag, ext, height, 30, vCodec, aCodec, audioBitrate, isDashContainer, isHlsContent)

    // Construtor 5: Com FPS variável
    internal constructor(
        itag: Int,
        ext: String?,
        height: Int,
        vCodec: VCodec?,
        fps: Int,
        aCodec: ACodec?,
        isDashContainer: Boolean
    ) : this(itag, ext, height, fps, vCodec, aCodec, -1, isDashContainer, false)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Format) return false

        return itag == other.itag &&
                height == other.height &&
                fps == other.fps &&
                audioBitrate == other.audioBitrate &&
                isDashContainer == other.isDashContainer &&
                isHlsContent == other.isHlsContent &&
                ext == other.ext &&
                videoCodec == other.videoCodec &&
                audioCodec == other.audioCodec
    }

    override fun hashCode(): Int {
        var result = itag
        result = 31 * result + (ext?.hashCode() ?: 0)
        result = 31 * result + height
        result = 31 * result + fps
        result = 31 * result + (videoCodec?.hashCode() ?: 0)
        result = 31 * result + (audioCodec?.hashCode() ?: 0)
        result = 31 * result + audioBitrate
        result = 31 * result + isDashContainer.hashCode()
        result = 31 * result + isHlsContent.hashCode()
        return result
    }

    override fun toString(): String {
        return "Format(itag=$itag, ext=$ext, height=$height, fps=$fps, vCodec=$videoCodec, aCodec=$audioCodec, audioBitrate=$audioBitrate, isDashContainer=$isDashContainer, isHlsContent=$isHlsContent)"
    }
}
