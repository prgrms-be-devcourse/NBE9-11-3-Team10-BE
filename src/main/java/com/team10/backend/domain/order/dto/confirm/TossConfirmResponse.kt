package com.team10.backend.domain.order.dto.confirm

import com.team10.backend.domain.order.dto.webhook.WebhookPayload

data class TossConfirmResponse(
    @JvmField val paymentKey: String,
    @JvmField val orderId: String,
    @JvmField val status: String
) {
    companion object {
        @JvmStatic
        fun from(payload: WebhookPayload): TossConfirmResponse {
            return TossConfirmResponse(
                paymentKey = payload.data.paymentKey,
                orderId = payload.data.orderId,
                status = payload.data.status
            )
        }
    }
}
