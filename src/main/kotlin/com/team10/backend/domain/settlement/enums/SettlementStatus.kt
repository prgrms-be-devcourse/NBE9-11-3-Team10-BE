package com.team10.backend.domain.settlement.enums

enum class SettlementStatus {
    PENDING,      // 정산 대상 추출/계산 중
    CALCULATED,   // 수수료/환불 반영 완료 (대기)
    APPROVED,     // 관리자 승인 완료
    TRANSFERRING, // 출금/이체 실행 중
    COMPLETED,    // 정산 완료
    FAILED,       // 정산 실패 (재시도 필요)
    ADJUSTED      // 정산 후 조정 발생 (차액 정산)
}