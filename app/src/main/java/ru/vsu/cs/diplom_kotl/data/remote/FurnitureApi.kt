package ru.vsu.cs.diplom_kotl.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FurnitureApi {
    @GET("api/v1/models")
    suspend fun getModels(
        @Query("style") style: String? = null,
        @Query("approvedOnly") approvedOnly: Boolean = true
    ): List<FurnitureModelDto>

    @POST("api/v1/recommendations")
    suspend fun getRecommendations(
        @Body request: RecommendationRequestDto
    ): RecommendationResponseDto

    @POST("api/v1/stores/{storeId}/models")
    suspend fun uploadStoreModel(
        @Path("storeId") storeId: String,
        @Body request: UploadModelRequestDto
    ): FurnitureModelDto
}
