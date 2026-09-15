package com.example.financeplanner.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OperationDto(
    @SerialName("date") val date: String,
    @SerialName("description") val description: String,
    @SerialName("amount") val amount: Double,
    @SerialName("mcc") val mcc: String = "9999"
)