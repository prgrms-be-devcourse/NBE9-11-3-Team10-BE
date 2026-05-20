package com.team10.backend.domain.settlement.enums

/**
 * 정산 대조 불일치 사유
 * - 확장 가능성 고려: 향후 새로운 사유 추가 시 개방-폐쇄 원칙 준수
 */
enum class UnmatchedReason(val description: String) {
    AMOUNT_MISMATCH("PG 와 DB 의 금액이 일치하지 않음"),
    STATUS_MISMATCH("결제 상태가 정산 가능 상태가 아님"),
    NOT_FOUND_IN_PG("PG 시스템에서 해당 결제 내역을 찾을 수 없음"),
    NETWORK_ERROR("PG API 호출 중 네트워크 오류 발생"),
    // 🔸 향후 확장: TIMEOUT, DUPLICATE_DETECTED, etc.
    ;
}