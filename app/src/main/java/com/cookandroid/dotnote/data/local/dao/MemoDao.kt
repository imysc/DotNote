package com.cookandroid.dotnote.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cookandroid.dotnote.data.local.entity.MemoEntity
import com.cookandroid.dotnote.data.local.entity.MemoRelationEntity
import com.cookandroid.dotnote.data.local.entity.MemoTagCrossRef
import com.cookandroid.dotnote.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemo(memo: MemoEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemoTagCrossRef(crossRef: MemoTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoRelation(relation: MemoRelationEntity)

    @Query("SELECT * FROM memos ORDER BY createdAt DESC")
    fun getAllMemos(): Flow<List<MemoEntity>>

    @Transaction
    @Query("SELECT * FROM memos ORDER BY createdAt DESC")
    fun getAllMemosWithTags(): Flow<List<com.cookandroid.dotnote.data.local.entity.MemoWithTags>>

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getMemoById(id: Long): MemoEntity?
    
    @Query("SELECT * FROM memos WHERE content LIKE '%' || :keyword || '%'")
    suspend fun getMemosByKeyword(keyword: String): List<MemoEntity>
    
    @Query("SELECT * FROM memo_relations WHERE parentId = :memoId OR childId = :memoId")
    suspend fun getMemoRelations(memoId: Long): List<MemoRelationEntity>

    @Query("SELECT m.* FROM memos m INNER JOIN memo_tag_cross_ref mt ON m.id = mt.memoId WHERE mt.tagId = :tagId")
    suspend fun getMemosByTagId(tagId: Long): List<MemoEntity>
    
    @Query("SELECT * FROM memo_relations")
    fun getAllMemoRelations(): Flow<List<MemoRelationEntity>>

    @Query("UPDATE memos SET content = :newContent, latitude = :latitude, longitude = :longitude WHERE id = :memoId")
    suspend fun updateMemoContent(memoId: Long, newContent: String, latitude: Double?, longitude: Double?)

    @Query("DELETE FROM memos WHERE id = :memoId")
    suspend fun deleteMemoById(memoId: Long)

    @Query("DELETE FROM memo_relations WHERE parentId = :memoId OR childId = :memoId")
    suspend fun deleteRelationsByMemoId(memoId: Long)

    @Query("DELETE FROM memo_tag_cross_ref WHERE memoId = :memoId")
    suspend fun deleteCrossRefsByMemoId(memoId: Long)
}
