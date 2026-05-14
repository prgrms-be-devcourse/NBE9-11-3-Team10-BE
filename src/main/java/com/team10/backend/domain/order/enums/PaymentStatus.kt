package com.team10.backend.domain.order.enums

import java.util.List

enum class PaymentStatus {
    READY,
    PENDING,
    PAID,
    FAILED,
    UNCERTAIN;

    // 현재 상태에서 다음에 올 수 있는 상태들을 정의
    fun canTransitionTo(next: PaymentStatus): Boolean {
        return when (this) {
            READY -> next in listOf(PENDING, FAILED)
            PENDING -> next in listOf(PAID, FAILED, UNCERTAIN)
            UNCERTAIN -> next in listOf(PAID, FAILED)
            PAID, FAILED -> false
        }
    }
}
