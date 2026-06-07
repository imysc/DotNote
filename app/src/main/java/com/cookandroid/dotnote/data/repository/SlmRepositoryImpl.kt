package com.cookandroid.dotnote.data.repository

import android.util.Log
import com.cookandroid.dotnote.domain.repository.GeminiRepository
import com.cookandroid.dotnote.domain.repository.SlmRepository
import com.cookandroid.dotnote.util.TagCleaner
import com.google.gson.Gson
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SlmRepository 구현체: Google AI Edge(MediaPipe LlmInference)를 활용해 
 * 기기 로컬에서 모델을 직접 구동(온디바이스)하며, 모델 파일이 없거나 하드웨어 오류 시 
 * 기존 Gemini 클라우드 API로 자동 폴백합니다.
 */
class SlmRepositoryImpl(
    @Volatile var llmInference: LlmInference?,
    private val geminiRepository: GeminiRepository
) : SlmRepository {

    private val gson = Gson()

    override suspend fun analyzeMemo(content: String): SlmRepository.SlmAnalysisResult = withContext(Dispatchers.IO) {
        // Gemma-2 QLoRA 튜닝 프롬프트 템플릿 구성 (최대 7개 유연 추출 및 노이즈 방지 밸런스 적용)
        val prompt = """
            <start_of_turn>user
            메모 내용을 정밀하게 분석하여 이 메모를 대표하는 연관 태그 리스트를 추출하되, 
            본문 내용이 풍부하고 구체적인 경우 연관 태그를 최대 7개까지 상세히 추출하고, 
            짧고 단순한 본문인 경우에는 노이즈 방지를 위해 3~5개 내외의 알짜 핵심 키워드만을 추출하세요.
            또한, 추출된 태그와 메모 내 키워드 간의 논리적 연결 관계를 최대한 상세하게 도출하여 JSON 규격으로만 출력하세요.

            메모 내용:
            $content<end_of_turn>
            <start_of_turn>model
        """.trimIndent()

        val inferenceEngine = llmInference
        if (inferenceEngine != null) {
            try {
                Log.d("SlmRepo", "온디바이스 MediaPipe LlmInference 추론 시작")
                val responseJson = inferenceEngine.generateResponse(prompt).trim()
                Log.d("SlmRepo", "온디바이스 응답 수신: $responseJson")

                // 마크다운 JSON 블록 (```json ... ```) 탈피 처리
                var cleanedJson = responseJson
                if (cleanedJson.contains("```json")) {
                    cleanedJson = cleanedJson.substringAfter("```json").substringBefore("```").trim()
                } else if (cleanedJson.contains("```")) {
                    cleanedJson = cleanedJson.substringAfter("```").substringBefore("```").trim()
                }

                val parsedResult = gson.fromJson(cleanedJson, SlmRepository.SlmAnalysisResult::class.java)
                val rawTags = parsedResult?.tags?.map { TagCleaner.cleanTag(it) }?.filter { it.length >= 2 && it !in TagCleaner.STOP_WORDS } ?: emptyList()

                // 하이브리드 보정: 태그 개수가 7개 미만일 때 본문에서 명사성 단어를 추출하여 채움
                val finalTags = if (rawTags.size < 7) {
                    val additionalTags = TagCleaner.extractValidTags(content, rawTags)
                    (rawTags + additionalTags).distinct().take(7)
                } else {
                    rawTags
                }

                SlmRepository.SlmAnalysisResult(
                    tags = finalTags,
                    relations = parsedResult?.relations?.map {
                        SlmRepository.SlmRelation(
                            targetMemoKeyword = it.targetMemoKeyword.trim(),
                            relationType = it.relationType.trim()
                        )
                    } ?: emptyList()
                )
            } catch (e: Exception) {
                Log.e("SlmRepo", "온디바이스 추론 실패 -> Gemini Cloud 폴백 실행. 에러: ${e.message}")
                e.printStackTrace()
                fallbackToGemini(content)
            }
        } else {
            Log.d("SlmRepo", "온디바이스 모델 파일(.task)이 로컬 저장소에 존재하지 않아 Gemini Cloud 폴백 실행")
            fallbackToGemini(content)
        }
    }

    private suspend fun fallbackToGemini(content: String): SlmRepository.SlmAnalysisResult {
        return try {
            val geminiTags = geminiRepository.generateTags(content)
            SlmRepository.SlmAnalysisResult(
                tags = geminiTags,
                relations = emptyList()
            )
        } catch (fallbackEx: Exception) {
            Log.e("SlmRepo", "Gemini 폴백 호출 실패: ${fallbackEx.message}")
            fallbackEx.printStackTrace()
            SlmRepository.SlmAnalysisResult(
                tags = emptyList(),
                relations = emptyList()
            )
        }
    }
}
