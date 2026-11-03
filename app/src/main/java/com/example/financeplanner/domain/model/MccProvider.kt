package com.example.financeplanner.domain.model

/**
 * Абстракция над справочником MCC-кодов (assets/mcc_codes.json).
 *
 * Domain-слой не должен зависеть от android.content.Context,
 * поэтому use case'ы работают с этим интерфейсом, а реализация
 * (MccRepository) читает JSON из assets.
 */
interface MccProvider {
    fun getCategoryName(mccCode: String): String
    fun getIcon(mccCode: String): String
}
