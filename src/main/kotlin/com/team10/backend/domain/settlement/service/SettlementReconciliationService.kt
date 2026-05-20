package com.team10.backend.domain.settlement.service

import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.settlement.enums.UnmatchedReason
import com.team10.backend.domain.settlement.service.internal.ReconciliationInternalResult
import com.team10.backend.domain.settlement.service.internal.UnmatchedInternalDetail
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class SettlementReconciliationService(
    // private val tossApiClient: TossApiClient // 실제 연동 시 주석 해제
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun reconcileInternal(
        payments: List<Payment>,
        sellerId: Long,
        targetDate: LocalDate
    ): ReconciliationInternalResult {
        val unmatched = mutableListOf<UnmatchedInternalDetail>()
        val matched = mutableListOf<Payment>()

        payments.forEach { payment ->
            try {
                val pgData = mockFetchPgSettlement(payment)

                when {
                    pgData == null -> {
                        unmatched.add(UnmatchedInternalDetail(payment, UnmatchedReason.NOT_FOUND_IN_PG, null, payment.totalAmount.toLong()))
                    }
                    pgData.amount != payment.totalAmount.toLong() -> {
                        unmatched.add(UnmatchedInternalDetail(payment, UnmatchedReason.AMOUNT_MISMATCH, pgData.amount, payment.totalAmount.toLong()))
                    }
                    pgData.status !in listOf("PAID", "DONE") -> {
                        unmatched.add(UnmatchedInternalDetail(payment, UnmatchedReason.STATUS_MISMATCH, pgData.amount, payment.totalAmount.toLong()))
                    }
                    else -> {
                        matched.add(payment)
                    }
                }
            } catch (e: Exception) {
                log.warn("Reconciliation failed for payment ${payment.orderNumber}: ${e.message}")
                unmatched.add(UnmatchedInternalDetail(payment, UnmatchedReason.NETWORK_ERROR, null, payment.totalAmount.toLong()))
            }
        }

        return ReconciliationInternalResult(matched, unmatched, LocalDateTime.now())
    }

    // === 모의 PG 응답 (테스트용) ===
    private data class PgSettlementData(val amount: Long, val status: String)
    private fun mockFetchPgSettlement(payment: Payment): PgSettlementData {
        return PgSettlementData(payment.totalAmount.toLong(), "PAID")
    }
}