package com.example.financeplanner.domain.model

import com.example.financeplanner.data.local.MeterReadingEntity

/**
 * Минимальный контракт хранилища показаний счётчиков, нужный use case'у.
 * Реализация — MeterRepository поверх Room; в тестах подменяется фейком.
 */
interface MeterStore {
    suspend fun previous(type: String, period: String): MeterReadingEntity?
    suspend fun findByPeriod(type: String, period: String): MeterReadingEntity?
    suspend fun save(reading: MeterReadingEntity): Long
}
