package com.team10.backend.domain.order.dto


data class OrderDeleteResponse(
    val orderNumber: String?,
    val status: String? // "DELETED" 등
)
