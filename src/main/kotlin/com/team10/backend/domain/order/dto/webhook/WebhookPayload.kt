package com.team10.backend.domain.order.dto.webhook

//https://docs.tosspayments.com/reference/using-api/webhook-events#payment_status_changed
data class WebhookPayload(
    val eventType: String, // 웹훅의 목적이므로 반드시 존재해야 함
    val data: Data
) {
    data class Data(
        val paymentKey: String, // 결제 식별자 (필수)
        val orderId: String,    // 주문 식별자 (필수)
        val status: String,     // 결제 상태 (필수)
        val totalAmount: Long   // 결제 금액 (필수)
    )
}