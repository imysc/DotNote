package com.cookandroid.dotnote.data.repository

import android.util.Log
import com.cookandroid.dotnote.data.local.dao.MemoDao
import com.cookandroid.dotnote.data.local.entity.MemoEntity
import com.cookandroid.dotnote.data.local.entity.MemoRelationEntity
import com.cookandroid.dotnote.data.local.entity.MemoTagCrossRef
import com.cookandroid.dotnote.data.local.entity.TagEntity
import com.cookandroid.dotnote.domain.repository.MemoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MemoRepositoryImpl(
    private val memoDao: MemoDao
) : MemoRepository {
    override suspend fun insertMemo(
        content: String,
        tags: List<String>,
        slmRelations: List<com.cookandroid.dotnote.domain.repository.SlmRepository.SlmRelation>,
        latitude: Double?,
        longitude: Double?
    ) {
        withContext(Dispatchers.IO) {
            val memoId = memoDao.insertMemo(
                MemoEntity(
                    content = content,
                    latitude = latitude,
                    longitude = longitude
                )
            )
            Log.d("MemoRepo", "Memo inserted: id=$memoId, tags=$tags")

            // 1. 태그 저장 및 매핑
            tags.forEach { tagName ->
                val normalizedTag = tagName.trim().lowercase()
                if (normalizedTag.isEmpty()) return@forEach

                var tag = memoDao.getTagByName(normalizedTag)
                val tagId = if (tag == null) {
                    memoDao.insertTag(TagEntity(name = normalizedTag))
                } else {
                    tag.id
                }
                
                // 메모-태그 매핑 저장
                memoDao.insertMemoTagCrossRef(
                    MemoTagCrossRef(memoId = memoId, tagId = tagId)
                )
            }

            // 2. 이미 연결된 메모 ID를 추적하여 중복 Edge 방지
            val linkedMemoIds = mutableSetOf<Long>()

            // 3. 모델이 분석한 시맨틱 관계 매핑
            slmRelations.forEach { slmRel ->
                val keyword = slmRel.targetMemoKeyword
                if (keyword.isNotEmpty()) {
                    // 키워드를 포함하는 기존 메모 검색
                    val matchedMemos = memoDao.getMemosByKeyword(keyword)
                    matchedMemos.forEach { matched ->
                        if (matched.id != memoId && matched.id !in linkedMemoIds) {
                            memoDao.insertMemoRelation(
                                MemoRelationEntity(
                                    parentId = matched.id,
                                    childId = memoId,
                                    relationType = slmRel.relationType
                                )
                            )
                            linkedMemoIds.add(matched.id)
                            Log.d("MemoRepo", "Explicit linked: ${matched.id} ↔ $memoId (type: ${slmRel.relationType})")
                        }
                    }
                }
            }

            // 4. 공통 태그 기반 추가 자동 연결 적용 (명시적 엣지와 하이브리드로 병합 연결)
            tags.forEach { tagName ->
                val normalizedTag = tagName.trim().lowercase()
                if (normalizedTag.isEmpty()) return@forEach

                val tag = memoDao.getTagByName(normalizedTag)
                if (tag != null) {
                    val relatedMemos = memoDao.getMemosByTagId(tag.id)
                    for (related in relatedMemos) {
                        if (related.id != memoId && related.id !in linkedMemoIds) {
                            memoDao.insertMemoRelation(
                                MemoRelationEntity(
                                    parentId = related.id,
                                    childId = memoId,
                                    relationType = normalizedTag // 연관된 태그 이름 저장
                                )
                            )
                            linkedMemoIds.add(related.id)
                            Log.d("MemoRepo", "Auto-linked by tag: ${related.id} ↔ $memoId (tag: $normalizedTag)")
                        }
                    }
                }
            }
            Log.d("MemoRepo", "Total links created: ${linkedMemoIds.size}")
        }
    }

    override suspend fun updateMemo(
        memoId: Long,
        newContent: String,
        tags: List<String>,
        slmRelations: List<com.cookandroid.dotnote.domain.repository.SlmRepository.SlmRelation>,
        latitude: Double?,
        longitude: Double?
    ) = withContext(Dispatchers.IO) {
        memoDao.updateMemoContent(memoId, newContent, latitude, longitude)
        
        // 기존 태그 매핑 및 관계 삭제
        memoDao.deleteRelationsByMemoId(memoId)
        memoDao.deleteCrossRefsByMemoId(memoId)
        
        // 새로운 태그 반영 및 관계 재설정
        tags.forEach { tagName ->
            val normalizedTag = tagName.trim().lowercase()
            if (normalizedTag.isEmpty()) return@forEach

            var tag = memoDao.getTagByName(normalizedTag)
            val tagId = if (tag == null) {
                memoDao.insertTag(TagEntity(name = normalizedTag))
            } else {
                tag.id
            }
            
            memoDao.insertMemoTagCrossRef(
                MemoTagCrossRef(memoId = memoId, tagId = tagId)
            )
        }
        val linkedMemoIds = mutableSetOf<Long>()

        slmRelations.forEach { slmRel ->
            val keyword = slmRel.targetMemoKeyword
            if (keyword.isNotEmpty()) {
                val matchedMemos = memoDao.getMemosByKeyword(keyword)
                matchedMemos.forEach { matched ->
                    if (matched.id != memoId && matched.id !in linkedMemoIds) {
                        memoDao.insertMemoRelation(
                            MemoRelationEntity(
                                parentId = matched.id,
                                childId = memoId,
                                relationType = slmRel.relationType
                            )
                        )
                        linkedMemoIds.add(matched.id)
                        Log.d("MemoRepo", "Explicit updated linked: ${matched.id} ↔ $memoId (type: ${slmRel.relationType})")
                    }
                }
            }
        }

        // 4. 공통 태그 기반 추가 자동 연결 적용 (명시적 엣지와 하이브리드로 병합 연결)
        tags.forEach { tagName ->
            val normalizedTag = tagName.trim().lowercase()
            if (normalizedTag.isEmpty()) return@forEach

            val tag = memoDao.getTagByName(normalizedTag)
            if (tag != null) {
                val relatedMemos = memoDao.getMemosByTagId(tag.id)
                for (related in relatedMemos) {
                    if (related.id != memoId && related.id !in linkedMemoIds) {
                        memoDao.insertMemoRelation(
                            MemoRelationEntity(
                                parentId = related.id,
                                childId = memoId,
                                relationType = normalizedTag
                            )
                        )
                        linkedMemoIds.add(related.id)
                        Log.d("MemoRepo", "Auto-linked by tag on update: ${related.id} ↔ $memoId (tag: $normalizedTag)")
                    }
                }
            }
        }
    }

    // 메모 삭제 시 연결된 관계(Edge)와 태그 매핑도 함께 제거
    override suspend fun deleteMemo(memoId: Long) = withContext(Dispatchers.IO) {
        memoDao.deleteRelationsByMemoId(memoId)
        memoDao.deleteCrossRefsByMemoId(memoId)
        memoDao.deleteMemoById(memoId)
    }

    override fun getAllMemos(): Flow<List<MemoEntity>> {
        return memoDao.getAllMemos()
    }

    override fun getAllMemosWithTags(): Flow<List<com.cookandroid.dotnote.data.local.entity.MemoWithTags>> {
        return memoDao.getAllMemosWithTags()
    }

    override fun getAllMemoRelations(): Flow<List<MemoRelationEntity>> {
        return memoDao.getAllMemoRelations()
    }
}
