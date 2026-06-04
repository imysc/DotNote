package com.cookandroid.dotnote.domain.repository

interface GeminiRepository {
    suspend fun generateTags(content: String): List<String>
}
