package com.maxrave.exampleApp.model

data class OnlineSong(
    val videoId: String, // O erro sugere que você pode estar usando videoId em vez de id
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val url: String,      // Link de áudio real
    val duration: String
)
