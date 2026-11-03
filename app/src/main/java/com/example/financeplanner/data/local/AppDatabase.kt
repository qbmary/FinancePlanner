package com.example.financeplanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TransactionEntity::class,
        WalletEntity::class,
        LlmCacheEntity::class,
        MeterReadingEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun walletDao(): WalletDao
    abstract fun llmCacheDao(): LlmCacheDao
    abstract fun meterReadingDao(): MeterReadingDao
}
