package com.example.financeplanner.data.repository

import com.example.financeplanner.data.local.TransactionDao
import com.example.financeplanner.data.local.TransactionEntity
import com.example.financeplanner.data.local.WalletDao
import com.example.financeplanner.data.local.WalletEntity
import kotlinx.coroutines.flow.Flow

class FinanceRepository(
    private val transactionDao: TransactionDao,
    private val walletDao: WalletDao
) {
    fun getAllTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.getAllTransactions()

    suspend fun addTransaction(transaction: TransactionEntity) =
        transactionDao.insertTransaction(transaction)

    suspend fun addTransactions(transactions: List<TransactionEntity>) =
        transactionDao.insertTransactions(transactions)

    suspend fun clearAllTransactions() =
        transactionDao.clearAll()

    fun getAllWallets(): Flow<List<WalletEntity>> =
        walletDao.getAllWallets()

    suspend fun addWallet(wallet: WalletEntity) =
        walletDao.insertWallet(wallet)
}