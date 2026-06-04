package com.cookandroid.dotnote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cookandroid.dotnote.data.local.dao.MemoDao
import com.cookandroid.dotnote.data.local.entity.MemoEntity
import com.cookandroid.dotnote.data.local.entity.MemoRelationEntity
import com.cookandroid.dotnote.data.local.entity.MemoTagCrossRef
import com.cookandroid.dotnote.data.local.entity.TagEntity

@Database(
    entities = [
        MemoEntity::class,
        TagEntity::class,
        MemoTagCrossRef::class,
        MemoRelationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao
}
