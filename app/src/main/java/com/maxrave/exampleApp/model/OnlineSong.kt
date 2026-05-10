package com.maxrave.exampleApp.model

data class OnlineSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    var streamUrl: String? = null // O link extraído virá para aqui
)
