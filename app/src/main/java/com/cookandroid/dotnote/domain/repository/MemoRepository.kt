package com.cookandroid.dotnote.domain.repository

import com.cookandroid.dotnote.data.local.entity.MemoEntity
import kotlinx.coroutines.flow.Flow

interface MemoRepository {
    suspend fun insertMemo(
        content: String, 
        tags: List<String>, 
        slmRelations: List<SlmRepository.SlmRelation>,
        latitude: Double? = null,
        longitude: Double? = null
    )
    suspend fun updateMemo(
        memoId: Long, 
        newContent: String, 
        tags: List<String>, 
        slmRelations: List<SlmRepository.SlmRelation>,
        latitude: Double? = null,
        longitude: Double? = null
    )
    suspend fun deleteMemo(memoId: Long)
    fun getAllMemos(): Flow<List<MemoEntity>>
    fun getAllMemosWithTags(): Flow<List<com.cookandroid.dotnote.data.local.entity.MemoWithTags>>
    fun getAllMemoRelations(): Flow<List<com.cookandroid.dotnote.data.local.entity.MemoRelationEntity>>
}
