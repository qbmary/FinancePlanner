package com.example.financeplanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "llm_cache")
data class LlmCacheEntity(
    @PrimaryKey val promptHash: String,
    val prompt: String,
    val response: String,
    val createdAt: Long = System.currentTimeMillis()
)
