package com.cookandroid.dotnote.data.repository

import android.util.Log
import com.cookandroid.dotnote.domain.repository.GeminiRepository
import com.cookandroid.dotnote.util.TagCleaner
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepositoryImpl(
    private val apiKey: String
) : GeminiRepository {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    override suspend fun generateTags(content: String): List<String> = withContext(Dispatchers.IO) {
        try {
            // 한국어 프롬프트로 변경, 넓은 범주의 표준화된 태그를 유도
            val prompt = """
                다음 텍스트를 분석하고 3~5개의 핵심 키워드 태그를 추출하세요.
                
                규칙:
                1. 태그는 반드시 한국어 '명사' 단어로 작성하세요. 동사나 형용사는 명사형으로 바꾸세요.
                2. 단어 뒤에 붙는 조사(은, 는, 이, 가, 을, 를, 도, 에, 에서 등)는 반드시 완벽하게 제거하세요. (예: "사진이", "사진을" -> "사진")
                3. 가능한 한 넓고 일반적인 범주의 단어를 사용하세요. (예: "화창한 날씨" 대신 "날씨")
                4. 다음 범주 중에서 해당되는 것을 우선 사용하세요: 일상, 업무, 공부, 아이디어, 감정, 건강, 음식, 여행, 사람, 날씨, 취미, 기술, 독서, 운동, 음악, 영화, 쇼핑, 자연, 가족, 친구
                5. 쉼표로만 구분하고 다른 텍스트는 절대 포함하지 마세요.
                
                텍스트:
                $content
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val resultText = response.text ?: ""

            val tags = resultText
                .split(",")
                .map { TagCleaner.cleanTag(it) }  // TagCleaner로 일관된 조사 제거 및 정제 수행
                .filter { it.length >= 2 && it.length <= 10 && it !in TagCleaner.STOP_WORDS }  // 유효한 길이 및 불용어 필터링

            Log.d("GeminiTags", "Input: ${content.take(30)}... → Tags: $tags")
            tags
        } catch (e: Exception) {
            Log.e("GeminiTags", "API 호출 실패: ${e.message}")
            e.printStackTrace()
            // API 실패 시 간단한 형태소 기반 폴백 태그 생성
            generateFallbackTags(content)
        }
    }

    /**
     * Gemini API 실패 시 폴백: 입력 텍스트에서 긴 단어를 추출하여 태그로 사용
     */
    private fun generateFallbackTags(content: String): List<String> {
        val fallbackTags = TagCleaner.extractValidTags(content).take(7)
        Log.d("GeminiTags", "Fallback tags: $fallbackTags")
        return fallbackTags
    }
}
