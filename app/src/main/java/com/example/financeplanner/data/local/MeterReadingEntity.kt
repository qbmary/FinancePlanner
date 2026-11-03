package com.example.financeplanner.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Показания счётчика за конкретный месяц.
 *
 * Уникальный индекс (type, period) не даёт продублировать показания:
 * повторный ввод за тот же месяц перезаписывает значение.
 */
@Entity(
    tableName = "meter_readings",
    indices = [Index(value = ["type", "period"], unique = true)],
)
data class MeterReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val value: Double,
    val unit: String,
    /** Период в формате yyyy-MM. */
    val period: String,
    val recordedAt: Long = System.currentTimeMillis(),
)
