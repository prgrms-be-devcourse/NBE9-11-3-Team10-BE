package com.team10.backend.domain.order.dto.confirm



data class ConfirmRequest(
     val paymentKey: String,
     val orderId: String,
     val amount: Long
)