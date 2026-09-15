package com.example.financeplanner.di

import androidx.room.Room
import com.example.financeplanner.data.local.AppDatabase
import com.example.financeplanner.data.local.TransactionDao
import com.example.financeplanner.data.local.WalletDao
import com.example.financeplanner.data.repository.FinanceRepository
import com.example.financeplanner.data.repository.MccRepository
import com.example.financeplanner.ui.screens.detail.DetailViewModel
import com.example.financeplanner.ui.screens.home.HomeViewModel
import com.example.financeplanner.ui.screens.payments.PaymentsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "finance_database"
        ).build()
    }

    single<TransactionDao> { get<AppDatabase>().transactionDao() }
    single<WalletDao> { get<AppDatabase>().walletDao() }

    single { FinanceRepository(get(), get()) }
    single { MccRepository(androidContext()) }

    viewModel { (limit: Int) ->
        HomeViewModel(repository = get(), limit = limit, isHistoryMode = false)
    }

    viewModel { (limit: Int, isHistoryMode: Boolean) ->
        HomeViewModel(repository = get(), limit = limit, isHistoryMode = isHistoryMode)
    }

    viewModel { (transactionId: Long) ->
        DetailViewModel(
            repository = get(),
            mccRepository = get(),
            transactionId = transactionId
        )
    }

    // PaymentsViewModel — теперь с Context
    viewModel { PaymentsViewModel(get(), get(), androidContext()) }
}