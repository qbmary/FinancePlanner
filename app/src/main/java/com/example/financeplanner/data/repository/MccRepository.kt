package com.example.financeplanner.data.repository

import android.content.Context
import com.example.financeplanner.data.remote.dto.MccCodeDto
import com.example.financeplanner.domain.model.MccProvider
import kotlinx.serialization.json.Json

class MccRepository(
    private val context: Context
) : MccProvider {

    private val json = Json { ignoreUnknownKeys = true }

    // Кэш: загружаем один раз, держим в памяти
    private var cachedMccCodes: Map<String, MccCodeDto>? = null

    /**
     * Загружает справочник MCC из assets. Кэширует результат.
     */
    fun getMccCodes(): Map<String, MccCodeDto> {
        cachedMccCodes?.let { return it }

        val rawJson = context.assets
            .open("mcc_codes.json")
            .bufferedReader()
            .use { it.readText() }

        val list: List<MccCodeDto> = json.decodeFromString(rawJson)
        val map = list.associateBy { it.id }
        cachedMccCodes = map
        return map
    }

    /**
     * Возвращает название категории по MCC-коду.
     * Если код не найден — возвращает "Неизвестно".
     */
    override fun getCategoryName(mccCode: String): String {
        return getMccCodes()[mccCode]?.name ?: "Неизвестно"
    }

    /**
     * Возвращает иконку (эмодзи) по MCC-коду.
     */
    override fun getIcon(mccCode: String): String {
        return getMccCodes()[mccCode]?.icon ?: "❓"
    }
}
