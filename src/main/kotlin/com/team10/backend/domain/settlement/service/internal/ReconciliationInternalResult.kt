// file: service/internal/ReconciliationInternalResult.kt
package com.team10.backend.domain.settlement.service.internal

import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.settlement.enums.UnmatchedReason
import java.time.LocalDateTime

/**
 * 🔹 서비스 내부 처리용 결과 (Entity 포함)
 * - SettlementBatchService 등 내부 로직에서 직접 사용
 * - API 응답용으로는 사용 금지
 */
data class ReconciliationInternalResult(
    val matched: List<Payment>,  // ✅ Entity 그대로 반환
    val unmatched: List<UnmatchedInternalDetail>,
    val pgResponseTime: LocalDateTime?
)

data class UnmatchedInternalDetail(
    val payment: Payment,  // ✅ Entity 참조 유지 → markAsUncertain() 호출 가능
    val reason: UnmatchedReason,
    val pgAmount: Long?,
    val dbAmount: Long
)