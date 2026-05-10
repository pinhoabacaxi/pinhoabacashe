package com.maxrave.exampleApp.model

data class Playlist(
    val name: String,
    val songIds: MutableList<Long> = mutableListOf()
)
