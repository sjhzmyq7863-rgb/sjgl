package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: String, // yyyy-MM-dd
    val startTime: String, // HH:mm
    val endTime: String, // HH:mm
    val category: String, // 工作, 学习, 个人, 健身
    val isCompleted: Boolean = false,
    val isKeyTask: Boolean = false,
    val hasReminder: Boolean = false,
    val reminderTimeMillis: Long? = null,
    val calendarEventId: Long? = null
)
