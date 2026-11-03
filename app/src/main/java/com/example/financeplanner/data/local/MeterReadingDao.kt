package com.example.financeplanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterReadingDao {

    @Query("SELECT * FROM meter_readings ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings WHERE type = :type AND period = :period LIMIT 1")
    suspend fun findByPeriod(type: String, period: String): MeterReadingEntity?

    /** Последние показания по типу в любом месяце. */
    @Query("SELECT * FROM meter_readings WHERE type = :type ORDER BY period DESC LIMIT 1")
    suspend fun latest(type: String): MeterReadingEntity?

    /** Показания за предыдущий месяц относительно [period]. */
    @Query(
        "SELECT * FROM meter_readings WHERE type = :type AND period < :period " +
            "ORDER BY period DESC LIMIT 1"
    )
    suspend fun previous(type: String, period: String): MeterReadingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reading: MeterReadingEntity): Long

    @Query("DELETE FROM meter_readings")
    suspend fun clearAll()
}
