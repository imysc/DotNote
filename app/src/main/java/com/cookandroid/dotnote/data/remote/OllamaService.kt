package com.cookandroid.dotnote.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Ollama /api/generate 요청 DTO
 */
data class OllamaRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val format: String = "json"
)

/**
 * Ollama /api/generate 응답 DTO
 */
data class OllamaResponse(
    val model: String,
    val response: String, // Gemma-2가 생성한 JSON 형식의 문자열이 포함됨
    val done: Boolean
)

/**
 * Ollama 로컬 통신을 위한 Retrofit 인터페이스
 */
interface OllamaService {
    @POST("api/generate")
    suspend fun generate(@Body request: OllamaRequest): OllamaResponse
}
