package com.example.financeplanner.domain.usecase

import com.example.financeplanner.domain.model.MccProvider
import com.example.financeplanner.domain.util.VoiceNumberParser
import java.util.Locale

/**
 * Разбор голосовой команды о трате: «потратила двадцать пять рублей на такси».
 *
 * Сумма вытаскивается из цифр или числительных, категория — по ключевым словам.
 * MCC-код и название категории берутся из того же справочника assets/mcc_codes.json,
 * что и при импорте выписки, поэтому категории в приложении не расходятся.
 */
class ParseExpenseVoiceUseCase(
    private val mccProvider: MccProvider,
) {

    data class ParsedExpense(
        val amount: Double,
        val mccCode: String,
        val category: String,
        val description: String,
        val rawText: String,
    )

    private data class CategoryRule(
        val mcc: String,
        val keywords: List<String>,
    )

    operator fun invoke(text: String): Result<ParsedExpense> {
        val normalized = text.lowercase(Locale.forLanguageTag("ru")).replace('ё', 'е')

        val amount = VoiceNumberParser.parse(normalized)
            ?: return Result.failure(
                IllegalArgumentException(
                    "Не удалось определить сумму. Скажите, например: " +
                        "«потратила двадцать пять рублей на такси»."
                )
            )

        if (amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Сумма траты должна быть больше нуля"))
        }

        val rule = categoryRules.firstOrNull { categoryRule ->
            categoryRule.keywords.any { keyword -> normalized.contains(keyword) }
        }

        val mccCode = rule?.mcc ?: DEFAULT_MCC
        val category = mccProvider.getCategoryName(mccCode)
            .takeIf { it.isNotBlank() && it != UNKNOWN_CATEGORY }
            ?: "Прочее"

        return Result.success(
            ParsedExpense(
                amount = amount,
                mccCode = mccCode,
                category = category,
                description = buildDescription(text, category),
                rawText = text,
            )
        )
    }

    private fun buildDescription(raw: String, category: String): String {
        val cleaned = raw
            .replace(Regex("[,.!?]+$"), "")
            .trim()
            .replaceFirstChar { it.uppercase(Locale.forLanguageTag("ru")) }

        return if (cleaned.length > 60) cleaned.take(57) + "…" else cleaned
            .ifBlank { category }
    }

    private companion object {
        const val DEFAULT_MCC = "9999"
        const val UNKNOWN_CATEGORY = "Неизвестно"

        /**
         * Порядок важен: проверяется первое совпадение.
         */
        val categoryRules = listOf(
            CategoryRule(
                "4121",
                listOf("такси", "яндекс го", "убер", "bolt", "каршеринг"),
            ),
            CategoryRule(
                "5812",
                listOf("кафе", "ресторан", "кофе", "обед", "ужин", "столовая", "бар"),
            ),
            CategoryRule(
                "5814",
                listOf("фастфуд", "макдональдс", "бургер", "шаурм", "пицц"),
            ),
            CategoryRule(
                "5411",
                listOf(
                    "продукт", "супермаркет", "магазин продукт", "пятерочк",
                    "пятёрочк", "еда", "покупа", "универсам", "гипермаркет",
                ),
            ),
            CategoryRule(
                "5541",
                listOf("бензин", "азс", "заправк", "топлив", "дизел"),
            ),
            CategoryRule(
                "4111",
                listOf("транспорт", "автобус", "троллейбус", "трамвай", "метро", "проезд"),
            ),
            CategoryRule(
                "5912",
                listOf("аптек", "лекарств", "медикамент", "таблетк"),
            ),
            CategoryRule(
                "8011",
                listOf("врач", "клиник", "стоматолог", "анализ"),
            ),
            CategoryRule(
                "5651",
                listOf("одежд", "куртк", "джинс", "футболк", "платье"),
            ),
            CategoryRule(
                "5691",
                listOf("обув", "кроссовк", "ботинк", "сапог"),
            ),
            CategoryRule(
                "5732",
                listOf("электроник", "наушник", "зарядк", "ноутбук", "телефон купил"),
            ),
            CategoryRule(
                "5399",
                listOf("маркетплейс", "озон", "ozon", "вайлдберриз", "wildberries", "aliexpress"),
            ),
            CategoryRule(
                "7832",
                listOf("кино", "кинотеатр", "фильм", "театр"),
            ),
            CategoryRule(
                "7997",
                listOf("фитнес", "спортзал", "тренажер", "бассейн", "спорт"),
            ),
            CategoryRule(
                "5942",
                listOf("книг", "учебник"),
            ),
            CategoryRule(
                "4899",
                listOf("интернет", "кабельн", "подписк"),
            ),
            CategoryRule(
                "4814",
                listOf("мобильн", "связь", "пополнил телефон"),
            ),
            CategoryRule(
                "4900",
                listOf("коммунал", "квартплат", "жкх", "свет", "вода"),
            ),
            CategoryRule(
                "5310",
                listOf("фикс прайс", "дискаунтер"),
            ),
        )
    }
}
