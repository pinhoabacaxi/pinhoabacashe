package com.maxrave.exampleApp.online

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {

    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 20,
        @Query("q") query: String,
        @Query("key") apiKey: String
    ): Response<YouTubeSearchResponse>
}
