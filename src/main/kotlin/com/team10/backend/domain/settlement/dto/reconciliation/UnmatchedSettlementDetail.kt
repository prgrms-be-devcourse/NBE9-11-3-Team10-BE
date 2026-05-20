package com.team10.backend.domain.settlement.dto.reconciliation

import com.team10.backend.domain.settlement.enums.UnmatchedReason

/**
 * PG 대조 실패 사유 및 상세 정보
 */
data class UnmatchedSettlementDetail(
    val paymentSummary: SettlementReconciliationResult.PaymentSummary,
    val reason: UnmatchedReason,
    val pgAmount: Long?,          // PG 측 금액 (누락 시 null)
    val dbAmount: Long,           // DB 측 금액
    val suggestion: String? = null  // 추후 자동 조치 제안 메시지 등 확장 가능
)