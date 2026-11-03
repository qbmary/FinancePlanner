package com.example.financeplanner.data.repository

import com.example.financeplanner.data.local.MeterReadingDao
import com.example.financeplanner.data.local.MeterReadingEntity
import com.example.financeplanner.domain.model.MeterStore
import kotlinx.coroutines.flow.Flow

/** Доступ к показаниям счётчиков (лабораторная №6, вариант 4). */
class MeterRepository(
    private val meterReadingDao: MeterReadingDao,
) : MeterStore {

    fun observeAll(): Flow<List<MeterReadingEntity>> = meterReadingDao.observeAll()

    suspend fun latest(type: String): MeterReadingEntity? = meterReadingDao.latest(type)

    override suspend fun findByPeriod(type: String, period: String): MeterReadingEntity? =
        meterReadingDao.findByPeriod(type, period)

    override suspend fun previous(type: String, period: String): MeterReadingEntity? =
        meterReadingDao.previous(type, period)

    override suspend fun save(reading: MeterReadingEntity): Long = meterReadingDao.upsert(reading)
}
