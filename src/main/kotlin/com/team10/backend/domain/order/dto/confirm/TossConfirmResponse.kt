package com.team10.backend.domain.order.dto.confirm

import com.team10.backend.domain.order.dto.webhook.WebhookPayload

data class TossConfirmResponse(
    val paymentKey: String,
    val orderId: String,
    val status: String
) {
    companion object {
        fun from(payload: WebhookPayload): TossConfirmResponse {
            return TossConfirmResponse(
                paymentKey = payload.data.paymentKey,
                orderId = payload.data.orderId,
                status = payload.data.status
            )
        }
    }
}
