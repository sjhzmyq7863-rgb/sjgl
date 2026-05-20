package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_logs")
data class FocusLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // yyyy-MM-dd
    val durationMinutes: Int,
    val category: String, // 工作, 学习, 个人, 健身
    val timestamp: Long = System.currentTimeMillis()
)
