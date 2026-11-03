package com.example.financeplanner.di

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.financeplanner.data.ai.LlmRepository
import com.example.financeplanner.data.ai.LlmRepositoryImpl
import com.example.financeplanner.data.ai.ModelProvider
import com.example.financeplanner.data.local.AppDatabase
import com.example.financeplanner.data.local.LlmCacheDao
import com.example.financeplanner.data.local.MeterReadingDao
import com.example.financeplanner.data.local.TransactionDao
import com.example.financeplanner.data.local.WalletDao
import com.example.financeplanner.data.prefs.SettingsRepository
import com.example.financeplanner.data.repository.FinanceRepository
import com.example.financeplanner.data.repository.MccRepository
import com.example.financeplanner.data.repository.MeterRepository
import com.example.financeplanner.data.voice.VoiceRepository
import com.example.financeplanner.data.voice.VoiceRepositoryImpl
import com.example.financeplanner.domain.model.MccProvider
import com.example.financeplanner.domain.model.MeterStore
import com.example.financeplanner.domain.usecase.AddVoiceExpenseUseCase
import com.example.financeplanner.domain.usecase.AnalyzeExpensesUseCase
import com.example.financeplanner.domain.usecase.ParseExpenseVoiceUseCase
import com.example.financeplanner.domain.usecase.RecordMeterReadingUseCase
import com.example.financeplanner.domain.usecase.SeedDemoDataUseCase
import com.example.financeplanner.ui.screens.assistant.AssistantViewModel
import com.example.financeplanner.ui.screens.detail.DetailViewModel
import com.example.financeplanner.ui.screens.home.HomeViewModel
import com.example.financeplanner.ui.screens.payments.PaymentsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Единая точка внедрения зависимостей (требование варианта 4 — Koin).
 *
 * Слои:
 *   data     — Room, DataStore, llama.cpp, SpeechRecognizer/TextToSpeech;
 *   domain   — use case'ы;
 *   ui       — ViewModel'и (создаются Koin'ом, Compose получает их через koinViewModel()).
 */
val appModule = module {

    // ------------------------------------------------------------------ Room

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "finance_database",
        ).addMigrations(migration1To2, migration2To3).build()
    }

    single<TransactionDao> { get<AppDatabase>().transactionDao() }
    single<WalletDao> { get<AppDatabase>().walletDao() }
    single<LlmCacheDao> { get<AppDatabase>().llmCacheDao() }
    single<MeterReadingDao> { get<AppDatabase>().meterReadingDao() }

    // ------------------------------------------------------------- хранилища

    single { SettingsRepository(androidContext()) }

    single { FinanceRepository(get(), get()) }

    single { MccRepository(androidContext()) }

    single<MccProvider> { get<MccRepository>() }

    single { MeterRepository(get()) }

    single<MeterStore> { get<MeterRepository>() }

    // ------------------------------------------------------------------- AI

    /** Синглтон: модель остаётся в памяти при переходах между экранами. */
    single<LlmRepository> { LlmRepositoryImpl(androidContext()) }

    single { ModelProvider(androidContext(), get()) }

    single { AnalyzeExpensesUseCase(llmRepository = get(), llmCacheDao = get()) }

    // --------------------------------------------------------------- голос

    /** factory: у каждого экрана свой экземпляр сервисов речи и свой destroy(). */
    factory<VoiceRepository> { VoiceRepositoryImpl(androidContext(), get()) }

    factory { ParseExpenseVoiceUseCase(mccProvider = get()) }

    factory { AddVoiceExpenseUseCase(financeRepository = get()) }

    factory { RecordMeterReadingUseCase(meterStore = get()) }

    factory { SeedDemoDataUseCase(financeRepository = get(), mccProvider = get()) }

    // ----------------------------------------------------------- ViewModel'и

    viewModel { (limit: Int, isHistoryMode: Boolean) ->
        HomeViewModel(
            repository = get(),
            limit = limit,
            isHistoryMode = isHistoryMode,
        )
    }

    viewModel { (transactionId: Long) ->
        DetailViewModel(
            repository = get(),
            mccRepository = get(),
            transactionId = transactionId,
        )
    }

    viewModel {
        PaymentsViewModel(
            get(),
            get(),
            androidContext(),
        )
    }

    viewModel {
        AssistantViewModel(
            financeRepository = get(),
            meterRepository = get(),
            llmRepository = get(),
            voiceRepository = get(),
            modelProvider = get(),
            settingsRepository = get(),
            analyzeExpensesUseCase = get(),
            parseExpenseVoiceUseCase = get(),
            addVoiceExpenseUseCase = get(),
            recordMeterReadingUseCase = get(),
            seedDemoDataUseCase = get(),
        )
    }
}

/** v1 → v2: кэш ответов LLM. */
private val migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS llm_cache " +
                "(promptHash TEXT NOT NULL PRIMARY KEY, " +
                "prompt TEXT NOT NULL, " +
                "response TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
    }
}

/** v2 → v3: показания счётчиков для голосового интерфейса. */
private val migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS meter_readings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "type TEXT NOT NULL, " +
                "value REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "period TEXT NOT NULL, " +
                "recordedAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_meter_readings_type_period " +
                "ON meter_readings (type, period)"
        )
    }
}
