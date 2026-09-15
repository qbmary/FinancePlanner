package com.example.financeplanner.domain.model

data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val description: String,
    val mccCode: String,
    val category: String,
    val date: Long,
    val walletId: Long
)