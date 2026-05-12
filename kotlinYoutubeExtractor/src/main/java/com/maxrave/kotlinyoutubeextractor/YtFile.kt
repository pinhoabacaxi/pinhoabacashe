package com.maxrave.kotlinyoutubeextractor

/**
 * Representa um ficheiro de media do YouTube, contendo os seus metadados de formato
 * e o URL direto para streaming ou download.
 */
data class YtFile(
    /**
     * Dados de formato específicos para este ficheiro (resolução, codec, bitrate, etc.).
     */
    val meta: Format?,
    
    /**
     * O URL direto para descarregar ou reproduzir o ficheiro.
     */
    val url: String?
)
