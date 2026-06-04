package com.cookandroid.dotnote.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "memo_relations",
    primaryKeys = ["parentId", "childId"],
    indices = [Index("childId")]
)
data class MemoRelationEntity(
    val parentId: Long,
    val childId: Long,
    val relationType: String = "related" // e.g., "parent_child", "related", "semantic_similar"
)
