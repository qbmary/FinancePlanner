package com.example.financeplanner.domain.usecase

import com.example.financeplanner.data.local.MeterReadingEntity
import com.example.financeplanner.domain.model.MeterStore
import com.example.financeplanner.domain.model.MeterType
import com.example.financeplanner.domain.util.RussianPlurals
import com.example.financeplanner.domain.util.VoiceNumberParser
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Сохранение показаний счётчика, введённых голосом, и озвучивание разницы
 * с прошлым месяцем (вариант 4: AI-функция ЛР №5 + голосовой интерфейс ЛР №6).
 */
class RecordMeterReadingUseCase(
    private val meterStore: MeterStore,
) {

    data class MeterUpdate(
        val type: MeterType,
        val value: Double,
        val unit: String,
        val periodLabel: String,
        val previousValue: Double?,
        val delta: Double?,
        /** Текст, который отправляется в TextToSpeech. */
        val speech: String,
        /** Короткая строка для экрана. */
        val message: String,
    )

    suspend operator fun invoke(
        spokenText: String,
        fallbackType: MeterType? = null,
    ): kotlin.Result<MeterUpdate> {
        val type = MeterType.detect(spokenText) ?: fallbackType
            ?: return kotlin.Result.failure(
                IllegalArgumentException(
                    "Не понял, какой это счётчик. Скажите, например: " +
                        "«электроэнергия тысяча двести пятьдесят»."
                )
            )

        val value = VoiceNumberParser.parse(spokenText)
            ?: return kotlin.Result.failure(
                IllegalArgumentException(
                    "Не удалось определить показание. Скажите так: " +
                        "«${type.title.lowercase(Locale.forLanguageTag("ru"))} тысяча двести пятьдесят»."
                )
            )

        if (value < 0.0) {
            return kotlin.Result.failure(
                IllegalArgumentException("Показание не может быть отрицательным")
            )
        }

        val zone = ZoneId.systemDefault()
        val currentMonth = YearMonth.now(zone)
        val previous = meterStore.previous(type.id, currentMonth.toString())
        val delta = previous?.let { value - it.value }

        val periodLabel = currentMonth.format(
            DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru"))
        )

        meterStore.save(
            MeterReadingEntity(
                type = type.id,
                value = value,
                unit = type.unit,
                period = currentMonth.toString(),
                recordedAt = System.currentTimeMillis(),
            )
        )

        val valueText = "${RussianPlurals.integer(value)} ${type.unitFor(value)}"

        val speechText = buildString {
            append("Показания счётчика «${type.title}» за $periodLabel сохранены: $valueText. ")
            if (previous == null || delta == null) {
                append("Это первые показания в приложении, сравнивать пока не с чем. ")
                append("В следующем месяце я озвучу разницу.")
            } else {
                append("Разница с прошлым месяцем: ")
                when {
                    delta > 0.0 -> append(
                        "плюс ${RussianPlurals.integer(delta)} ${type.unitFor(delta)}. "
                    )

                    delta < 0.0 -> append(
                        "минус ${RussianPlurals.integer(abs(delta))} ${type.unitFor(abs(delta))}. "
                    )

                    else -> append("расхода нет. ")
                }
                append(
                    "Прошлое показание — ${RussianPlurals.integer(previous.value)} " +
                        type.unitFor(previous.value) + "."
                )
            }
        }

        val shortMessage = buildString {
            append("${type.title}: $valueText за $periodLabel")
            when {
                previous == null || delta == null ->
                    append(" • сравнение появится со следующего месяца")

                delta > 0.0 ->
                    append(" • +${RussianPlurals.integer(delta)} ${type.unitFor(delta)}")

                delta < 0.0 ->
                    append(" • ${RussianPlurals.integer(delta)} ${type.unitFor(delta)}")

                else -> append(" • без изменений")
            }
        }

        return kotlin.Result.success(
            MeterUpdate(
                type = type,
                value = value,
                unit = type.unit,
                periodLabel = periodLabel,
                previousValue = previous?.value,
                delta = delta,
                speech = speechText,
                message = shortMessage,
            )
        )
    }
}
