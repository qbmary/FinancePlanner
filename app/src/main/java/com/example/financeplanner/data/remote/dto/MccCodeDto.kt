package com.example.financeplanner.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MccCodeDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("icon") val icon: String = ""
)