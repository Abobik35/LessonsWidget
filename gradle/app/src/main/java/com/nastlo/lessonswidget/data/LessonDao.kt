package com.nastlo.lessonswidget.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {

    @Query("SELECT * FROM lessons ORDER BY weekday ASC, start ASC")
    fun observeAll(): Flow<List<Lesson>>

    @Query("SELECT * FROM lessons ORDER BY weekday ASC, start ASC")
    suspend fun getAll(): List<Lesson>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lesson: Lesson)

    @Delete
    suspend fun delete(lesson: Lesson)
}
