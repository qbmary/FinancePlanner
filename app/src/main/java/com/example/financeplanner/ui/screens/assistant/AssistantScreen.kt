package com.example.financeplanner.ui.screens.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financeplanner.data.ai.GenerationStatus
import com.example.financeplanner.data.ai.ModelOrigin
import com.example.financeplanner.data.ai.ModelStatus
import com.example.financeplanner.data.voice.VoiceUiState
import com.example.financeplanner.domain.model.MeterType
import com.example.financeplanner.domain.util.RussianPlurals
import org.koin.androidx.compose.koinViewModel

private val Accent = Color(0xFF8B5CF6)
private val Success = Color(0xFF4ADE80)
private val Danger = Color(0xFFFF6B6B)
private val Warning = Color(0xFFFBBF24)

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Выбор GGUF-файла через системный диалог (ActivityResultContracts.OpenDocument).
    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onModelPicked(uri)
    }

    var pendingVoiceMode by remember { mutableStateOf(VoiceInputMode.EXPENSE) }

    val microphonePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput(pendingVoiceMode)
        }
    }

    fun requestVoice(mode: VoiceInputMode) {
        pendingVoiceMode = mode
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            viewModel.startVoiceInput(mode)
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B),
                        Color(0xFF0F172A),
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Финансовый ассистент",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Баланс",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                    Text(
                        text = RussianPlurals.byn(state.balance),
                        color = Success,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                StatusChip(
                    text = if (state.modelReady) "AI офлайн" else "AI не загружен",
                    color = if (state.modelReady) Success else Warning,
                )
            }

            // ------------------------------------------------- ЛР 5: модель
            SectionCard(
                title = "5.1. Локальная LLM (On-Device AI)",
                subtitle = "Qwen-2.5-1.5B-Instruct Q4_K_M · llama.cpp на устройстве",
            ) {
                ModelSection(
                    state = state,
                    onPickModel = { modelPicker.launch(arrayOf("*/*")) },
                    onReload = viewModel::onReloadModel,
                    onUnload = viewModel::onUnloadModel,
                    onForget = viewModel::onForgetModel,
                )
            }

            // ------------------------------------------------ ЛР 5: отчёт
            SectionCard(
                title = "5.2. AI-отчёт по расходам",
                subtitle = "Промпт → локальная генерация → кэш в Room",
            ) {
                if (state.isAnalyzing) {
                    val status = state.generation
                    if (status is GenerationStatus.Streaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Accent,
                            )
                            Text(
                                text = "Модель печатает… ${status.tokens} токенов",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { viewModel.analyzeExpenses(forceRefresh = false) },
                        modifier = Modifier.weight(1f),
                        enabled = state.modelReady && !state.isAnalyzing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(if (state.isAnalyzing) "Анализ…" else "Сформировать отчёт")
                    }

                    OutlinedButton(
                        onClick = { viewModel.analyzeExpenses(forceRefresh = true) },
                        enabled = state.modelReady && !state.isAnalyzing,
                    ) {
                        Text("Заново", color = Color.White)
                    }
                }

                if (!state.modelReady && !state.isLoadingModel) {
                    Text(
                        text = "Отчёт станет доступен после загрузки модели.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                    )
                }

                if (state.report.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    ResultBox(
                        title = if (state.reportFromCache) "Отчёт из кэша" else "Отчёт AI",
                        text = state.report,
                        accent = if (state.reportFromCache) Warning else Success,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::speakReport,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (state.isSpeaking) "🔊 Читаю…" else "🔊 Озвучить",
                                color = Color.White,
                            )
                        }
                        if (state.isSpeaking) {
                            OutlinedButton(
                                onClick = viewModel::stopSpeaking,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Стоп", color = Color.White)
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------- ЛР 6: голос
            SectionCard(
                title = "6.1. Голос: добавить трату",
                subtitle = "Скажите сумму и категорию — приложение озвучит остаток бюджета",
            ) {
                VoiceButtons(
                    state = state,
                    mode = VoiceInputMode.EXPENSE,
                    label = "🎤 Добавить трату голосом",
                    onStart = { requestVoice(VoiceInputMode.EXPENSE) },
                    onStop = viewModel::stopVoiceInput,
                )

                Text(
                    text = "Пример: «потратила двадцать пять рублей на такси»",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )
            }

            SectionCard(
                title = "6.2. Голос: показания счётчиков",
                subtitle = "Приложение сохранит показание и озвучит разницу с прошлым месяцем",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MeterType.entries.forEach { type ->
                        ChoiceChip(
                            text = type.title,
                            selected = state.selectedMeter == type,
                            onClick = { viewModel.selectMeterType(type) },
                        )
                    }
                }

                VoiceButtons(
                    state = state,
                    mode = VoiceInputMode.METER,
                    label = "🎤 Продиктовать показания",
                    onStart = { requestVoice(VoiceInputMode.METER) },
                    onStop = viewModel::stopVoiceInput,
                )

                Text(
                    text = "Пример: «электроэнергия тысяча двести пятьдесят»",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )

                if (state.meters.any { it.value != null }) {
                    Spacer(Modifier.height(4.dp))
                    state.meters.filter { it.value != null }.forEach { summary ->
                        MeterRow(summary = summary)
                    }
                }
            }

            // ------------------------------------------- ЛР 6: настройки
            SectionCard(
                title = "6.3. Настройки голоса",
                subtitle = "Хранятся в Jetpack DataStore",
            ) {
                ToggleRow(
                    title = "Офлайн-распознавание",
                    description = "EXTRA_PREFER_OFFLINE = true. Работает, если на устройстве " +
                        "загружен офлайн-пакет русского языка.",
                    checked = state.preferOfflineRecognition,
                    onCheckedChange = viewModel::onPreferOfflineRecognitionChanged,
                )
                ToggleRow(
                    title = "Озвучивать ответы",
                    description = "TextToSpeech читает отчёт и подтверждения операций.",
                    checked = state.speakAnswers,
                    onCheckedChange = viewModel::onSpeakAnswersChanged,
                )
            }

            SectionCard(
                title = "Демо-данные",
                subtitle = "Заполняет базу операциями за текущий и прошлый месяц",
            ) {
                Button(
                    onClick = viewModel::seedDemoData,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Добавить демо-операции")
                }
                Text(
                    text = "Нужно один раз перед показом AI-отчёта: модель анализирует " +
                        "текущий и предыдущий календарный месяц.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )
            }

            state.recognizedText.takeIf { it.isNotBlank() }?.let { text ->
                ResultBox(
                    title = "Распознано",
                    text = text,
                    accent = Color.White.copy(alpha = 0.8f),
                )
            }

            state.voiceResult.takeIf { it.isNotBlank() }?.let { text ->
                ResultBox(title = "Результат", text = text, accent = Success)
            }

            state.notice?.let { text ->
                ResultBox(title = "Статус", text = text, accent = Success)
            }

            state.error?.let { text ->
                ResultBox(title = "Ошибка", text = text, accent = Danger)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ модель

@Composable
private fun ModelSection(
    state: AssistantUiState,
    onPickModel: () -> Unit,
    onReload: () -> Unit,
    onUnload: () -> Unit,
    onForget: () -> Unit,
) {
    val compatibility = state.compatibility

    when (val status = state.modelStatus) {
        is ModelStatus.Ready -> StatusChip(
            text = "Модель загружена за ${status.loadTimeMs / 1000} с",
            color = Success,
        )

        ModelStatus.Loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Accent,
            )
            Text(
                text = "Загружаем модель в память…",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
            )
        }

        is ModelStatus.Failed -> StatusChip(text = "Ошибка загрузки модели", color = Danger)
        ModelStatus.NotLoaded -> StatusChip(text = "Модель не загружена", color = Warning)
    }

    if (state.isPreparingModel) {
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { state.prepareProgress / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = Accent,
        )
        Text(
            text = "Копируем модель из APK: ${state.prepareProgress}%",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
    }

    state.model?.let { model ->
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${model.displayName} · ${model.sizeMb} МБ · ${originLabel(model.origin)}",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
        )
    }

    compatibility?.let { info ->
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Устройство: ${info.abi} · ОЗУ ${"%.1f".format(info.totalRamGb)} ГБ · " +
                "свободно ${"%.1f".format(info.freeStorageGb)} ГБ",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
        )

        info.blockers.forEach { blocker ->
            Spacer(Modifier.height(4.dp))
            Text(text = "⛔ $blocker", color = Danger, fontSize = 12.sp)
        }

        info.warnings.forEach { warning ->
            Spacer(Modifier.height(4.dp))
            Text(text = "⚠️ $warning", color = Warning, fontSize = 12.sp)
        }
    }

    Spacer(Modifier.height(6.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onPickModel,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
            ),
        ) {
            Text(if (state.modelReady) "Другая модель" else "Выбрать GGUF-файл")
        }

        when {
            state.modelReady -> OutlinedButton(onClick = onUnload) {
                Text("Выгрузить", color = Color.White)
            }

            state.isLoadingModel -> OutlinedButton(onClick = {}, enabled = false) {
                Text("Загрузка…", color = Color.White.copy(alpha = 0.6f))
            }

            else -> OutlinedButton(onClick = onReload) {
                Text("Загрузить", color = Color.White)
            }
        }
    }

    if (state.model != null) {
        Text(
            text = "Забыть выбранный файл",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onForget() }
                .padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}

private fun originLabel(origin: ModelOrigin): String = when (origin) {
    ModelOrigin.PICKED -> "выбрано вручную"
    ModelOrigin.APP_STORAGE -> "файл приложения"
    ModelOrigin.BUNDLED -> "из APK"
}

// ------------------------------------------------------------------ голос

@Composable
private fun VoiceButtons(
    state: AssistantUiState,
    mode: VoiceInputMode,
    label: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val listening = state.voiceState is VoiceUiState.Listening ||
        state.voiceState is VoiceUiState.Processing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { if (listening) onStop() else onStart() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (listening) Danger.copy(alpha = 0.85f) else Accent,
                contentColor = Color.White,
            ),
        ) {
            Text(
                when (state.voiceState) {
                    VoiceUiState.Listening -> "⏹ Слушаю… (остановить)"
                    VoiceUiState.Processing -> "Обрабатываю…"
                    else -> label
                }
            )
        }
    }

    if (state.voiceMode == mode && state.partialVoiceText.isNotBlank() &&
        state.voiceState is VoiceUiState.Listening
    ) {
        Text(
            text = "…${state.partialVoiceText}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun MeterRow(summary: MeterSummary) {
    val value = summary.value ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.type.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary.periodLabel ?: "",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${RussianPlurals.integer(value)} ${summary.type.unit}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            summary.delta?.let { delta ->
                Text(
                    text = when {
                        delta > 0.0 -> "▲ ${RussianPlurals.integer(delta)} к прошлому месяцу"
                        delta < 0.0 -> "▼ ${RussianPlurals.integer(-delta)} к прошлому месяцу"
                        else -> "без изменений"
                    },
                    color = when {
                        delta > 0.0 -> Warning
                        delta < 0.0 -> Success
                        else -> Color.White.copy(alpha = 0.6f)
                    },
                    fontSize = 11.sp,
                )
            }
        }
    }
}

// ------------------------------------------------------------- компоненты

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        }

        content()
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) Accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f)
            )
            .border(
                1.dp,
                if (selected) Accent else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(20.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent,
            ),
        )
    }
}

@Composable
private fun ResultBox(
    title: String,
    text: String,
    accent: Color,
    monospace: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        )
    }
}
