package com.example.financeplanner.domain.model

import com.example.financeplanner.domain.util.RussianPlurals

/**
 * Тип счётчика, показания которого диктует пользователь (вариант 4).
 *
 * Каждому типу соответствует единица измерения в трёх формах склонения
 * (для корректного озвучивания) и набор ключевых слов, по которым
 * приложение понимает голосовую команду.
 */
enum class MeterType(
    val id: String,
    val title: String,
    val unit: String,
    val unitOne: String,
    val unitFew: String,
    val unitMany: String,
    val keywords: List<String>,
) {
    ELECTRICITY(
        id = "electricity",
        title = "Электроэнергия",
        unit = "кВт·ч",
        unitOne = "киловатт-час",
        unitFew = "киловатт-часа",
        unitMany = "киловатт-часов",
        keywords = listOf("электр", "свет", "квт", "киловатт", "электроэнерг"),
    ),
    COLD_WATER(
        id = "cold_water",
        title = "Холодная вода",
        unit = "м³",
        unitOne = "кубометр",
        unitFew = "кубометра",
        unitMany = "кубометров",
        keywords = listOf("холодн", "хвс", "вода холод", "холодная вода"),
    ),
    HOT_WATER(
        id = "hot_water",
        title = "Горячая вода",
        unit = "м³",
        unitOne = "кубометр",
        unitFew = "кубометра",
        unitMany = "кубометров",
        keywords = listOf("горяч", "гвс", "вода горяч", "горячая вода"),
    ),
    GAS(
        id = "gas",
        title = "Газ",
        unit = "м³",
        unitOne = "кубометр",
        unitFew = "кубометра",
        unitMany = "кубометров",
        keywords = listOf("газ"),
    ),
    HEATING(
        id = "heating",
        title = "Отопление",
        unit = "Гкал",
        unitOne = "гигакалория",
        unitFew = "гигакалории",
        unitMany = "гигакалорий",
        keywords = listOf("отоплен", "тепло", "гигакалор"),
    ),
    ;

    /** Единица измерения в правильной форме для числа [value]. */
    fun unitFor(value: Double): String =
        RussianPlurals.plural(value, unitOne, unitFew, unitMany)

    companion object {
        fun fromId(id: String): MeterType =
            entries.firstOrNull { it.id == id } ?: ELECTRICITY

        /** Определяет тип счётчика по тексту голосовой команды. */
        fun detect(text: String): MeterType? {
            val normalized = text.lowercase().replace('ё', 'е')
            return entries.firstOrNull { type ->
                type.keywords.any { keyword -> normalized.contains(keyword) }
            }
        }
    }
}
