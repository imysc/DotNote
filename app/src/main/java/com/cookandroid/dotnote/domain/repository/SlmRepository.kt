package com.cookandroid.dotnote.domain.repository

import com.google.gson.annotations.SerializedName

/**
 * 로컬 SLM(Gemma-2)을 활용하여 메모의 메타데이터를 추출하기 위한 리포지토리 인터페이스
 */
interface SlmRepository {

    /**
     * 모델이 분석한 개별 시맨틱 연결 관계 정보
     */
    data class SlmRelation(
        @SerializedName("target_memo_keyword")
        val targetMemoKeyword: String,
        @SerializedName("relation_type")
        val relationType: String
    )

    /**
     * 모델이 분석한 태그 및 관계망 결과
     */
    data class SlmAnalysisResult(
        val tags: List<String> = emptyList(),
        val relations: List<SlmRelation> = emptyList()
    )

    /**
     * 메모 본문을 분석하여 태그와 관계 정보 DTO를 반환합니다.
     */
    suspend fun analyzeMemo(content: String): SlmAnalysisResult
}
