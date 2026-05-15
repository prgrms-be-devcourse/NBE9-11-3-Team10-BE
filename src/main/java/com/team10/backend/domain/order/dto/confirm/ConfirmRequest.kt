package com.team10.backend.domain.order.dto.confirm



data class ConfirmRequest(
     @JvmField val paymentKey: String,
     @JvmField val orderId: String,
     @JvmField val amount: Long
)