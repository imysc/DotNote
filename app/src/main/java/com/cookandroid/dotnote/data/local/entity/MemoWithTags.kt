package com.cookandroid.dotnote.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class MemoWithTags(
    @Embedded val memo: MemoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MemoTagCrossRef::class,
            parentColumn = "memoId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)
