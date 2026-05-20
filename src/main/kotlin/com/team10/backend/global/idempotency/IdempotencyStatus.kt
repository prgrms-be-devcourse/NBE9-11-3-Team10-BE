package com.team10.backend.global.idempotency

enum class IdempotencyStatus {
    NONE,       // 첫 요청 (락 설정 필요)
    LOCKED,     // 처리 중 (409 반환)
    COMPLETED   // 완료됨 (캐시 응답 반환)
}