package com.cookandroid.dotnote.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "memo_tag_cross_ref",
    primaryKeys = ["memoId", "tagId"],
    indices = [Index("tagId")]
)
data class MemoTagCrossRef(
    val memoId: Long,
    val tagId: Long
)
