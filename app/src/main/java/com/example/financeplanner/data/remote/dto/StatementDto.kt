package com.example.financeplanner.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatementDto(
    @SerialName("bank") val bank: String = "",
    @SerialName("account") val account: String = "",
    @SerialName("operations") val operations: List<OperationDto> = emptyList()
)