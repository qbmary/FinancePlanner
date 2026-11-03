package com.example.financeplanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LlmCacheDao {
    @Query("SELECT * FROM llm_cache WHERE promptHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): LlmCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: LlmCacheEntity)
}
