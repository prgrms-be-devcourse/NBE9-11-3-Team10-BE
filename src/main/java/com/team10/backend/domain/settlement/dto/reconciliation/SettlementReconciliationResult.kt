package com.team10.backend.domain.settlement.dto.reconciliation

import com.team10.backend.domain.order.entity.Payment
import java.time.LocalDateTime

/**
 * PG 정산 대조 결과 응답 객체
 * - Controller 에서 API 응답으로 직접 사용 가능
 * - 추후 정산 리포트 생성, 알림 발송 등 다양한 유즈케이스에서 재사용 목적
 */
data class SettlementReconciliationResult(
    val matchedPayments: List<PaymentSummary>,
    val unmatchedDetails: List<UnmatchedSettlementDetail>,
    val reconciledAt: LocalDateTime,
    val pgResponseTime: LocalDateTime? = null
) {
    // 🔸 Entity 전체를 노출하지 않기 위한 경량 DTO
    data class PaymentSummary(
        val paymentId: Long,
        val orderNumber: String,
        val amount: Long,
        val status: String
    ) {
        companion object {
            fun from(payment: Payment): PaymentSummary {
                return PaymentSummary(
                    paymentId = payment.id!!,
                    orderNumber = payment.orderNumber,
                    amount = payment.totalAmount.toLong(),
                    status = payment.status.name
                )
            }
        }
    }
}