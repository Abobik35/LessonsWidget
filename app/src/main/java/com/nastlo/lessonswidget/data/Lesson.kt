package com.nastlo.lessonswidget.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Один урок в недельном расписании.
 * Время хранится строкой "HH:mm" (например "09:20").
 */
@Entity(tableName = "lessons")
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** День недели: 1 = понедельник ... 7 = воскресенье. */
    val weekday: Int,
    val subject: String,
    val room: String,
    val floor: Int? = null,
    val teacher: String? = null,
    val note: String? = null,
    val start: String,
    val end: String,
)
